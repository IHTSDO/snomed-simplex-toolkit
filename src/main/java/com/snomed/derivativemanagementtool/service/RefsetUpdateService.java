package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.client.ConceptMini;
import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.domain.CodeSystemProperties;
import com.snomed.derivativemanagementtool.domain.RefsetMember;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@Service
public class RefsetUpdateService {

	@Autowired
	private CodeSystemConfigService configService;

	@Autowired
	private SpreadsheetService spreadsheetService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void downloadSimpleRefsetAsSpreadsheet(String refsetId, OutputStream outputStream) throws ServiceException, IOException {
		SnowstormClient snowstormClient = getSnowstormClient();
		List<RefsetMember> members = snowstormClient.loadAllRefsetMembers(refsetId);
		sortSimpleRefset(members);

		Map<String, Function<RefsetMember, String>> simpleRefsetColumns = new LinkedHashMap<>();
		simpleRefsetColumns.put("conceptId", RefsetMember::getReferencedComponentId);

		Workbook workbook = spreadsheetService.createSpreadsheet(members, simpleRefsetColumns);
		workbook.write(outputStream);
	}

	public ChangeSummary updateSimpleRefsetViaSpreadsheet(String refsetId, InputStream inputStream) throws ServiceException {
		// Check refset exists
		ConceptMini refset = getSnowstormClient().getRefsetOrThrow(refsetId);
		List<String> conceptIds = spreadsheetService.readSimpleRefsetSpreadsheet(inputStream);
		return update(refset, conceptIds);
	}

	private ChangeSummary update(ConceptMini refset, List<String> inputMembers) throws ServiceException {
		String refsetId = refset.getConceptId();
		String refsetTerm = refset.getTerm();
		try {
			SnowstormClient snowstormClient = getSnowstormClient();
			logger.info("Updating refset {} \"{}\", read {} members from spreadsheet.", refsetId, refsetTerm, inputMembers.size());

			// Read members from store
			List<RefsetMember> allStoredMembers = snowstormClient.loadAllRefsetMembers(refsetId);
			logger.info("Updating refset {} \"{}\", loaded {} members from Snowstorm for comparison.", refsetId, refsetTerm, allStoredMembers.size());

			List<String> conceptsExist = snowstormClient.getConceptIds(inputMembers).stream().map(Object::toString).collect(Collectors.toList());
			List<String> conceptsDoNotExist = new ArrayList<>(inputMembers);
			conceptsDoNotExist.removeAll(conceptsExist);
			if (!conceptsDoNotExist.isEmpty()) {
				logger.error("{} concepts do not exist: {}", conceptsDoNotExist.size(), conceptsDoNotExist);
				// TODO: Should we alert the user?
				inputMembers = conceptsExist;
			}

			Map<String, List<RefsetMember>> storedMemberMap = new HashMap<>();
			int activeMembersBefore = 0;
			for (RefsetMember storedMember : allStoredMembers) {
				if (storedMember.isActive()) {
					activeMembersBefore++;
				}
				storedMemberMap.computeIfAbsent(storedMember.getReferencedComponentId(), key -> new ArrayList<>()).add(storedMember);
			}

			// Members to create
			List<RefsetMember> membersToCreate = new ArrayList<>();
			List<RefsetMember> membersToUpdate = new ArrayList<>();
			List<RefsetMember> membersToKeep = new ArrayList<>();

			int added = 0;
			int removed;

			CodeSystemProperties config = configService.getConfig();
			for (String inputMember : inputMembers) {
				// Lookup existing member(s) for component
				List<RefsetMember> storedMembers = storedMemberMap.getOrDefault(inputMember, Collections.emptyList());
				if (storedMembers.isEmpty()) {
					// None exist, create
					membersToCreate.add(new RefsetMember(refsetId, config.getDefaultModule(), inputMember));
					added++;
				} else {
					RefsetMember memberToKeep;
					if (storedMembers.size() == 1) {
						memberToKeep = storedMembers.get(0);
					} else {
						// Find best member
						storedMembers.sort(Comparator.comparing(RefsetMember::isReleased).thenComparing(RefsetMember::isActive));
						memberToKeep = storedMembers.get(0);
					}
					// Keep remaining member
					if (!memberToKeep.isActive()) {
						// Not active, update to make active
						memberToKeep.setActive(true);
						membersToUpdate.add(memberToKeep);
						added++;
					} else {
						// Keep as-is
						membersToKeep.add(memberToKeep);
					}
				}
			}

			List<RefsetMember> membersToRemove = allStoredMembers.stream().filter(not(membersToKeep::contains)).collect(Collectors.toList());
			removed = membersToRemove.size();

			List<RefsetMember> membersToInactivate = membersToRemove.stream().filter(RefsetMember::isReleased).collect(Collectors.toList());
			List<RefsetMember> membersToDelete = membersToRemove.stream().filter(not(RefsetMember::isReleased)).collect(Collectors.toList());

			logger.info("Member changes required: {} create, {} delete, {} inactivate.", membersToCreate.size(), membersToDelete.size(), membersToInactivate.size());

			// Assemble create / update / inactivate members
			List<RefsetMember> membersToUpdateCreate = new ArrayList<>(membersToCreate);
			membersToUpdateCreate.addAll(membersToUpdate);
			membersToInactivate.forEach(memberToInactivate -> memberToInactivate.setActive(false));
			membersToUpdateCreate.addAll(membersToInactivate);
			if (!membersToUpdateCreate.isEmpty()) {
				// Send all as batch
				logger.info("Running bulk create/update...");
				snowstormClient.createUpdateRefsetMembers(membersToUpdateCreate);
			}
			if (!membersToDelete.isEmpty()) {
				logger.info("Running bulk delete...");
				snowstormClient.deleteRefsetMembers(membersToDelete);
			}

			logger.info("Processing refset {} \"{}\" complete.", refsetId, refsetTerm);
			return new ChangeSummary(added, removed, (activeMembersBefore + added) - removed);
		} catch (ServiceException e) {
			throw new ServiceException(String.format("Processing refset %s \"%s\" failed.", refsetId, refsetTerm), e);
		}
	}

	private SnowstormClient getSnowstormClient() throws ServiceException {
		return configService.getSnowstormClient();
	}

	private void sortSimpleRefset(List<RefsetMember> members) {
		members.sort(Comparator.comparing(RefsetMember::getReferencedComponentId));
	}
}
