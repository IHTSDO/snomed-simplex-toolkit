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

	private final LlmUsageDailyRepository repository = mock(LlmUsageDailyRepository.class);
	private final OpenAiPricingConfig pricingConfig = testPricingConfig();
	private final LlmUsageService service = new LlmUsageService(repository, null, null, pricingConfig);

	private static OpenAiPricingConfig testPricingConfig() {
		OpenAiPricingConfig config = new OpenAiPricingConfig();
		config.getModels().put("gpt-5.4", new OpenAiPricingConfig.ModelRates(2.50, 15.00));
		config.getModels().put("gpt-5.4-mini", new OpenAiPricingConfig.ModelRates(0.75, 4.50));
		return config;
	}

	@Test
	void documentIdIncludesCodesystemModelAndDate() {
		assertEquals("SNOMEDCT-ES|gpt-5.4-mini|2026-07-02",
				LlmUsageService.documentId("SNOMEDCT-ES", "gpt-5.4-mini", "2026-07-02"));
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
				new LlmUsageDaily("1", "SNOMEDCT-ES", "gpt-5.4-mini", "openai", todayString, 100, 50, 2, 25),
				new LlmUsageDaily("2", "SNOMEDCT-ES", "gpt-5.4", "openai", todayString, 200, 80, 1, 10),
				new LlmUsageDaily("3", "SNOMEDCT-DE", "gpt-5.4-mini", "openai", yesterdayString, 30, 10, 1, 5)
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
				.filter(item -> "gpt-5.4-mini".equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(130, fastModel.getInputTokens());
		assertEquals(60, fastModel.getOutputTokens());
		assertEquals(3, fastModel.getRequestCount());
		assertEquals(30, fastModel.getConceptsTranslated());
		assertEquals(0.0003675, fastModel.getCostUsd(), 1e-9);

		LlmUsageByModel goodModel = summary.getByModel().stream()
				.filter(item -> "gpt-5.4".equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(0.0017, goodModel.getCostUsd(), 1e-9);
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
				"SNOMEDCT-ES", "gpt-5.4-mini", startString, todayString))
				.thenReturn(List.of(
						new LlmUsageDaily("1", "SNOMEDCT-ES", "gpt-5.4-mini", "openai", todayString, 10, 5, 1, 3)
				));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.WEEK, "SNOMEDCT-ES", "gpt-5.4-mini");

		assertEquals("SNOMEDCT-ES", summary.getCodesystem());
		assertEquals("gpt-5.4-mini", summary.getModel());
		assertEquals(10, summary.getInputTokens());
		assertEquals(3, summary.getConceptsTranslated());
		assertEquals(1, summary.getByModel().size());
		assertEquals(1, summary.getDailyBreakdown().size());
	}

	@Test
	void getSummaryAllTimeUsesRepositoryWithoutDateFilter() throws ServiceExceptionWithStatusCode {
		when(repository.findAllByOrderByDateDesc()).thenReturn(List.of(
				new LlmUsageDaily("1", "SNOMEDCT-ES", "gpt-5.4-mini", "openai", "2024-01-01", 5, 2, 1)
		));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.ALL, null, null);

		assertNull(summary.getStartDate());
		assertNotNull(summary.getEndDate());
		assertEquals(5, summary.getInputTokens());
	}

	@Test
	void getSummaryCalculatesCostForDatedModelNames() throws ServiceExceptionWithStatusCode {
		LocalDate today = LlmUsageService.currentUtcLocalDate();
		String todayString = LlmUsageService.formatDate(today);

		when(repository.findByDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(anyString(), anyString()))
				.thenReturn(List.of(
						new LlmUsageDaily("1", "SNOMEDCT-ES", "gpt-5.4-2026-03-05", "openai", todayString, 200, 80, 1, 12),
						new LlmUsageDaily("2", "SNOMEDCT-ES", "gpt-5.4-mini-2026-03-17", "openai", todayString, 130, 60, 1, 8)
				));

		LlmUsageSummary summary = service.getSummary(LlmUsagePeriod.WEEK, null, null);

		LlmUsageByModel datedGoodModel = summary.getByModel().stream()
				.filter(item -> "gpt-5.4-2026-03-05".equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(0.0017, datedGoodModel.getCostUsd(), 1e-9);

		LlmUsageByModel datedMiniModel = summary.getByModel().stream()
				.filter(item -> "gpt-5.4-mini-2026-03-17".equals(item.getModel()))
				.findFirst()
				.orElseThrow();
		assertEquals(0.0003675, datedMiniModel.getCostUsd(), 1e-9);
	}
}
