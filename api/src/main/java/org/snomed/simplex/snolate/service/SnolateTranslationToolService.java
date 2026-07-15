package org.snomed.simplex.snolate.service;

import com.google.common.base.Strings;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationUnitPage;
import org.snomed.simplex.rest.pojos.TranslationUnitRow;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationStatusLabels;
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class SnolateTranslationToolService {

	private static final int CSV_EXPORT_PAGE_SIZE = 2000;

	private static final String DEFAULT_TRANSLATION_LABEL = "Translation";

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
	 * Fills {@link SnolateTranslationSet#setTranslated} and {@link SnolateTranslationSet#setStatusCounts}.
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
			Map<String, Map<String, Long>> statusCountsBySet = translationSearchService.countStatusInSubsetBatch(lang, setCodes);
			for (SnolateTranslationSet set : group) {
				String code = set.getCompositeSetCode();
				set.setTranslated(translated.getOrDefault(code, 0L).intValue());
				Map<String, Long> counts = statusCountsBySet.getOrDefault(code, Map.of());
				Map<String, Integer> statusCounts = new LinkedHashMap<>();
				for (TranslationStatus status : TranslationStatus.values()) {
					statusCounts.put(status.name(), counts.getOrDefault(status.name(), 0L).intValue());
				}
				set.setStatusCounts(statusCounts);
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
	public TranslationUnitPage<TranslationUnitRow> getRows(SnolateTranslationSet translationSet, int page, int pageSize)
			throws ServiceExceptionWithStatusCode {
		return getRows(translationSet, page, pageSize, null, null, null);
	}

	public TranslationUnitPage<TranslationUnitRow> getRows(SnolateTranslationSet translationSet, int page, int pageSize,
			TranslationStatus statusFilter) throws ServiceExceptionWithStatusCode {
		return getRows(translationSet, page, pageSize, statusFilter, null, null);
	}

	public TranslationUnitPage<TranslationUnitRow> getRows(SnolateTranslationSet translationSet, int page, int pageSize,
			TranslationStatus statusFilter, String englishSearch, String targetSearch) throws ServiceExceptionWithStatusCode {
		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		Sort sort = Sort.by("statusSort", "order", "code");
		Collection<String> englishConceptCodes = null;
		String trimmedEnglish = SnolateTranslationSearchService.normalizeOptionalSearchTerm(englishSearch);
		if (trimmedEnglish != null) {
			String conceptCode = SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch(trimmedEnglish);
			if (conceptCode != null) {
				if (conceptCode.isEmpty()) {
					return new TranslationUnitPage<TranslationUnitRow>(0, null, null, List.of()).withoutPagination();
				}
				englishConceptCodes = List.of(conceptCode);
			} else {
				englishConceptCodes = translationSearchService.findSourceCodesByTermSubstring(trimmedEnglish);
			}
		}
		String trimmedTarget = SnolateTranslationSearchService.normalizeOptionalSearchTerm(targetSearch);
		Page<TranslationUnit> pageResult = translationSearchService.pageUnitsInSet(setCode, lang,
				PageRequest.of(page, pageSize, sort), statusFilter, englishConceptCodes, trimmedTarget);
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
			TranslationUnitRow row = new TranslationUnitRow(List.of(english), target, u.getCode(), statusName);
			row.setSuggestions(copyAiSuggestions(Optional.of(u)));
			rows.add(row);
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
		row.setSuggestions(copyAiSuggestions(Optional.of(tu)));
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
		if (status == TranslationStatus.COMPLETE && unit.getStatus() != TranslationStatus.COMPLETE) {
			throw new ServiceExceptionWithStatusCode("COMPLETE is set automatically when synced with Snowstorm.", HttpStatus.BAD_REQUEST);
		}
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
		unit.setAiSuggestions(new ArrayList<>());
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
				.toList();
	}

	private static List<String> copyTerms(Optional<TranslationUnit> tu) {
		return tu.map(u -> new ArrayList<>(u.getTerms())).orElseGet(ArrayList::new);
	}

	private static List<String> copyAiSuggestions(Optional<TranslationUnit> tu) {
		return tu.filter(TranslationUnit::hasAiSuggestions)
				.map(u -> new ArrayList<>(u.getAiSuggestions()))
				.orElseGet(ArrayList::new);
	}

	/**
	 * Plain-text language/dialect label for CSV headers; strips trailing SNOMED-style refset name suffixes.
	 */
	public static String displayLanguageDialect(String refsetPreferredTerm) {
		if (refsetPreferredTerm == null) {
			return DEFAULT_TRANSLATION_LABEL;
		}
		String s = refsetPreferredTerm.trim();
		if (s.isEmpty()) {
			return DEFAULT_TRANSLATION_LABEL;
		}
		String lower = s.toLowerCase();
		for (String suffix : List.of("language reference set", "language refset")) {
			if (lower.endsWith(suffix)) {
				s = s.substring(0, s.length() - suffix.length()).trim();
				break;
			}
		}
		return s.isEmpty() ? DEFAULT_TRANSLATION_LABEL : s;
	}

	public void writeTranslationSetCsv(SnolateTranslationSet translationSet, TranslationStatus statusFilter,
			String languageDisplayName, OutputStream out) throws ServiceException {
		String dialect = languageDisplayName == null || languageDisplayName.isBlank()
				? DEFAULT_TRANSLATION_LABEL
				: languageDisplayName.trim();
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
			writeCsvLine(writer,
					"Concept Code",
					"English Term",
					dialect + " Preferred Term",
					"Other " + dialect + " Terms",
					"Status",
					"URL");
			int page = 0;
			boolean hasMore = true;
			while (hasMore) {
				TranslationUnitPage<TranslationUnitRow> pageResult = getRows(translationSet, page, CSV_EXPORT_PAGE_SIZE,
						statusFilter, null, null);
				List<TranslationUnitRow> rows = pageResult.results();
				if (rows == null || rows.isEmpty()) {
					hasMore = false;
				} else {
					for (TranslationUnitRow row : rows) {
						writeCsvDataRow(writer, row);
					}
					hasMore = rows.size() >= CSV_EXPORT_PAGE_SIZE;
					page++;
				}
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to write translation set CSV.", e);
		}
	}

	private static void writeCsvDataRow(BufferedWriter writer, TranslationUnitRow row) throws IOException {
		String conceptCode = row.getContext() != null ? row.getContext() : "";
		String englishTerm = firstTerm(row.getSource());
		List<String> targetTerms = row.getTarget() != null ? row.getTarget() : List.of();
		String preferredTerm = targetTerms.isEmpty() ? "" : targetTerms.get(0);
		String otherTerms = targetTerms.size() <= 1 ? "" : String.join("\n", targetTerms.subList(1, targetTerms.size()));
		String statusLabel = TranslationStatusLabels.radioLabel(row.getStatus());
		String url = conceptCode.isEmpty() ? "" : "https://snomed.info/id/" + conceptCode;
		writeCsvLine(writer, conceptCode, englishTerm, preferredTerm, otherTerms, statusLabel, url);
	}

	private static String firstTerm(List<String> terms) {
		return terms != null && !terms.isEmpty() ? terms.get(0) : "";
	}

	private static void writeCsvLine(BufferedWriter writer, String... fields) throws IOException {
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				writer.write(',');
			}
			writer.write(escapeCsvField(fields[i]));
		}
		writer.newLine();
	}

	static String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0;
		if (!needsQuotes) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
