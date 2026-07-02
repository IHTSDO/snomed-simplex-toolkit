package org.snomed.simplex.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiPricingConfigTest {

	@Test
	void normalizeModelNameStripsDateSuffix() {
		assertEquals("gpt-5.4", OpenAiPricingConfig.normalizeModelName("gpt-5.4-2026-03-05"));
		assertEquals("gpt-5.4-mini", OpenAiPricingConfig.normalizeModelName("gpt-5.4-mini-2026-03-17"));
		assertEquals("gpt-5.4", OpenAiPricingConfig.normalizeModelName("gpt-5.4"));
	}

	@Test
	void getRatesForModelUsesNormalizedName() {
		OpenAiPricingConfig config = pricingConfig();

		assertNotNull(config.getRatesForModel("gpt-5.4-2026-03-05"));
		assertEquals(2.50, config.getRatesForModel("gpt-5.4-2026-03-05").getInput());
		assertEquals(15.00, config.getRatesForModel("gpt-5.4-2026-03-05").getOutput());

		assertNotNull(config.getRatesForModel("gpt-5.4-mini-2026-03-17"));
		assertEquals(0.75, config.getRatesForModel("gpt-5.4-mini-2026-03-17").getInput());
		assertEquals(4.50, config.getRatesForModel("gpt-5.4-mini-2026-03-17").getOutput());
	}

	@Test
	void calculateCostUsdUsesConfiguredRates() {
		OpenAiPricingConfig config = pricingConfig();

		assertEquals(0.0003675, config.calculateCostUsd("gpt-5.4-mini", 130, 60), 1e-9);
		assertEquals(0.0017, config.calculateCostUsd("gpt-5.4", 200, 80), 1e-9);
		assertNull(config.calculateCostUsd("unknown-model", 100, 50));
	}

	private OpenAiPricingConfig pricingConfig() {
		OpenAiPricingConfig config = new OpenAiPricingConfig();
		config.getModels().put("gpt-5.4", new OpenAiPricingConfig.ModelRates(2.50, 15.00));
		config.getModels().put("gpt-5.4-mini", new OpenAiPricingConfig.ModelRates(0.75, 4.50));
		return config;
	}
}
