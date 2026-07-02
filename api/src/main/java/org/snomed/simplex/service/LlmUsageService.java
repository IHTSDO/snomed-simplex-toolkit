package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.ai.LlmUsageDaily;
import org.snomed.simplex.ai.LlmUsageRecord;
import org.snomed.simplex.config.IndexNameProvider;
import org.snomed.simplex.config.OpenAiPricingConfig;
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
      if (ctx._source.conceptsTranslated == null) {
        ctx._source.conceptsTranslated = 0;
      }
      ctx._source.conceptsTranslated += params.conceptsTranslated;
      """;

	private final LlmUsageDailyRepository repository;
	private final ElasticsearchOperations elasticsearchOperations;
	private final IndexNameProvider indexNameProvider;
	private final OpenAiPricingConfig openAiPricingConfig;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public LlmUsageService(LlmUsageDailyRepository repository, ElasticsearchOperations elasticsearchOperations,
			IndexNameProvider indexNameProvider, OpenAiPricingConfig openAiPricingConfig) {
		this.repository = repository;
		this.elasticsearchOperations = elasticsearchOperations;
		this.indexNameProvider = indexNameProvider;
		this.openAiPricingConfig = openAiPricingConfig;
	}

	/**
	 * Records token usage for the current UTC calendar day.
	 */
	public void recordUsage(LlmUsageRecord useRecord) {
		if (useRecord == null || useRecord.codesystem() == null || useRecord.codesystem().isBlank()) {
			return;
		}
		String date = currentUtcDate();
		String id = documentId(useRecord.codesystem(), useRecord.model(), date);
		Map<String, Object> upsert = Map.of(
				"codesystem", useRecord.codesystem(),
				"model", useRecord.model(),
				"provider", useRecord.provider(),
				"date", date,
				"inputTokens", (long) useRecord.inputTokens(),
				"outputTokens", (long) useRecord.outputTokens(),
				"requestCount", 1L,
				"conceptsTranslated", (long) useRecord.conceptsTranslated()
		);
		Map<String, Object> params = Map.of(
				"inputTokens", useRecord.inputTokens(),
				"outputTokens", useRecord.outputTokens(),
				"conceptsTranslated", useRecord.conceptsTranslated()
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
		logger.debug("Recorded LLM usage for {} model {} on {}", useRecord.codesystem(), useRecord.model(), date);
	}

	public LlmUsageSummary getSummary(LlmUsagePeriod period, String codesystem, String model) {
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
		long conceptsTranslated = 0;
		Map<String, ModelTotals> byModelMap = new LinkedHashMap<>();

		for (LlmUsageDaily useRecord : records) {
			inputTokens += useRecord.getInputTokens();
			outputTokens += useRecord.getOutputTokens();
			requestCount += useRecord.getRequestCount();
			conceptsTranslated += useRecord.getConceptsTranslated();

			String modelKey = useRecord.getModel() + "|" + useRecord.getProvider();
			ModelTotals totals = byModelMap.computeIfAbsent(modelKey,
					key -> new ModelTotals(useRecord.getModel(), useRecord.getProvider(), openAiPricingConfig));
			totals.add(useRecord);
		}

		summary.setInputTokens(inputTokens);
		summary.setOutputTokens(outputTokens);
		summary.setTotalTokens(inputTokens + outputTokens);
		summary.setRequestCount(requestCount);
		summary.setConceptsTranslated(conceptsTranslated);

		List<LlmUsageByModel> byModel = byModelMap.values().stream()
				.map(ModelTotals::toSummary)
				.sorted(Comparator.comparing(LlmUsageByModel::getModel))
				.toList();
		summary.setByModel(byModel);

		List<LlmUsageDailyBreakdown> dailyBreakdown = records.stream()
				.sorted(Comparator.comparing(LlmUsageDaily::getDate).reversed()
						.thenComparing(LlmUsageDaily::getCodesystem)
						.thenComparing(LlmUsageDaily::getModel))
				.map(useRecord -> new LlmUsageDailyBreakdown(
						useRecord.getDate(),
						useRecord.getCodesystem(),
						useRecord.getModel(),
						useRecord.getProvider(),
						useRecord.getInputTokens(),
						useRecord.getOutputTokens(),
						useRecord.getRequestCount(),
						useRecord.getConceptsTranslated()))
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
		private final OpenAiPricingConfig openAiPricingConfig;
		private long inputTokens;
		private long outputTokens;
		private long requestCount;
		private long conceptsTranslated;

		private ModelTotals(String model, String provider, OpenAiPricingConfig openAiPricingConfig) {
			this.model = model;
			this.provider = provider;
			this.openAiPricingConfig = openAiPricingConfig;
		}

		private void add(LlmUsageDaily useRecord) {
			inputTokens += useRecord.getInputTokens();
			outputTokens += useRecord.getOutputTokens();
			requestCount += useRecord.getRequestCount();
			conceptsTranslated += useRecord.getConceptsTranslated();
		}

		private LlmUsageByModel toSummary() {
			Double costUsd = openAiPricingConfig.calculateCostUsd(model, inputTokens, outputTokens);
			return new LlmUsageByModel(model, provider, inputTokens, outputTokens, requestCount, conceptsTranslated, costUsd);
		}
	}
}
