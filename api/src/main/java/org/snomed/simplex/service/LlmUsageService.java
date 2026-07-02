package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.ai.LlmUsageDaily;
import org.snomed.simplex.ai.LlmUsageRecord;
import org.snomed.simplex.config.IndexNameProvider;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.LlmUsageByModel;
import org.snomed.simplex.rest.pojos.LlmUsageDailyBreakdown;
import org.snomed.simplex.rest.pojos.LlmUsageSummary;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmUsageService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final String INCREMENT_SCRIPT = """
			ctx._source.inputTokens += params.inputTokens;
			ctx._source.outputTokens += params.outputTokens;
			ctx._source.requestCount += 1;
			""";

	private final LlmUsageDailyRepository repository;
	private final ElasticsearchOperations elasticsearchOperations;
	private final IndexNameProvider indexNameProvider;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public LlmUsageService(LlmUsageDailyRepository repository, ElasticsearchOperations elasticsearchOperations,
			IndexNameProvider indexNameProvider) {
		this.repository = repository;
		this.elasticsearchOperations = elasticsearchOperations;
		this.indexNameProvider = indexNameProvider;
	}

	/**
	 * Records token usage for the current UTC calendar day.
	 */
	public void recordUsage(LlmUsageRecord record) {
		if (record == null || record.codesystem() == null || record.codesystem().isBlank()) {
			return;
		}
		String date = currentUtcDate();
		String id = documentId(record.codesystem(), record.model(), date);
		Map<String, Object> upsert = Map.of(
				"codesystem", record.codesystem(),
				"model", record.model(),
				"provider", record.provider(),
				"date", date,
				"inputTokens", (long) record.inputTokens(),
				"outputTokens", (long) record.outputTokens(),
				"requestCount", 1L
		);
		Map<String, Object> params = Map.of(
				"inputTokens", record.inputTokens(),
				"outputTokens", record.outputTokens()
		);

		UpdateQuery updateQuery = UpdateQuery.builder(id)
				.withScriptType(ScriptType.INLINE)
				.withLang("painless")
				.withScript(INCREMENT_SCRIPT)
				.withParams(params)
				.withUpsert(Document.from(upsert))
				.withRetryOnConflict(3)
				.build();

		IndexCoordinates index = IndexCoordinates.of(indexNameProvider.indexName("llm-usage-daily"));
		elasticsearchOperations.update(updateQuery, index);
		logger.debug("Recorded LLM usage for {} model {} on {}", record.codesystem(), record.model(), date);
	}

	public LlmUsageSummary getSummary(LlmUsagePeriod period, String codesystem, String model) throws ServiceExceptionWithStatusCode {
		LocalDate endDate = currentUtcLocalDate();
		LocalDate startDate = period == LlmUsagePeriod.ALL ? null : endDate.minusDays(period.getDays() - 1L);

		String startDateString = startDate != null ? formatDate(startDate) : null;
		String endDateString = formatDate(endDate);

		List<LlmUsageDaily> records = fetchRecords(codesystem, model, startDateString, endDateString);

		LlmUsageSummary summary = new LlmUsageSummary();
		summary.setPeriod(period.getParam());
		summary.setCodesystem(codesystem);
		summary.setModel(model);
		summary.setStartDate(startDateString);
		summary.setEndDate(endDateString);

		long inputTokens = 0;
		long outputTokens = 0;
		long requestCount = 0;
		Map<String, ModelTotals> byModelMap = new LinkedHashMap<>();

		for (LlmUsageDaily record : records) {
			inputTokens += record.getInputTokens();
			outputTokens += record.getOutputTokens();
			requestCount += record.getRequestCount();

			String modelKey = record.getModel() + "|" + record.getProvider();
			ModelTotals totals = byModelMap.computeIfAbsent(modelKey,
					key -> new ModelTotals(record.getModel(), record.getProvider()));
			totals.add(record);
		}

		summary.setInputTokens(inputTokens);
		summary.setOutputTokens(outputTokens);
		summary.setTotalTokens(inputTokens + outputTokens);
		summary.setRequestCount(requestCount);

		List<LlmUsageByModel> byModel = byModelMap.values().stream()
				.map(ModelTotals::toSummary)
				.sorted(Comparator.comparing(LlmUsageByModel::getModel))
				.toList();
		summary.setByModel(byModel);

		List<LlmUsageDailyBreakdown> dailyBreakdown = records.stream()
				.sorted(Comparator.comparing(LlmUsageDaily::getDate).reversed()
						.thenComparing(LlmUsageDaily::getCodesystem)
						.thenComparing(LlmUsageDaily::getModel))
				.map(record -> new LlmUsageDailyBreakdown(
						record.getDate(),
						record.getCodesystem(),
						record.getModel(),
						record.getProvider(),
						record.getInputTokens(),
						record.getOutputTokens(),
						record.getRequestCount()))
				.toList();
		summary.setDailyBreakdown(dailyBreakdown);

		return summary;
	}

	static LocalDate currentUtcLocalDate() {
		return LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
	}

	static String formatDate(LocalDate date) {
		return date.format(DATE_FORMAT);
	}

	static String documentId(String codesystem, String model, String date) {
		return codesystem + "|" + model + "|" + date;
	}

	private String currentUtcDate() {
		return formatDate(currentUtcLocalDate());
	}

	private List<LlmUsageDaily> fetchRecords(String codesystem, String model, String startDate, String endDate) {
		boolean hasCodesystem = codesystem != null && !codesystem.isBlank();
		boolean hasModel = model != null && !model.isBlank();
		boolean allTime = startDate == null;

		if (allTime) {
			if (hasCodesystem && hasModel) {
				return repository.findByCodesystemAndModelOrderByDateDesc(codesystem, model);
			}
			if (hasCodesystem) {
				return repository.findByCodesystemOrderByDateDesc(codesystem);
			}
			if (hasModel) {
				return repository.findByModelOrderByDateDesc(model);
			}
			return repository.findAllByOrderByDateDesc();
		}

		if (hasCodesystem && hasModel) {
			return repository.findByCodesystemAndModelAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
					codesystem, model, startDate, endDate);
		}
		if (hasCodesystem) {
			return repository.findByCodesystemAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
					codesystem, startDate, endDate);
		}
		if (hasModel) {
			return repository.findByModelAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
					model, startDate, endDate);
		}
		return repository.findByDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(startDate, endDate);
	}

	private static final class ModelTotals {
		private final String model;
		private final String provider;
		private long inputTokens;
		private long outputTokens;
		private long requestCount;

		private ModelTotals(String model, String provider) {
			this.model = model;
			this.provider = provider;
		}

		private void add(LlmUsageDaily record) {
			inputTokens += record.getInputTokens();
			outputTokens += record.getOutputTokens();
			requestCount += record.getRequestCount();
		}

		private LlmUsageByModel toSummary() {
			return new LlmUsageByModel(model, provider, inputTokens, outputTokens, requestCount);
		}
	}
}
