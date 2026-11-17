package org.snomed.simplex.translation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateSetRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reacts to Managed Service (SI Authoring Platform) task lifecycle events for translation project branches:
 * a task <b>promotion</b> (confirmed by a Snowstorm JMS event, see {@link ManagedServiceBranchEventListener})
 * or a task <b>rejection</b> (no JMS signal for this exists yet, so it's driven by
 * {@code ManagedServiceTaskController} in the short term).
 * <p>
 * There is only ever zero or one open translation task per translation project at a time, so affected units are
 * found by {@link TranslationStatus#PROMOTED_TO_TASK} status alone - no unit-to-task-branch mapping is needed.
 * See docs/managed-service-integration.md §4.3/§6.3.
 */
@Service
public class ManagedServiceTaskEventService {

	private static final int SAVE_BATCH_SIZE = 5_000;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final SnolateSetRepository snolateSetRepository;
	private final SnolateTranslationSearchService translationSearchService;
	private final SnolateTranslationUnitStore translationUnitStore;

	public ManagedServiceTaskEventService(SnolateSetRepository snolateSetRepository,
			SnolateTranslationSearchService translationSearchService,
			SnolateTranslationUnitStore translationUnitStore) {

		this.snolateSetRepository = snolateSetRepository;
		this.translationSearchService = translationSearchService;
		this.translationUnitStore = translationUnitStore;
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
