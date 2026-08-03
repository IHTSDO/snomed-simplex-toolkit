package org.snomed.simplex.snolate.service;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationUnitPage;
import org.snomed.simplex.rest.pojos.TranslationUnitRow;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.SpreadsheetService;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationStatusLabels;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.snolate.service.ConceptListSupport.ConceptListParseResult;

import static org.snomed.simplex.snolate.service.ConceptListSupport.dedupeConceptIds;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.snomed.simplex.util.CsvParser;
import org.snomed.simplex.util.FileUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class SnolateTranslationService {

	private static final Logger logger = LoggerFactory.getLogger(SnolateTranslationService.class);

	private static final int ELASTIC_IO_CHUNK_SIZE = 1_000;

	private static final int CSV_EXPORT_BATCH_SIZE = 5_000;

	private static final String DEFAULT_TRANSLATION_LABEL = "Translation";

	private static final Set<TranslationStatus> ALLOWED_IMPORT_STATUSES = Set.of(
			TranslationStatus.NEEDS_EDIT,
			TranslationStatus.FOR_REVIEW,
			TranslationStatus.APPROVED);

	private final SnolateTranslationSourceRepository translationSourceRepository;
	private final SnolateTranslationSearchService translationSearchService;
	private final SnolateTranslationUnitStore translationUnitStore;

	public SnolateTranslationService(SnolateTranslationSourceRepository translationSourceRepository,
			SnolateTranslationSearchService translationSearchService,
			SnolateTranslationUnitStore translationUnitStore) {
		this.translationSourceRepository = translationSourceRepository;
		this.translationSearchService = translationSearchService;
		this.translationUnitStore = translationUnitStore;
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
		Optional<TranslationUnit> tuOpt = translationSearchService.findUnitInSet(setCode, lang, conceptId);
		if (tuOpt.isEmpty()) {
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
		Optional<TranslationUnit> tuOpt = translationSearchService.findUnitInSet(setCode, lang, conceptId);
		if (tuOpt.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Translation unit not found in this set.", HttpStatus.NOT_FOUND);
		}
		applyTermsAndStatusToUnit(tuOpt.get(), rawTerms, status);
	}

	private void applyTermsAndStatusToUnit(TranslationUnit unit, List<String> rawTerms, TranslationStatus status)
			throws ServiceExceptionWithStatusCode {
		applyTermsAndStatusInMemory(unit, rawTerms, status);
		translationUnitStore.save(unit);
	}

	private void applyTermsAndStatusInMemory(TranslationUnit unit, List<String> rawTerms, TranslationStatus status)
			throws ServiceExceptionWithStatusCode {
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

	public ConceptListParseResult parseConceptIdsFromFile(InputStream inputStream, String filename, String conceptColumn)
			throws ServiceException {
		org.snomed.simplex.service.ServiceHelper.requiredParameter("conceptColumn", conceptColumn);
		if (isSpreadsheetFile(filename)) {
			return parseConceptIdsFromSpreadsheet(inputStream, conceptColumn);
		}
		return parseConceptIdsFromCsv(inputStream, conceptColumn);
	}

	private ConceptListParseResult parseConceptIdsFromCsv(InputStream inputStream, String conceptColumn)
			throws ServiceException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String headerLine = reader.readLine();
			if (headerLine == null || headerLine.isBlank()) {
				throw new ServiceExceptionWithStatusCode("CSV file has no header row.", HttpStatus.BAD_REQUEST);
			}
			headerLine = FileUtils.removeUTF8BOM(headerLine);
			char delimiter = CsvParser.detectDelimiter(headerLine);
			List<String> headerFields = CsvParser.parseLine(headerLine, delimiter);
			Map<String, Integer> headerIndex = buildHeaderIndex(headerFields);
			int conceptIndex = resolveColumnIndex(headerIndex, conceptColumn.trim(), "Concept column");
			List<String> rawIds = new ArrayList<>();
			List<String> rowFields;
			while (!(rowFields = CsvParser.readRow(reader, delimiter)).isEmpty()) {
				if (isBlankCsvRow(rowFields)) {
					continue;
				}
				rawIds.add(getField(rowFields, conceptIndex));
			}
			return dedupeConceptIds(rawIds);
		} catch (IOException e) {
			throw new ServiceException("Failed to read concept list CSV.", e);
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		}
	}

	private ConceptListParseResult parseConceptIdsFromSpreadsheet(InputStream inputStream, String conceptColumn)
			throws ServiceException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(0);
			if (headerRow == null) {
				throw new ServiceExceptionWithStatusCode("Spreadsheet has no header row.", HttpStatus.BAD_REQUEST);
			}
			List<String> headerFields = readSpreadsheetHeaderFields(headerRow);
			Map<String, Integer> headerIndex = buildHeaderIndex(headerFields);
			int conceptIndex = resolveColumnIndex(headerIndex, conceptColumn.trim(), "Concept column");
			List<String> rawIds = new ArrayList<>();
			for (Row row : sheet) {
				if (row.getRowNum() == 0) {
					continue;
				}
				String conceptCode = SpreadsheetService.readSnomedConcept(row, conceptIndex, row.getRowNum() + 1);
				rawIds.add(conceptCode != null ? conceptCode : "");
			}
			return dedupeConceptIds(rawIds);
		} catch (IOException e) {
			throw new ServiceException("Failed to read concept list spreadsheet.", e);
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		}
	}

	public ChangeSummary importTranslationSetFile(SnolateTranslationSet translationSet, InputStream inputStream,
			String filename, String conceptColumn, List<String> termColumns, TranslationStatus status)
			throws ServiceException {
		return importTranslationSetFile(translationSet, inputStream, filename, conceptColumn, termColumns, status,
				OutsideSetBehavior.SKIP);
	}

	public ChangeSummary importTranslationSetFile(SnolateTranslationSet translationSet, InputStream inputStream,
			String filename, String conceptColumn, List<String> termColumns, TranslationStatus status,
			OutsideSetBehavior outsideSetBehavior) throws ServiceException {
		if (isSpreadsheetFile(filename)) {
			return importTranslationSetSpreadsheet(translationSet, inputStream, conceptColumn, termColumns, status,
					outsideSetBehavior);
		}
		return importTranslationSetCsv(translationSet, inputStream, conceptColumn, termColumns, status,
				outsideSetBehavior);
	}

	public ChangeSummary importTranslationLanguageFile(CodeSystem codeSystem, String refsetId, String languageCode,
			InputStream inputStream, String filename, String conceptColumn, List<String> termColumns,
			TranslationStatus status) throws ServiceException {
		if (isSpreadsheetFile(filename)) {
			return importTranslationLanguageSpreadsheet(codeSystem, refsetId, languageCode, inputStream, conceptColumn,
					termColumns, status);
		}
		return importTranslationLanguageCsv(codeSystem, refsetId, languageCode, inputStream, conceptColumn, termColumns,
				status);
	}

	public ChangeSummary importTranslationSetCsv(SnolateTranslationSet translationSet, InputStream inputStream,
			String conceptColumn, List<String> termColumns, TranslationStatus status) throws ServiceException {
		return importTranslationSetCsv(translationSet, inputStream, conceptColumn, termColumns, status, OutsideSetBehavior.SKIP);
	}

	public ChangeSummary importTranslationSetCsv(SnolateTranslationSet translationSet, InputStream inputStream,
			String conceptColumn, List<String> termColumns, TranslationStatus status, OutsideSetBehavior outsideSetBehavior)
			throws ServiceException {
		validateImportParameters(status, conceptColumn, termColumns);

		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		ChangeSummary changeSummary = new ChangeSummary();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			ImportColumnIndices columns = readImportColumnIndices(reader, conceptColumn, termColumns);
			importTranslationSetRows(reader, setCode, lang, columns, status, outsideSetBehavior, changeSummary);
			logImportSkips("Translation set CSV import", changeSummary);
		} catch (IOException e) {
			throw new ServiceException("Failed to read translation set CSV.", e);
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		}
		return changeSummary;
	}

	public ChangeSummary importTranslationLanguageCsv(CodeSystem codeSystem, String refsetId, String languageCode,
			InputStream inputStream, String conceptColumn, List<String> termColumns, TranslationStatus status)
			throws ServiceException {
		validateImportParameters(status, conceptColumn, termColumns);
		validateSnolateLinkedLanguage(codeSystem, refsetId);

		String compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		ChangeSummary changeSummary = new ChangeSummary();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			ImportColumnIndices columns = readImportColumnIndices(reader, conceptColumn, termColumns);
			importTranslationLanguageRows(reader, compositeLanguageCode, columns, status, changeSummary);
			logImportSkips("Translation language CSV import", changeSummary);
		} catch (IOException e) {
			throw new ServiceException("Failed to read translation language CSV.", e);
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		}
		return changeSummary;
	}

	private ChangeSummary importTranslationSetSpreadsheet(SnolateTranslationSet translationSet, InputStream inputStream,
			String conceptColumn, List<String> termColumns, TranslationStatus status,
			OutsideSetBehavior outsideSetBehavior) throws ServiceException {
		validateImportParameters(status, conceptColumn, termColumns);

		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		ChangeSummary changeSummary = new ChangeSummary();

		try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			SpreadsheetImportColumnIndices columns = readSpreadsheetImportColumnIndices(sheet, conceptColumn,
					termColumns);
			logger.info("Starting translation set spreadsheet import for set {}", setCode);
			importTranslationSetSpreadsheetRows(sheet, columns, setCode, lang, status, outsideSetBehavior,
					changeSummary);
			logImportSkips("Translation set file import", changeSummary);
			logger.info("Translation set spreadsheet import finished {}", changeSummary);
		} catch (IOException e) {
			throw new ServiceException("Failed to read translation set spreadsheet.", e);
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		} catch (ServiceException e) {
			throw new ServiceException("Failed to read translation set spreadsheet.", e);
		}
		return changeSummary;
	}

	private ChangeSummary importTranslationLanguageSpreadsheet(CodeSystem codeSystem, String refsetId,
			String languageCode, InputStream inputStream, String conceptColumn, List<String> termColumns,
			TranslationStatus status) throws ServiceException {
		validateImportParameters(status, conceptColumn, termColumns);
		validateSnolateLinkedLanguage(codeSystem, refsetId);

		String compositeLanguageCode = "%s-%s".formatted(languageCode, refsetId);
		ChangeSummary changeSummary = new ChangeSummary();

		try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			SpreadsheetImportColumnIndices columns = readSpreadsheetImportColumnIndices(sheet, conceptColumn,
					termColumns);
			logger.info("Starting translation language spreadsheet import for {}", compositeLanguageCode);
			importTranslationLanguageSpreadsheetRows(sheet, columns, compositeLanguageCode, status, changeSummary);
			logImportSkips("Translation language file import", changeSummary);
			logger.info("Translation language spreadsheet import finished {}", changeSummary);
		} catch (IOException e) {
			throw new ServiceException("Failed to read translation language spreadsheet.", e);
		} catch (ServiceExceptionWithStatusCode e) {
			throw e;
		} catch (ServiceException e) {
			throw new ServiceException("Failed to read translation language spreadsheet.", e);
		}
		return changeSummary;
	}

	private static boolean isSpreadsheetFile(String filename) {
		return filename != null && filename.toLowerCase().endsWith(".xlsx");
	}

	private SpreadsheetImportColumnIndices readSpreadsheetImportColumnIndices(Sheet sheet, String conceptColumn,
			List<String> termColumns) throws ServiceExceptionWithStatusCode {
		Row headerRow = sheet.getRow(0);
		if (headerRow == null) {
			throw new ServiceExceptionWithStatusCode("Spreadsheet has no header row.", HttpStatus.BAD_REQUEST);
		}
		List<String> headerFields = readSpreadsheetHeaderFields(headerRow);
		if (headerFields.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Spreadsheet has no header row.", HttpStatus.BAD_REQUEST);
		}
		Map<String, Integer> headerIndex = buildHeaderIndex(headerFields);
		int conceptIndex = resolveColumnIndex(headerIndex, conceptColumn.trim(), "Concept column");
		List<Integer> termColumnIndices = resolveTermColumnIndices(headerIndex, termColumns);
		return new SpreadsheetImportColumnIndices(conceptIndex, termColumnIndices, headerFields.size());
	}

	private static List<String> readSpreadsheetHeaderFields(Row headerRow) {
		List<String> headerFields = new ArrayList<>();
		int lastCellNum = headerRow.getLastCellNum();
		if (lastCellNum < 0) {
			return headerFields;
		}
		for (int i = 0; i < lastCellNum; i++) {
			headerFields.add(SpreadsheetService.readCellAsString(headerRow, i));
		}
		return headerFields;
	}

	private void importTranslationSetSpreadsheetRows(Sheet sheet, SpreadsheetImportColumnIndices columns,
			String setCode, String lang, TranslationStatus status, OutsideSetBehavior outsideSetBehavior,
			ChangeSummary changeSummary) throws ServiceException, ServiceExceptionWithStatusCode {
		Map<String, List<String>> termsByCode = parseSpreadsheetTermsByConceptCode(sheet, columns);
		logger.info("Translation set spreadsheet import parsed {} concept row(s)", termsByCode.size());
		if (termsByCode.isEmpty()) {
			return;
		}
		Map<String, TranslationUnit> unitsByCode = translationUnitStore.loadByCodes(lang, termsByCode.keySet());
		List<TranslationUnit> toSave = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : termsByCode.entrySet()) {
			TranslationUnit unit = unitsByCode.get(entry.getKey());
			if (unit == null) {
				changeSummary.incrementSkippedNotFound();
				logger.debug("Skipping concept {} not found for language {}", entry.getKey(), lang);
				continue;
			}
			if (!unit.getMemberOf().contains(setCode)) {
				if (outsideSetBehavior == OutsideSetBehavior.SKIP) {
					changeSummary.incrementSkippedOutsideSet();
					logger.debug("Skipping concept {} not found in translation set {}", entry.getKey(), setCode);
					continue;
				}
			}
			applyTermsAndStatusInMemory(unit, entry.getValue(), status);
			toSave.add(unit);
			changeSummary.incrementUpdated();
		}
		translationUnitStore.saveAll(toSave);
	}

	private void importTranslationLanguageSpreadsheetRows(Sheet sheet, SpreadsheetImportColumnIndices columns,
			String compositeLanguageCode, TranslationStatus status, ChangeSummary changeSummary)
			throws ServiceException, ServiceExceptionWithStatusCode {
		Map<String, List<String>> termsByCode = parseSpreadsheetTermsByConceptCode(sheet, columns);
		logger.info("Translation language spreadsheet import parsed {} concept row(s)", termsByCode.size());
		if (termsByCode.isEmpty()) {
			return;
		}
		Map<String, TranslationUnit> unitsByCode = translationUnitStore.loadByCodes(compositeLanguageCode, termsByCode.keySet());
		List<TranslationUnit> toSave = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : termsByCode.entrySet()) {
			TranslationUnit unit = unitsByCode.get(entry.getKey());
			if (unit == null) {
				changeSummary.incrementSkippedNotFound();
				logger.debug("Skipping concept {} not found for language {}", entry.getKey(), compositeLanguageCode);
				continue;
			}
			applyTermsAndStatusInMemory(unit, entry.getValue(), status);
			toSave.add(unit);
			changeSummary.incrementUpdated();
		}
		translationUnitStore.saveAll(toSave);
	}

	private Map<String, List<String>> parseSpreadsheetTermsByConceptCode(Sheet sheet,
			SpreadsheetImportColumnIndices columns) throws ServiceException, ServiceExceptionWithStatusCode {
		Map<String, List<String>> termsByCode = new LinkedHashMap<>();
		for (Row row : sheet) {
			if (row.getRowNum() == 0) {
				continue;
			}
			List<String> rowFields = readSpreadsheetRowFields(row, row.getRowNum() + 1, columns);
			if (isBlankCsvRow(rowFields)) {
				continue;
			}
			String conceptCode = getField(rowFields, columns.conceptIndex()).trim();
			if (conceptCode.isEmpty()) {
				continue;
			}
			List<String> terms = buildTermsFromRow(rowFields, columns.termColumnIndices());
			if (terms.isEmpty()) {
				continue;
			}
			termsByCode.put(conceptCode, terms);
		}
		return termsByCode;
	}

	private List<String> readSpreadsheetRowFields(Row row, int rowNumber, SpreadsheetImportColumnIndices columns)
			throws ServiceException {
		List<String> rowFields = new ArrayList<>(columns.columnCount());
		for (int columnIndex = 0; columnIndex < columns.columnCount(); columnIndex++) {
			if (columnIndex == columns.conceptIndex()) {
				String conceptCode = SpreadsheetService.readSnomedConcept(row, columnIndex, rowNumber);
				rowFields.add(conceptCode != null ? conceptCode : "");
			} else {
				rowFields.add(SpreadsheetService.readCellAsString(row, columnIndex));
			}
		}
		return rowFields;
	}

	private static void validateSnolateLinkedLanguage(CodeSystem codeSystem, String refsetId)
			throws ServiceExceptionWithStatusCode {
		Map<String, String> snolateLanguages = codeSystem.getTranslationSnolateLanguages();
		if (snolateLanguages == null || !snolateLanguages.containsKey(refsetId)) {
			throw new ServiceExceptionWithStatusCode(
					"Language is not linked to Translation Studio.", HttpStatus.BAD_REQUEST);
		}
	}

	private static void logImportSkips(String label, ChangeSummary changeSummary) {
		int skippedNotFound = changeSummary.getSkippedNotFound();
		int skippedOutsideSet = changeSummary.getSkippedOutsideSet();
		if (skippedNotFound > 0 || skippedOutsideSet > 0) {
			logger.info("{} skipped {} row(s) not found and {} row(s) outside set.",
					label, skippedNotFound, skippedOutsideSet);
		}
	}

	private static void validateImportParameters(TranslationStatus status, String conceptColumn, List<String> termColumns)
			throws ServiceExceptionWithStatusCode {
		if (status == null || !ALLOWED_IMPORT_STATUSES.contains(status)) {
			throw new ServiceExceptionWithStatusCode(
					"Import status must be NEEDS_EDIT, FOR_REVIEW, or APPROVED.", HttpStatus.BAD_REQUEST);
		}
		if (conceptColumn == null || conceptColumn.isBlank()) {
			throw new ServiceExceptionWithStatusCode("Concept column is required.", HttpStatus.BAD_REQUEST);
		}
		if (termColumns == null || termColumns.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("At least one term column is required.", HttpStatus.BAD_REQUEST);
		}
	}

	private static ImportColumnIndices readImportColumnIndices(BufferedReader reader, String conceptColumn,
			List<String> termColumns) throws IOException, ServiceExceptionWithStatusCode {

		String headerLine = reader.readLine();
		if (headerLine == null || headerLine.isBlank()) {
			throw new ServiceExceptionWithStatusCode("CSV file has no header row.", HttpStatus.BAD_REQUEST);
		}
		headerLine = FileUtils.removeUTF8BOM(headerLine);
		char delimiter = CsvParser.detectDelimiter(headerLine);
		List<String> headerFields = CsvParser.parseLine(headerLine, delimiter);
		if (headerFields.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("CSV file has no header row.", HttpStatus.BAD_REQUEST);
		}
		Map<String, Integer> headerIndex = buildHeaderIndex(headerFields);
		int conceptIndex = resolveColumnIndex(headerIndex, conceptColumn.trim(), "Concept column");
		List<Integer> termColumnIndices = resolveTermColumnIndices(headerIndex, termColumns);
		return new ImportColumnIndices(conceptIndex, termColumnIndices, delimiter);
	}

	private static List<Integer> resolveTermColumnIndices(Map<String, Integer> headerIndex, List<String> termColumns)
			throws ServiceExceptionWithStatusCode {
		List<Integer> termColumnIndices = new ArrayList<>();
		for (String column : termColumns) {
			String trimmed = column.trim();
			if (!trimmed.isEmpty()) {
				termColumnIndices.add(resolveColumnIndex(headerIndex, trimmed, "Term column"));
			}
		}
		if (termColumnIndices.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("At least one term column is required.", HttpStatus.BAD_REQUEST);
		}
		return termColumnIndices;
	}

	private void importTranslationSetRows(BufferedReader reader, String setCode, String lang,
			ImportColumnIndices columns, TranslationStatus status, OutsideSetBehavior outsideSetBehavior,
			ChangeSummary changeSummary) throws IOException, ServiceExceptionWithStatusCode {
		List<String> rowFields;
		while (!(rowFields = CsvParser.readRow(reader, columns.delimiter())).isEmpty()) {
			processImportRow(rowFields, columns, (conceptCode, terms) -> {
				Optional<TranslationUnit> tuOpt = translationUnitStore.loadByCode(lang, conceptCode);
				if (tuOpt.isEmpty()) {
					changeSummary.incrementSkippedNotFound();
					logger.debug("Skipping concept {} not found for language {}", conceptCode, lang);
					return;
				}
				TranslationUnit unit = tuOpt.get();
				if (!unit.getMemberOf().contains(setCode)) {
					if (outsideSetBehavior == OutsideSetBehavior.SKIP) {
						changeSummary.incrementSkippedOutsideSet();
						logger.debug("Skipping concept {} not found in translation set {}", conceptCode, setCode);
						return;
					}
					applyTermsAndStatusToUnit(unit, terms, status);
					changeSummary.incrementUpdated();
					return;
				}
				applyTermsAndStatusToUnit(unit, terms, status);
				changeSummary.incrementUpdated();
			});
		}
	}

	private void importTranslationLanguageRows(BufferedReader reader, String compositeLanguageCode,
			ImportColumnIndices columns, TranslationStatus status, ChangeSummary changeSummary)
			throws IOException, ServiceExceptionWithStatusCode {
		List<String> rowFields;
		while (!(rowFields = CsvParser.readRow(reader, columns.delimiter())).isEmpty()) {
			processImportRow(rowFields, columns, (conceptCode, terms) -> {
				Optional<TranslationUnit> tuOpt = translationUnitStore.loadByCode(compositeLanguageCode, conceptCode);
				if (tuOpt.isEmpty()) {
					changeSummary.incrementSkippedNotFound();
					logger.debug("Skipping concept {} not found for language {}", conceptCode, compositeLanguageCode);
					return;
				}
				applyTermsAndStatusToUnit(tuOpt.get(), terms, status);
				changeSummary.incrementUpdated();
			});
		}
	}

	@FunctionalInterface
	private interface ImportRowHandler {
		void handle(String conceptCode, List<String> terms) throws ServiceExceptionWithStatusCode;
	}

	private void processImportRow(List<String> rowFields, ImportColumnIndices columns, ImportRowHandler handler)
			throws ServiceExceptionWithStatusCode {
		if (isBlankCsvRow(rowFields)) {
			return;
		}
		String conceptCode = getField(rowFields, columns.conceptIndex()).trim();
		if (conceptCode.isEmpty()) {
			return;
		}
		List<String> terms = buildTermsFromRow(rowFields, columns.termColumnIndices());
		if (terms.isEmpty()) {
			return;
		}
		handler.handle(conceptCode, terms);
	}

	private static boolean isBlankCsvRow(List<String> rowFields) {
		return rowFields.isEmpty() || rowFields.stream().allMatch(String::isBlank);
	}

	private record ImportColumnIndices(int conceptIndex, List<Integer> termColumnIndices, char delimiter) {}

	private record SpreadsheetImportColumnIndices(int conceptIndex, List<Integer> termColumnIndices, int columnCount) {}

	public void writeTranslationSetCsv(SnolateTranslationSet translationSet, TranslationStatus statusFilter,
			String languageDisplayName, OutputStream out) throws ServiceException {
		String dialect = languageDisplayName == null || languageDisplayName.isBlank()
				? DEFAULT_TRANSLATION_LABEL
				: languageDisplayName.trim();
		String setCode = translationSet.getCompositeSetCode();
		String lang = translationSet.getLanguageCodeWithRefsetId();
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
			writeCsvLine(writer,
					"Concept Code",
					"English Term",
					dialect + " Preferred Term",
					"Other " + dialect + " Terms",
					"Status",
					"URL");
			List<TranslationUnit> batch = new ArrayList<>(CSV_EXPORT_BATCH_SIZE);
			translationSearchService.forEachUnitInSet(setCode, lang, statusFilter,
					SnolateTranslationSearchService.UNITS_IN_SET_EXPORT_SORT, unit -> appendExportBatch(unit, batch, writer));
			if (!batch.isEmpty()) {
				flushExportBatch(batch, writer);
			}
		} catch (UncheckedIOException e) {
			throw new ServiceException("Failed to write translation set CSV.", e.getCause());
		} catch (IOException e) {
			throw new ServiceException("Failed to write translation set CSV.", e);
		}
	}

	private void appendExportBatch(TranslationUnit unit, List<TranslationUnit> batch, BufferedWriter writer) {
		batch.add(unit);
		if (batch.size() >= CSV_EXPORT_BATCH_SIZE) {
			try {
				flushExportBatch(batch, writer);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	private void flushExportBatch(List<TranslationUnit> batch, BufferedWriter writer) throws IOException {
		List<String> codes = batch.stream().map(TranslationUnit::getCode).toList();
		Map<String, TranslationSource> sourceByCode = loadSourcesByCodes(codes);
		for (TranslationUnit unit : batch) {
			TranslationSource source = sourceByCode.get(unit.getCode());
			String englishTerm = source != null ? source.getTerm() : "";
			writeCsvDataRow(writer, unit, englishTerm);
		}
		batch.clear();
	}

	private Map<String, TranslationSource> loadSourcesByCodes(Collection<String> codes) {
		if (codes.isEmpty()) {
			return Map.of();
		}
		List<String> codeList = codes instanceof List<String> list ? list : new ArrayList<>(codes);
		Map<String, TranslationSource> sourcesByCode = new HashMap<>();
		for (int i = 0; i < codeList.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, codeList.size());
			StreamSupport.stream(translationSourceRepository.findAllById(codeList.subList(i, end)).spliterator(), false)
					.forEach(source -> sourcesByCode.put(source.getCode(), source));
		}
		return sourcesByCode;
	}

	private static void writeCsvDataRow(BufferedWriter writer, TranslationUnit unit, String englishTerm) throws IOException {
		String conceptCode = unit.getCode() != null ? unit.getCode() : "";
		List<String> targetTerms = unit.getTerms() != null ? unit.getTerms() : List.of();
		String preferredTerm = targetTerms.isEmpty() ? "" : targetTerms.get(0);
		String otherTerms = targetTerms.size() <= 1 ? "" : String.join("\n", targetTerms.subList(1, targetTerms.size()));
		String statusLabel = TranslationStatusLabels.radioLabel(unit.getStatus());
		String url = conceptCode.isEmpty() ? "" : "https://snomed.info/id/" + conceptCode;
		writeCsvLine(writer, conceptCode, englishTerm, preferredTerm, otherTerms, statusLabel, url);
	}

	private static void writeCsvLine(BufferedWriter writer, String... fields) throws IOException {
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) {
				writer.write('\t');
			}
			writer.write(escapeCsvField(fields[i]));
		}
		writer.newLine();
	}

	static String escapeCsvField(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.indexOf('\t') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0;
		if (!needsQuotes) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static Map<String, Integer> buildHeaderIndex(List<String> headerFields) {
		Map<String, Integer> headerIndex = new LinkedHashMap<>();
		for (int i = 0; i < headerFields.size(); i++) {
			headerIndex.put(headerFields.get(i).trim(), i);
		}
		return headerIndex;
	}

	private static int resolveColumnIndex(Map<String, Integer> headerIndex, String columnName, String columnLabel)
			throws ServiceExceptionWithStatusCode {
		Integer index = headerIndex.get(columnName);
		if (index == null) {
			throw new ServiceExceptionWithStatusCode(
					columnLabel + " '" + columnName + "' was not found in the file header.", HttpStatus.BAD_REQUEST);
		}
		return index;
	}

	private static String getField(List<String> row, int index) {
		if (index < 0 || index >= row.size()) {
			return "";
		}
		String value = row.get(index);
		return value != null ? value : "";
	}

	private static List<String> buildTermsFromRow(List<String> row, List<Integer> termColumnIndices) {
		List<String> terms = new ArrayList<>();
		for (int index : termColumnIndices) {
			String cell = getField(row, index);
			if (cell.isBlank()) {
				continue;
			}
			for (String part : cell.split("\\R")) {
				String trimmed = part.trim();
				if (!trimmed.isEmpty()) {
					terms.add(trimmed);
				}
			}
		}
		return terms;
	}
}
