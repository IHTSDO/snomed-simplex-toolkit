package org.snomed.simplex.snolate.service;

import com.google.common.base.Strings;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationUnitPage;
import org.snomed.simplex.rest.pojos.TranslationUnitRow;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class SnolateTranslationToolService {

	private final SnolateTranslationUnitRepository translationUnitRepository;
	private final SnolateTranslationSourceRepository translationSourceRepository;
	private final SnolateTranslationSearchService translationSearchService;

	public SnolateTranslationToolService(SnolateTranslationUnitRepository translationUnitRepository,
			SnolateTranslationSourceRepository translationSourceRepository, SnolateTranslationSearchService translationSearchService) {
		this.translationUnitRepository = translationUnitRepository;
		this.translationSourceRepository = translationSourceRepository;
		this.translationSearchService = translationSearchService;
	}

	public void applyDashboardMetadata(SnolateTranslationSet set) {
		Map<String, String> aiGoldenSet = set.getAiGoldenSet();
		boolean aiSetupComplete = false;
		if (aiGoldenSet != null && aiGoldenSet.size() >= 5 && aiGoldenSet.values().stream().noneMatch(Strings::isNullOrEmpty)) {
			aiSetupComplete = true;
		}
		set.setAiSetupComplete(aiSetupComplete);
	}

	public void applyCounts(SnolateTranslationSet translationSet) {
		applyCounts(List.of(translationSet));
	}

	/**
	 * Fills {@link SnolateTranslationSet#setTranslated} and {@link SnolateTranslationSet#setChangedSinceCreatedOrLastPulled}.
	 * Counts are loaded in one round-trip per distinct {@link SnolateTranslationSet#getLanguageCodeWithRefsetId()} value.
	 */
	public void applyCounts(List<SnolateTranslationSet> sets) {
		if (sets == null || sets.isEmpty()) {
			return;
		}
		Map<String, List<SnolateTranslationSet>> byLang = sets.stream()
				.collect(Collectors.groupingBy(SnolateTranslationSet::getLanguageCodeWithRefsetId));
		for (Map.Entry<String, List<SnolateTranslationSet>> entry : byLang.entrySet()) {
			String lang = entry.getKey();
			List<SnolateTranslationSet> group = entry.getValue();
			List<String> setCodes = group.stream()
					.map(SnolateTranslationSet::getCompositeSetCode)
					.distinct()
					.toList();
			if (setCodes.isEmpty()) {
				continue;
			}
			Map<String, Long> translated = aggregateCounts(translationSearchService.countTranslatedInSubsetBatch(lang, setCodes));
			Map<String, Long> outstanding = aggregateCounts(translationSearchService.countOutstandingReviewInSubsetBatch(lang, setCodes));
			for (SnolateTranslationSet set : group) {
				String code = set.getCompositeSetCode();
				set.setTranslated(translated.getOrDefault(code, 0L).intValue());
				set.setChangedSinceCreatedOrLastPulled(outstanding.getOrDefault(code, 0L).intValue());
			}
		}
	}

	private static Map<String, Long> aggregateCounts(Map<String, Long> rows) {
		if (rows == null || rows.isEmpty()) {
			return Map.of();
		}
		Map<String, Long> map = new HashMap<>();
		for (Map.Entry<String, Long> row : rows.entrySet()) {
			String setCode = row.getKey();
			if (setCode == null) {
				continue;
			}
			map.put(setCode, row.getValue());
		}
		return map;
	}

	/**
	 * Paginated translation-set rows: English term from {@link TranslationSource}, dialect terms and {@link TranslationUnit#getStatus()} from persistence.
	 * Ordering is by status (NEEDS_EDIT, FOR_REVIEW, APPROVED, then not started), then source display order, then concept id.
	 */
	public TranslationUnitPage<TranslationUnitRow> getRows(SnolateTranslationSet translationSet, int page, int pageSize) {
		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		Sort sort = Sort.by("statusSort", "order", "code");
		Page<TranslationUnit> pageResult = translationSearchService.pageUnitsInSet(setCode, lang, PageRequest.of(page, pageSize, sort));
		List<String> codes = pageResult.getContent().stream().map(TranslationUnit::getCode).toList();
		Map<String, TranslationSource> sourceByCode = Map.of();
		if (!codes.isEmpty()) {
			sourceByCode = StreamSupport.stream(translationSourceRepository.findAllById(codes).spliterator(), false)
					.collect(Collectors.toMap(TranslationSource::getCode, Function.identity()));
		}
		List<TranslationUnitRow> rows = new ArrayList<>();
		for (TranslationUnit u : pageResult.getContent()) {
			TranslationSource src = sourceByCode.get(u.getCode());
			String english = src != null ? src.getTerm() : "";
			List<String> target = copyTerms(Optional.of(u));
			String statusName = u.getStatus() != null ? u.getStatus().name() : null;
			rows.add(new TranslationUnitRow(List.of(english), target, u.getCode(), statusName));
		}
		return new TranslationUnitPage<>((int) pageResult.getTotalElements(), null, null, rows).withoutPagination();
	}

	public TranslationUnitRow getSampleRow(SnolateTranslationSet translationSet, String conceptId) throws ServiceExceptionWithStatusCode {
		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		Optional<TranslationUnit> tuOpt = translationUnitRepository.findByCodeAndCompositeLanguageCode(conceptId, lang);
		if (tuOpt.isEmpty() || !tuOpt.get().getMemberOf().contains(setCode)) {
			return null;
		}
		TranslationUnit tu = tuOpt.get();
		TranslationSource src = translationSourceRepository.findById(conceptId)
				.orElseThrow(() -> new ServiceExceptionWithStatusCode("Translation source not found for concept", HttpStatus.NOT_FOUND));
		List<String> target = copyTerms(Optional.of(tu));
		String statusName = tu.getStatus() != null ? tu.getStatus().name() : null;
		TranslationUnitRow row = new TranslationUnitRow(List.of(src.getTerm()), target, conceptId, statusName);
		row.blankLabels();
		return row;
	}

	/**
	 * Updates dialect terms and review status for a concept that is already a member of the translation set.
	 * Normalized terms are trimmed, empty strings removed. Empty terms are only allowed with {@link TranslationStatus#NOT_STARTED};
	 * non-empty terms cannot use {@link TranslationStatus#NOT_STARTED}.
	 */
	public void updateTranslationUnit(SnolateTranslationSet translationSet, String conceptId, List<String> rawTerms,
			TranslationStatus status) throws ServiceExceptionWithStatusCode {
		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		Optional<TranslationUnit> tuOpt = translationUnitRepository.findByCodeAndCompositeLanguageCode(conceptId, lang);
		if (tuOpt.isEmpty() || !tuOpt.get().getMemberOf().contains(setCode)) {
			throw new ServiceExceptionWithStatusCode("Translation unit not found in this set.", HttpStatus.NOT_FOUND);
		}
		TranslationUnit unit = tuOpt.get();
		List<String> terms = normalizeTranslationTerms(rawTerms);
		if (terms.isEmpty()) {
			if (status != TranslationStatus.NOT_STARTED) {
				throw new ServiceExceptionWithStatusCode("Empty translation terms require status NOT_STARTED.", HttpStatus.BAD_REQUEST);
			}
		} else {
			if (status == TranslationStatus.NOT_STARTED) {
				throw new ServiceExceptionWithStatusCode("Non-empty translation terms cannot use status NOT_STARTED.", HttpStatus.BAD_REQUEST);
			}
		}
		unit.setTerms(terms);
		unit.setStatus(status);
		translationUnitRepository.save(unit);
	}

	private static List<String> normalizeTranslationTerms(List<String> rawTerms) {
		if (rawTerms == null) {
			return List.of();
		}
		return rawTerms.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
	}

	private static List<String> copyTerms(Optional<TranslationUnit> tu) {
		return tu.map(u -> new ArrayList<>(u.getTerms())).orElseGet(ArrayList::new);
	}
}
