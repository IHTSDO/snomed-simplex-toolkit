package org.snomed.simplex.snolate.service;

import com.google.common.base.Strings;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationUnitPage;
import org.snomed.simplex.rest.pojos.TranslationUnitRow;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.domain.TranslationUnitId;
import org.snomed.simplex.snolate.repository.TranslationSourceRepository;
import org.snomed.simplex.snolate.repository.TranslationUnitRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.translation.util.ConceptExplanationMarkdown;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SnolateTranslationToolService {

	private final TranslationUnitRepository translationUnitRepository;
	private final TranslationSourceRepository translationSourceRepository;

	public SnolateTranslationToolService(TranslationUnitRepository translationUnitRepository,
			TranslationSourceRepository translationSourceRepository) {
		this.translationUnitRepository = translationUnitRepository;
		this.translationSourceRepository = translationSourceRepository;
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
			Map<String, Long> translated = aggregateCounts(translationUnitRepository.countTranslatedInSubsetBatch(lang, setCodes));
			Map<String, Long> outstanding = aggregateCounts(
					translationUnitRepository.countOutstandingReviewInSubsetBatch(lang, setCodes));
			for (SnolateTranslationSet set : group) {
				String code = set.getCompositeSetCode();
				set.setTranslated(translated.getOrDefault(code, 0L).intValue());
				set.setChangedSinceCreatedOrLastPulled(outstanding.getOrDefault(code, 0L).intValue());
			}
		}
	}

	private static Map<String, Long> aggregateCounts(List<Object[]> rows) {
		if (rows == null || rows.isEmpty()) {
			return Map.of();
		}
		Map<String, Long> map = new HashMap<>();
		for (Object[] row : rows) {
			String setCode = Objects.toString(row[0], null);
			if (setCode == null) {
				continue;
			}
			map.put(setCode, ((Number) row[1]).longValue());
		}
		return map;
	}

	@Transactional(readOnly = true)
	public TranslationUnitPage<TranslationUnitRow> getSampleRows(SnolateTranslationSet translationSet, int pageSize) {
		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		Page<TranslationSource> page = translationSourceRepository.findPageHavingSetMembership(setCode,
				PageRequest.of(0, pageSize, Sort.by("code")));
		List<TranslationUnitRow> rows = new ArrayList<>();
		for (TranslationSource src : page.getContent()) {
			Optional<TranslationUnit> tu = translationUnitRepository.findById(new TranslationUnitId(src.getCode(), lang));
			List<String> target = copyTerms(tu);
			rows.add(new TranslationUnitRow(List.of(src.getTerm()), target, src.getCode(), null));
		}
		return new TranslationUnitPage<>((int) page.getTotalElements(), null, null, rows).withoutPagination();
	}

	@Transactional(readOnly = true)
	public TranslationUnitRow getSampleRow(SnolateTranslationSet translationSet, String conceptId, SnowstormClient snowstormClient)
			throws ServiceExceptionWithStatusCode {
		String setCode = translationSet.getCompositeSetCode();
		Optional<TranslationSource> sourceRow = translationSourceRepository.findByCodeHavingSetMembership(conceptId, setCode);
		if (sourceRow.isEmpty()) {
			return null;
		}
		TranslationSource src = sourceRow.get();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		Optional<TranslationUnit> tu = translationUnitRepository.findById(new TranslationUnitId(conceptId, lang));
		List<String> target = copyTerms(tu);
		long conceptIdLong;
		try {
			conceptIdLong = Long.parseLong(conceptId);
		} catch (NumberFormatException e) {
			throw new ServiceExceptionWithStatusCode("Invalid concept id", HttpStatus.BAD_REQUEST, e);
		}
		String explanation;
		try {
			CodeSystem international = new CodeSystem("SNOMEDCT", "SNOMEDCT", "MAIN");
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(List.of(conceptIdLong), international);
			explanation = concepts.isEmpty() ? null : ConceptExplanationMarkdown.getMarkdown(concepts.get(0));
		} catch (Exception e) {
			explanation = null;
		}
		TranslationUnitRow row = new TranslationUnitRow(List.of(src.getTerm()), target, conceptId, explanation);
		row.blankLabels();
		return row;
	}

	/** Materialize TranslationUnit.terms so JSON serialization does not touch lazy Hibernate collections. */
	private static List<String> copyTerms(Optional<TranslationUnit> tu) {
		return tu.map(u -> new ArrayList<>(u.getTerms())).orElseGet(ArrayList::new);
	}
}
