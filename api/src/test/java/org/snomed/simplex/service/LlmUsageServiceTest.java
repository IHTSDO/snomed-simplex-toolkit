package org.snomed.simplex.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.ai.LlmUsageDaily;
import org.snomed.simplex.config.OpenAiPricingConfig;
import org.snomed.simplex.rest.pojos.LlmUsageByModel;
import org.snomed.simplex.rest.pojos.LlmUsageDailyBreakdown;
import org.snomed.simplex.rest.pojos.LlmUsageSummary;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmUsageServiceTest {

	private static final String FAST_MODEL = "model-a";
	private static final String GOOD_MODEL = "model-b";
	private static final String GOOD_MODEL_FAST_BILLING = "model-b-fast";

	private final LlmUsageDailyRepository repository = mock(LlmUsageDailyRepository.class);
	private final OpenAiPricingConfig pricingConfig = testPricingConfig();
	private final LlmUsageService service = new LlmUsageService(repository, null, null, pricingConfig);

	private static OpenAiPricingConfig testPricingConfig() {
		OpenAiPricingConfig config = new OpenAiPricingConfig();
		config.getModels().put(FAST_MODEL, new OpenAiPricingConfig.ModelRates(1.0, 2.0));
		config.getModels().put(GOOD_MODEL, new OpenAiPricingConfig.ModelRates(3.0, 4.0));
		config.getModels().put(GOOD_MODEL_FAST_BILLING, new OpenAiPricingConfig.ModelRates(6.0, 8.0));
		return config;
	}

	@Test
	void documentIdIncludesCodesystemModelAndDate() {
		assertEquals("SNOMEDCT-ES|model-a|2026-07-02",
				LlmUsageService.documentId("SNOMEDCT-ES", FAST_MODEL, "2026-07-02"));
	}

	@Test
	void periodFromParamAcceptsKnownValues() throws ServiceExceptionWithStatusCode {
		assertEquals(LlmUsagePeriod.WEEK, LlmUsagePeriod.fromParam("week"));
		assertEquals(LlmUsagePeriod.THREE_MONTHS, LlmUsagePeriod.fromParam("3months"));
	}

	@Test
	void periodFromParamRejectsUnknownValue() {
		assertThrows(ServiceExceptionWithStatusCode.class, () -> LlmUsagePeriod.fromParam("fortnight"));
	}

	@Test
	void getSummaryAggregatesTotalsAndByModel() throws ServiceExceptionWithStatusCode {
		LocalDate today = LlmUsageService.currentUtcLocalDate();
		String todayString = LlmUsageService.formatDate(today);
		String yesterdayString = LlmUsageService.formatDate(today.minusDays(1));

		List<LlmUsageDaily> records = List.of(
				new LlmUsageDaily("1", "SNOMEDCT-ES", FAST_MODEL, "openai", todayString, 100, 50, 2, 25),
				new LlmUsageDaily("2", "SNOMEDCT-ES", GOOD_MODEL, "openai", todayString, 200, 80, 1, 10),
				new LlmUsageDaily("3", "SNOMEDCT-DE", FAST_MODEL, "openai", yesterdayString, 30, 10, 1, 5)
		);

		when(repository.findByDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(anyString(), anyString()))
				.thenReturn(records);

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.WEEK, null, null);

		assertEquals("week", summary.getPeriod());
		assertEquals(330, summary.getInputTokens());
		assertEquals(140, summary.getOutputTokens());
		assertEquals(470, summary.getTotalTokens());
		assertEquals(4, summary.getRequestCount());
		assertEquals(40, summary.getConceptsTranslated());
		assertEquals(2, summary.getByModel().size());

		LlmUsageByModel fastModel = summary.getByModel().stream()
				.filter(item -> FAST_MODEL.equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(130, fastModel.getInputTokens());
		assertEquals(60, fastModel.getOutputTokens());
		assertEquals(3, fastModel.getRequestCount());
		assertEquals(30, fastModel.getConceptsTranslated());
		assertEquals(pricingConfig.calculateCostUsd(FAST_MODEL, 130, 60), fastModel.getCostUsd(), 1e-9);

		LlmUsageByModel goodModel = summary.getByModel().stream()
				.filter(item -> GOOD_MODEL.equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(pricingConfig.calculateCostUsd(GOOD_MODEL, 200, 80), goodModel.getCostUsd(), 1e-9);
		assertEquals(10, goodModel.getConceptsTranslated());

		assertEquals(3, summary.getDailyBreakdown().size());
		LlmUsageDailyBreakdown firstRow = summary.getDailyBreakdown().get(0);
		assertEquals(todayString, firstRow.getDate());
	}

	@Test
	void getSummaryFiltersByCodesystemAndModel() throws ServiceExceptionWithStatusCode {
		LocalDate today = LlmUsageService.currentUtcLocalDate();
		String todayString = LlmUsageService.formatDate(today);
		String startString = LlmUsageService.formatDate(today.minusDays(6));

		when(repository.findByCodesystemAndModelAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
				"SNOMEDCT-ES", FAST_MODEL, startString, todayString))
				.thenReturn(List.of(
						new LlmUsageDaily("1", "SNOMEDCT-ES", FAST_MODEL, "openai", todayString, 10, 5, 1, 3)
				));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.WEEK, "SNOMEDCT-ES", FAST_MODEL);

		assertEquals("SNOMEDCT-ES", summary.getCodesystem());
		assertEquals(FAST_MODEL, summary.getModel());
		assertEquals(10, summary.getInputTokens());
		assertEquals(3, summary.getConceptsTranslated());
		assertEquals(1, summary.getByModel().size());
		assertEquals(1, summary.getDailyBreakdown().size());
	}

	@Test
	void getSummaryAllTimeUsesRepositoryWithoutDateFilter() throws ServiceExceptionWithStatusCode {
		when(repository.findAllByOrderByDateDesc()).thenReturn(List.of(
				new LlmUsageDaily("1", "SNOMEDCT-ES", FAST_MODEL, "openai", "2024-01-01", 5, 2, 1)
		));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.ALL, null, null);

		assertNull(summary.getStartDate());
		assertNotNull(summary.getEndDate());
		assertEquals(5, summary.getInputTokens());
	}

	@Test
	void getSummaryAggregatesBillingModelsSeparately() throws ServiceExceptionWithStatusCode {
		LocalDate today = LlmUsageService.currentUtcLocalDate();
		String todayString = LlmUsageService.formatDate(today);

		when(repository.findByDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(anyString(), anyString()))
				.thenReturn(List.of(
						new LlmUsageDaily("1", "SNOMEDCT-ES", GOOD_MODEL, "openai", todayString, 200, 80, 1, 10),
						new LlmUsageDaily("2", "SNOMEDCT-ES", GOOD_MODEL_FAST_BILLING, "openai", todayString, 130, 60, 2, 25)
				));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.WEEK, null, null);

		assertEquals(2, summary.getByModel().size());

		LlmUsageByModel standardModel = summary.getByModel().stream()
				.filter(item -> GOOD_MODEL.equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(pricingConfig.calculateCostUsd(GOOD_MODEL, 200, 80), standardModel.getCostUsd(), 1e-9);

		LlmUsageByModel fastBillingModel = summary.getByModel().stream()
				.filter(item -> GOOD_MODEL_FAST_BILLING.equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(pricingConfig.calculateCostUsd(GOOD_MODEL_FAST_BILLING, 130, 60), fastBillingModel.getCostUsd(), 1e-9);
	}

	@Test
	void getSummaryCalculatesCostForDatedModelNames() throws ServiceExceptionWithStatusCode {
		LocalDate today = LlmUsageService.currentUtcLocalDate();
		String todayString = LlmUsageService.formatDate(today);
		String datedGoodModel = GOOD_MODEL + "-2026-03-05";
		String datedFastModel = FAST_MODEL + "-2026-03-17";

		when(repository.findByDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(anyString(), anyString()))
				.thenReturn(List.of(
						new LlmUsageDaily("1", "SNOMEDCT-ES", datedGoodModel, "openai", todayString, 200, 80, 1, 12),
						new LlmUsageDaily("2", "SNOMEDCT-ES", datedFastModel, "openai", todayString, 130, 60, 1, 8)
				));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.WEEK, null, null);

		LlmUsageByModel datedGood = summary.getByModel().stream()
				.filter(item -> datedGoodModel.equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(pricingConfig.calculateCostUsd(datedGoodModel, 200, 80), datedGood.getCostUsd(), 1e-9);

		LlmUsageByModel datedFast = summary.getByModel().stream()
				.filter(item -> datedFastModel.equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(pricingConfig.calculateCostUsd(datedFastModel, 130, 60), datedFast.getCostUsd(), 1e-9);
	}
}
