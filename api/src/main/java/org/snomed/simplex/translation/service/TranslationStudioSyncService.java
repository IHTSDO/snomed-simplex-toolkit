package org.snomed.simplex.translation.service;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ProgressMonitor;
import org.snomed.simplex.service.job.APTaskCreationCallable;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.service.SnolateTranslationSource;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.repository.TranslationStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TranslationStudioSyncService {

	private static final int STATUS_SAVE_BATCH_SIZE = 5_000;
	private static final int CREATE_LOAD_BATCH_SIZE = 500;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final TranslationService translationService;
	private final TranslationSyncService translationSyncService;
	private final TranslationStateRepository translationStateRepository;
	private final SnolateTranslationUnitStore translationUnitStore;
	private final SnolateTranslationSearchService translationSearchService;
	private final SnolateTranslationSourceRepository translationSourceRepository;

	public TranslationStudioSyncService(TranslationService translationService,
			TranslationSyncService translationSyncService,
			TranslationStateRepository translationStateRepository,
			SnolateTranslationUnitStore translationUnitStore,
			SnolateTranslationSearchService translationSearchService,
			SnolateTranslationSourceRepository translationSourceRepository) {

		this.translationService = translationService;
		this.translationSyncService = translationSyncService;
		this.translationStateRepository = translationStateRepository;
		this.translationUnitStore = translationUnitStore;
		this.translationSearchService = translationSearchService;
		this.translationSourceRepository = translationSourceRepository;
	}

	public void synchroniseWholeTranslationFromSnowstormToSnolate(CodeSystem codeSystem, SnowstormClient snowstormClient,
			String languageCode, String refsetId) throws ServiceExceptionWithStatusCode {

		logger.info("Starting Snowstorm to Snolate translation sync for {}/{}-{}", codeSystem.getShortName(), languageCode, refsetId);

		SnowstormTranslationSource snowstormTranslationSource = new SnowstormTranslationSource(snowstormClient, codeSystem, languageCode, refsetId);
		SnolateTranslationSource snolateTranslationSource = new SnolateTranslationSource(translationUnitStore, translationSearchService, languageCode, refsetId);

		TranslationState currentSnowstorm = snowstormTranslationSource.readTranslation();
		createMissingUnitsFromSnowstorm(languageCode, refsetId, currentSnowstorm);
		TranslationState previousSnowstorm = translationStateRepository.loadSnowstormSnapshot(refsetId);
		TranslationIntent delta = TranslationStateDiff.diff(previousSnowstorm, currentSnowstorm);
		if (TranslationStateDiff.hasAdditionsOrRemovals(delta)) {
			snolateTranslationSource.applyDelta(delta);
		}
		translationStateRepository.saveSnowstormSnapshot(refsetId, currentSnowstorm);

		String compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		markSnowstormMatchingUnitsComplete(compositeLanguageCode, currentSnowstorm);

		logger.info("Completed Snowstorm to Snolate translation sync for {}/{}-{} ({} concepts)",
				codeSystem.getShortName(), languageCode, refsetId, currentSnowstorm.getConceptTerms().size());
	}

	public ChangeSummary synchroniseSnolateSubsetToSnowstorm(CodeSystem codeSystem, SnowstormClient snowstormClient,
			SnolateTranslationSet translationSet, ProgressMonitor progressMonitor, APTaskCreationCallable taskCreationCallable,
			boolean includeReadyForReview)
			throws ServiceException {

		String languageCode = translationSet.getLanguageCode();
		String refsetId = translationSet.getRefset();

		SnowstormTranslationSource snowstormTranslationSource = new SnowstormTranslationSource(snowstormClient, codeSystem, languageCode, refsetId);
		SnolateTranslationSource snolateTranslationSource = new SnolateTranslationSource(translationUnitStore, translationSearchService, languageCode, refsetId);
		SnolateSubsetTranslationSource subsetSource = new SnolateSubsetTranslationSource(translationSearchService, languageCode, refsetId, translationSet.getCompositeSetCode(),
			includeReadyForReview);

		TranslationState currentSnowstorm = snowstormTranslationSource.readTranslation();
		TranslationState previousSnowstorm = translationStateRepository.loadSnowstormSnapshot(refsetId);
		TranslationIntent delta = TranslationStateDiff.diff(previousSnowstorm, currentSnowstorm);
		if (TranslationStateDiff.hasAdditionsOrRemovals(delta)) {
			snolateTranslationSource.applyDelta(delta);
		}

		TranslationState snolateSubset = subsetSource.readTranslation();
		TranslationSyncService.PushPlan plan = translationSyncService.planPushUpload(
				previousSnowstorm, currentSnowstorm, delta, snolateSubset, snolateSubset.getConceptTerms().keySet());

		ChangeSummary changeSummary;
		if (plan.hasSnowstormUpload()) {
			changeSummary = translationService.uploadTranslationFromState(refsetId, codeSystem, plan.snowstormUpload(),
					snowstormClient, progressMonitor, taskCreationCallable);
		} else {
			logger.info("TranslationSync {}-{} No Snolate-side upload changes for set {}", languageCode, refsetId, translationSet.getLabel());
			int activeRefsetMembers = snowstormClient.countActiveRefsetMembers(refsetId, codeSystem, codeSystem.getDefaultModuleOrThrow());
			changeSummary = new ChangeSummary(0, 0, 0, activeRefsetMembers);
		}

		translationStateRepository.saveSnowstormSnapshot(refsetId, plan.postPushSnapshot());
		markPulledUnitsComplete(translationSet, includeReadyForReview);
		return changeSummary;
	}

	void markPulledUnitsComplete(SnolateTranslationSet translationSet, boolean includeReadyForReview) {
		String compositeLanguageCode = translationSet.getLanguageCodeWithRefsetId();
		String setCode = translationSet.getCompositeSetCode();
		List<TranslationUnit> saveBuffer = new ArrayList<>();
		translationSearchService.forEachUnitInSet(setCode, compositeLanguageCode, unit -> {
			if (unit.hasTermContent() && (includeReadyForReview || unit.getStatus() != TranslationStatus.FOR_REVIEW)) {
				unit.setStatus(TranslationStatus.COMPLETE);
				saveBuffer.add(unit);
				flushStatusSaveBufferIfNeeded(saveBuffer);
			}
		});
		flushStatusSaveBufferRemainder(saveBuffer);
	}

	void createMissingUnitsFromSnowstorm(String languageCode, String refsetId, TranslationState currentSnowstorm) {
		String compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		List<String> candidateCodes = new ArrayList<>();
		for (Map.Entry<Long, List<String>> entry : currentSnowstorm.getConceptTerms().entrySet()) {
			if (!normalizeOrderedTerms(entry.getValue()).isEmpty()) {
				candidateCodes.add(entry.getKey().toString());
			}
		}
		if (candidateCodes.isEmpty()) {
			return;
		}

		List<TranslationUnit> saveBuffer = new ArrayList<>();
		int createdTotal = 0;
		for (List<String> chunkCodes : Iterables.partition(candidateCodes, CREATE_LOAD_BATCH_SIZE)) {
			Map<String, TranslationUnit> existingByCode = translationUnitStore.loadByCodes(compositeLanguageCode, chunkCodes);
			Map<String, TranslationSource> sourceByCode = StreamSupport
					.stream(translationSourceRepository.findAllById(chunkCodes).spliterator(), false)
					.collect(Collectors.toMap(TranslationSource::getCode, s -> s, (a, b) -> a));

			for (String code : chunkCodes) {
				if (existingByCode.containsKey(code)) {
					continue;
				}
				TranslationSource source = sourceByCode.get(code);
				if (source == null) {
					continue;
				}
				List<String> snowstormTerms = currentSnowstorm.getConceptTerms().get(Long.parseLong(code));
				if (normalizeOrderedTerms(snowstormTerms).isEmpty()) {
					continue;
				}
				TranslationUnit unit = new TranslationUnit(
						new TranslationUnit.MembershipKey(code, refsetId, languageCode, compositeLanguageCode, source.getOrder()),
						new ArrayList<>(snowstormTerms),
						TranslationStatus.COMPLETE,
						new LinkedHashSet<>());
				saveBuffer.add(unit);
				createdTotal++;
				flushStatusSaveBufferIfNeeded(saveBuffer);
			}
		}
		flushStatusSaveBufferRemainder(saveBuffer);
		if (createdTotal > 0) {
			logger.info("Created {} translation units from Snowstorm for {}", createdTotal, compositeLanguageCode);
		}
	}

	void markSnowstormMatchingUnitsComplete(String compositeLanguageCode, TranslationState snowstormState) {
		Map<Long, List<String>> snowstormTerms = snowstormState.getConceptTerms();
		List<TranslationUnit> saveBuffer = new ArrayList<>();
		translationSearchService.forEachUnitByCompositeLanguageCode(compositeLanguageCode, unit -> {
			if (unit.hasTermContent() && unit.getStatus() != TranslationStatus.NEEDS_EDIT) {
				try {
					long conceptId = Long.parseLong(unit.getCode());
					List<String> snowstormConceptTerms = snowstormTerms.getOrDefault(conceptId, List.of());
					if (orderedTermsMatch(unit.getTerms(), snowstormConceptTerms)) {
						unit.setStatus(TranslationStatus.COMPLETE);
						saveBuffer.add(unit);
						flushStatusSaveBufferIfNeeded(saveBuffer);
					}
				} catch (NumberFormatException e) {
					// Skip units with non-numeric concept codes
				}
			}
		});
		flushStatusSaveBufferRemainder(saveBuffer);
	}

	private void flushStatusSaveBufferIfNeeded(List<TranslationUnit> saveBuffer) {
		while (saveBuffer.size() >= STATUS_SAVE_BATCH_SIZE) {
			translationUnitStore.saveAll(saveBuffer.subList(0, STATUS_SAVE_BATCH_SIZE));
			saveBuffer.subList(0, STATUS_SAVE_BATCH_SIZE).clear();
		}
	}

	private void flushStatusSaveBufferRemainder(List<TranslationUnit> saveBuffer) {
		if (!saveBuffer.isEmpty()) {
			translationUnitStore.saveAll(saveBuffer);
		}
	}

	static boolean orderedTermsMatch(List<String> snolateTerms, List<String> snowstormTerms) {
		return normalizeOrderedTerms(snolateTerms).equals(normalizeOrderedTerms(snowstormTerms));
	}

	private static List<String> normalizeOrderedTerms(List<String> terms) {
		if (terms == null || terms.isEmpty()) {
			return List.of();
		}
		return terms.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
