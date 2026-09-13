package org.snomed.simplex.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiPricingConfigTest {

	private static final double FAST_INPUT_RATE = 1.0;
	private static final double FAST_OUTPUT_RATE = 2.0;
	private static final double GOOD_INPUT_RATE = 3.0;
	private static final double GOOD_OUTPUT_RATE = 4.0;

	@Test
	void normalizeModelNameStripsDateSuffix() {
		assertEquals("model-a", OpenAiPricingConfig.normalizeModelName("model-a-2026-03-05"));
		assertEquals("model-b", OpenAiPricingConfig.normalizeModelName("model-b-2026-03-17"));
		assertEquals("model-a", OpenAiPricingConfig.normalizeModelName("model-a"));
		assertEquals("model-b-fast", OpenAiPricingConfig.normalizeModelName("model-b-fast"));
	}

	@Test
	void getRatesForModelUsesNormalizedName() {
		OpenAiPricingConfig config = pricingConfig();

		assertNotNull(config.getRatesForModel("model-a-2026-03-05"));
		assertEquals(FAST_INPUT_RATE, config.getRatesForModel("model-a-2026-03-05").getInput());
		assertEquals(FAST_OUTPUT_RATE, config.getRatesForModel("model-a-2026-03-05").getOutput());

		assertNotNull(config.getRatesForModel("model-b-fast"));
		assertEquals(GOOD_INPUT_RATE * 2, config.getRatesForModel("model-b-fast").getInput());
		assertEquals(GOOD_OUTPUT_RATE * 2, config.getRatesForModel("model-b-fast").getOutput());
	}

	@Test
	void calculateCostUsdUsesConfiguredRates() {
		OpenAiPricingConfig config = pricingConfig();

		assertEquals(expectedCost(FAST_INPUT_RATE, FAST_OUTPUT_RATE, 130, 60),
				config.calculateCostUsd("model-a", 130, 60), 1e-9);
		assertEquals(expectedCost(GOOD_INPUT_RATE, GOOD_OUTPUT_RATE, 200, 80),
				config.calculateCostUsd("model-b", 200, 80), 1e-9);
		assertEquals(expectedCost(GOOD_INPUT_RATE * 2, GOOD_OUTPUT_RATE * 2, 130, 60),
				config.calculateCostUsd("model-b-fast", 130, 60), 1e-9);
		assertNull(config.calculateCostUsd("unknown-model", 100, 50));
	}

	private static double expectedCost(double inputRate, double outputRate, long inputTokens, long outputTokens) {
		return (inputTokens / 1_000_000.0) * inputRate + (outputTokens / 1_000_000.0) * outputRate;
	}

	private OpenAiPricingConfig pricingConfig() {
		OpenAiPricingConfig config = new OpenAiPricingConfig();
		config.getModels().put("model-a", new OpenAiPricingConfig.ModelRates(FAST_INPUT_RATE, FAST_OUTPUT_RATE));
		config.getModels().put("model-b", new OpenAiPricingConfig.ModelRates(GOOD_INPUT_RATE, GOOD_OUTPUT_RATE));
		config.getModels().put("model-b-fast", new OpenAiPricingConfig.ModelRates(GOOD_INPUT_RATE * 2, GOOD_OUTPUT_RATE * 2));
		return config;
	}
}
