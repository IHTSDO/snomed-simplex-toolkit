package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.ConceptMini;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.domain.*;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public abstract class RefsetUpdateService {

	@Autowired
	private SpreadsheetService spreadsheetService;

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void downloadRefsetAsSpreadsheet(String refsetId, OutputStream outputStream, CodeSystem codeSystem) throws ServiceException, IOException {
		SnowstormClient snowstormClient = getSnowstormClient();
		List<RefsetMember> members = snowstormClient.loadAllRefsetMembers(refsetId, codeSystem, true);

		members.sort(Comparator.comparing(RefsetMember::getReferencedComponentId));

		Map<String, Function<RefsetMember, String>> refsetColumns = getRefsetToSpreadsheetConversionMap();
		try (Workbook workbook = spreadsheetService.createSpreadsheet(members, refsetColumns)) {
			workbook.write(outputStream);
		}
	}

	public ChangeSummary updateRefsetViaSpreadsheet(String refsetId, InputStream inputStream, CodeSystem codeSystem) throws ServiceException {
		// Check refset exists
		ConceptMini refset = getSnowstormClient().getRefsetOrThrow(refsetId, codeSystem);
		List<SheetRefsetMember> sheetMembers = spreadsheetService.readRefsetSpreadsheet(inputStream, getInputSheetExpectedHeaders(), getInputSheetMemberExtractor());
		return update(refset, sheetMembers, codeSystem);
	}

	private ChangeSummary update(ConceptMini refset, List<SheetRefsetMember> inputMembers, CodeSystem codeSystem) throws ServiceException {
		String refsetId = refset.getConceptId();
		String refsetTerm = refset.getPtOrFsnOrConceptId();
		try {
			SnowstormClient snowstormClient = getSnowstormClient();
			logger.info("Updating refset {} \"{}\", read {} members from spreadsheet.", refsetId, refsetTerm, inputMembers.size());

			// Read members from Snowstorm
			List<RefsetMember> allStoredMembers = snowstormClient.loadAllRefsetMembers(refsetId, codeSystem, false);
			logger.info("Updating refset {} \"{}\", loaded {} members from Snowstorm for comparison.", refsetId, refsetTerm, allStoredMembers.size());

			// Ignore sheet members where concept does not exist
			Set<String> inputMemberConceptIds = inputMembers.stream().map(SheetRefsetMember::getReferenceComponentId).collect(Collectors.toSet());
			List<String> conceptsExist = snowstormClient.getConceptIds(inputMemberConceptIds, codeSystem).stream().map(Object::toString).collect(Collectors.toList());
			List<String> conceptsDoNotExist = new ArrayList<>(inputMemberConceptIds);
			conceptsDoNotExist.removeAll(conceptsExist);
			if (!conceptsDoNotExist.isEmpty()) {
				logger.error("{} concepts do not exist: {}", conceptsDoNotExist.size(), conceptsDoNotExist);
				// TODO: Should we alert the user?
				inputMembers = inputMembers.stream().filter(sheetMember -> conceptsExist.contains(sheetMember.getReferenceComponentId())).collect(Collectors.toList());
			}

			// Create map of existing members
			Map<String, List<RefsetMember>> storedMemberMap = new HashMap<>();
			for (RefsetMember storedMember : allStoredMembers) {
				storedMemberMap.computeIfAbsent(storedMember.getReferencedComponentId(), key -> new ArrayList<>()).add(storedMember);
			}

			// Create collections of members to create, update and leave alone. Those not in the sets will be deleted.
			List<RefsetMember> membersToCreate = new ArrayList<>();
			List<RefsetMember> membersToUpdate = new ArrayList<>();
			List<RefsetMember> membersToKeep = new ArrayList<>();

			for (SheetRefsetMember inputMember : inputMembers) {

				RefsetMember wantedRefsetMember = convertToMember(inputMember, refsetId, codeSystem.getDefaultModuleOrThrow());

				// Lookup existing member(s)
				List<RefsetMember> storedMembers = storedMemberMap.getOrDefault(inputMember.getReferenceComponentId(), Collections.emptyList());
				storedMembers.sort(Comparator.comparing(RefsetMember::isReleased).thenComparing(RefsetMember::isActive));
				boolean found = false;
				for (RefsetMember storedMember : storedMembers) {
					if (matchMember(wantedRefsetMember, storedMember)) {
						found = true;
						if (applyMember(wantedRefsetMember, storedMember)) {
							membersToUpdate.add(storedMember);
						} else {
							membersToKeep.add(storedMember);
						}
						break;
					}
				}
				if (!found) {
					membersToCreate.add(wantedRefsetMember);
				}
			}

			List<RefsetMember> membersToRemove = allStoredMembers.stream()
					.filter(not(membersToKeep::contains))
					.filter(not(membersToUpdate::contains))
					.collect(Collectors.toList());

			List<RefsetMember> membersToInactivate = membersToRemove.stream().filter(RefsetMember::isReleased).collect(Collectors.toList());
			List<RefsetMember> membersToDelete = membersToRemove.stream().filter(not(RefsetMember::isReleased)).collect(Collectors.toList());

			logger.info("Member changes required: {} create, {} update, {} delete, {} inactivate.",
					membersToCreate.size(), membersToUpdate.size(), membersToDelete.size(), membersToInactivate.size());

			// Assemble create / update / inactivate members
			List<RefsetMember> membersToUpdateCreate = new ArrayList<>(membersToCreate);
			membersToUpdateCreate.addAll(membersToUpdate);
			membersToInactivate.forEach(memberToInactivate -> memberToInactivate.setActive(false));
			membersToUpdateCreate.addAll(membersToInactivate);
			if (!membersToUpdateCreate.isEmpty()) {
				// Send all as batch
				logger.info("Running bulk create/update...");
				snowstormClient.createUpdateRefsetMembers(membersToUpdateCreate, codeSystem);
			}
			if (!membersToDelete.isEmpty()) {
				logger.info("Running bulk delete...");
				snowstormClient.deleteRefsetMembers(membersToDelete, codeSystem);
			}

			int newActiveCount = snowstormClient.countAllActiveRefsetMembers(refsetId, codeSystem);

			logger.info("Processing refset {} \"{}\" complete.", refsetId, refsetTerm);
			return new ChangeSummary(membersToCreate.size(), membersToUpdate.size(), membersToRemove.size(), newActiveCount);
		} catch (ServiceException e) {
			throw new ServiceException(String.format("Processing refset %s \"%s\" failed.", refsetId, refsetTerm), e);
		}
	}

	protected abstract Map<String, Function<RefsetMember, String>> getRefsetToSpreadsheetConversionMap();

	/**
	 * Get list of expected headers for this sheet type. Headers names are case-insensitive and can be marked optional.
	 */
	protected abstract List<SheetHeader> getInputSheetExpectedHeaders();

	protected abstract SheetRowToRefsetExtractor getInputSheetMemberExtractor();

	/**
	 * Convert SheetRefsetMember to RefsetMember
	 * @return RefsetMember
	 */
	protected abstract RefsetMember convertToMember(SheetRefsetMember inputMember, String refsetId, String moduleId);

	/**
	 * Check if the immutable fields of the two refset members match.
	 * @return true if the immutable fields match, otherwise false.
	 */
	protected abstract boolean matchMember(RefsetMember wantedRefsetMember, RefsetMember storedMember);

	/**
	 * Copy the values of the mutable fields in wantedRefsetMember to storedMember. Also set active flag to true if needed.
	 * @return true if any values, including active flag, in storedMember were changed, otherwise false.
	 */
	protected abstract boolean applyMember(RefsetMember wantedRefsetMember, RefsetMember storedMember);

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return snowstormClientFactory.getClient();
	}

	@FunctionalInterface
	public interface SheetRowToRefsetExtractor {
		SheetRefsetMember extract(Row row, Integer rowNumber, HeaderConfiguration headerConfiguration) throws ServiceException;
	}
}