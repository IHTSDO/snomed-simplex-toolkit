package org.snomed.simplex.translation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateSetRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reacts to Managed Service (SI Authoring Platform) task lifecycle events for translation project branches:
 * a task <b>promotion</b> or a task <b>deletion</b> (both signalled by JMS events, see {@link ManagedServiceBranchEventListener};
 * {@code ManagedServiceTaskController} remains as a manual trigger), and a project <b>rebase</b>.
 * <p>
 * There is only ever zero or one open translation task per translation project at a time, so affected units are
 * found by {@link TranslationStatus#PROMOTED_TO_TASK} status alone - no unit-to-task-branch mapping is needed.
 */
@Service
public class ManagedServiceTaskEventService {

	private static final int SAVE_BATCH_SIZE = 5_000;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final SnolateSetRepository snolateSetRepository;
	private final SnolateTranslationSearchService translationSearchService;
	private final SnolateTranslationUnitStore translationUnitStore;
	private final TranslationStudioSyncService translationStudioSyncService;

	public ManagedServiceTaskEventService(SnolateSetRepository snolateSetRepository,
			SnolateTranslationSearchService translationSearchService,
			SnolateTranslationUnitStore translationUnitStore,
			TranslationStudioSyncService translationStudioSyncService) {

		this.snolateSetRepository = snolateSetRepository;
		this.translationSearchService = translationSearchService;
		this.translationUnitStore = translationUnitStore;
		this.translationStudioSyncService = translationStudioSyncService;
	}

	/**
	 * Pull the translation content of every language refset from the translation project branch in Snowstorm into
	 * Translation Studio, and refresh the stored snapshot of the project. Called when the project was rebased (content
	 * may have come in from the code system) and when a task was promoted (any edits made in the task are now in the project).
	 * A failure on one refset is logged and does not stop the others.
	 *
	 * @return the number of language refsets synchronised, and the refsets that failed
	 */
	public PullResult pullTranslationContentFromSnowstorm(CodeSystem codeSystem, SnowstormClient snowstormClient) {
		Map<String, String> translationLanguages = codeSystem.getTranslationLanguages();
		if (translationLanguages == null || translationLanguages.isEmpty()) {
			return new PullResult(0, List.of());
		}
		int synchronised = 0;
		List<String> failures = new ArrayList<>();
		for (Map.Entry<String, String> entry : translationLanguages.entrySet()) {
			String refsetId = entry.getKey();
			if (TranslationService.isFoundationEnglishLangRefset(refsetId)) {
				continue;
			}
			try {
				translationStudioSyncService.synchroniseWholeTranslationFromSnowstormToSnolate(codeSystem, snowstormClient, entry.getValue(), refsetId);
				synchronised++;
			} catch (ServiceException e) {
				logger.error("Managed Service event: failed to pull {}/{} from Snowstorm: {}", codeSystem.getShortName(), refsetId, e.getMessage(), e);
				failures.add("%s: %s".formatted(refsetId, e.getMessage()));
			}
		}
		logger.info("Managed Service event: pulled {} language refset(s) from Snowstorm for code system {}", synchronised, codeSystem.getShortName());
		return new PullResult(synchronised, failures);
	}

	/** Outcome of {@link #pullTranslationContentFromSnowstorm}. */
	public record PullResult(int synchronised, List<String> failures) {
		public boolean hasFailures() {
			return !failures.isEmpty();
		}
	}

	/** The Managed Service task was promoted: units pushed to it are now in sync with the code system. */
	public int handleTaskPromoted(String codeSystemShortName) {
		return transitionPromotedToTaskUnits(codeSystemShortName, TranslationStatus.COMPLETE);
	}

	/** The Managed Service task was rejected/deleted: units pushed to it were not promoted, revert to APPROVED. */
	public int handleTaskRejected(String codeSystemShortName) {
		return transitionPromotedToTaskUnits(codeSystemShortName, TranslationStatus.APPROVED);
	}

	private int transitionPromotedToTaskUnits(String codeSystemShortName, TranslationStatus newStatus) {
		List<SnolateTranslationSet> sets = snolateSetRepository.findByCodesystemOrderByName(codeSystemShortName);
		int[] totalUpdated = {0};
		for (SnolateTranslationSet set : sets) {
			List<TranslationUnit> saveBuffer = new ArrayList<>();
			translationSearchService.forEachUnitInSet(set.getCompositeSetCode(), set.getLanguageCodeWithRefsetId(),
					TranslationStatus.PROMOTED_TO_TASK, unit -> {
						unit.setStatus(newStatus);
						saveBuffer.add(unit);
						totalUpdated[0]++;
						flushIfNeeded(saveBuffer);
					});
			flushRemainder(saveBuffer);
		}
		logger.info("Managed Service task event: transitioned {} translation unit(s) for code system {} from {} to {}",
				totalUpdated[0], codeSystemShortName, TranslationStatus.PROMOTED_TO_TASK, newStatus);
		return totalUpdated[0];
	}

	private void flushIfNeeded(List<TranslationUnit> saveBuffer) {
		while (saveBuffer.size() >= SAVE_BATCH_SIZE) {
			translationUnitStore.saveAll(saveBuffer.subList(0, SAVE_BATCH_SIZE));
			saveBuffer.subList(0, SAVE_BATCH_SIZE).clear();
		}
	}

	private void flushRemainder(List<TranslationUnit> saveBuffer) {
		if (!saveBuffer.isEmpty()) {
			translationUnitStore.saveAll(saveBuffer);
		}
	}
}
