package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LanguageRefsetInactiveDescriptionCleanupResult;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Service
public class LanguageRefsetInactiveDescriptionCleanupService {

	private static final Logger logger = LoggerFactory.getLogger(LanguageRefsetInactiveDescriptionCleanupService.class);

	private final TranslationService translationService;

	public LanguageRefsetInactiveDescriptionCleanupService(TranslationService translationService) {
		this.translationService = translationService;
	}

	public LanguageRefsetInactiveDescriptionCleanupResult cleanup(CodeSystem codeSystem, SnowstormClient snowstormClient, String refsetId) throws ServiceException {

		rejectFoundationEnglishLangRefset(refsetId);
		snowstormClient.getRefsetOrThrow(refsetId, codeSystem);

		int[] activeMembersScanned = {0};
		int[] membersInactivated = {0};

		snowstormClient.forEachRefsetMemberPage(refsetId, codeSystem, true, page -> {
			activeMembersScanned[0] += page.size();
			List<String> descriptionIds = page.stream()
					.map(RefsetMember::getReferencedComponentId)
					.toList();
			Map<String, Boolean> descriptionActiveFlags = snowstormClient.loadDescriptionActiveFlags(descriptionIds, codeSystem);

			List<RefsetMember> membersToInactivate = new ArrayList<>();
			for (RefsetMember member : page) {
				if (!member.isActive()) {
					continue;
				}
				String descriptionId = member.getReferencedComponentId();
				if (!Boolean.TRUE.equals(descriptionActiveFlags.get(descriptionId))) {
					member.setActive(false);
					membersToInactivate.add(member);
				}
			}

			if (!membersToInactivate.isEmpty()) {
				logger.info("Inactivating {} language refset members in {} referencing inactive or missing descriptions.",
						membersToInactivate.size(), refsetId);
				snowstormClient.createUpdateRefsetMembers(membersToInactivate, codeSystem);
				membersInactivated[0] += membersToInactivate.size();
			}
		});

		int activeMembersRemaining = snowstormClient.countAllActiveRefsetMembers(refsetId, codeSystem);
		translationService.clearLanguageRefsetCache(codeSystem.getShortName());

		logger.info("Language refset {} cleanup complete: scanned {}, inactivated {}, {} active members remain.",
				refsetId, activeMembersScanned[0], membersInactivated[0], activeMembersRemaining);

		return new LanguageRefsetInactiveDescriptionCleanupResult(
				activeMembersScanned[0], membersInactivated[0], activeMembersRemaining);
	}

	private static void rejectFoundationEnglishLangRefset(String refsetId) throws ServiceExceptionWithStatusCode {
		if (TranslationService.isFoundationEnglishLangRefset(refsetId)) {
			throw new ServiceExceptionWithStatusCode(
					"Foundation English synonym refsets cannot be modified through this operation.", HttpStatus.BAD_REQUEST);
		}
	}
}
