package org.snomed.simplex.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class OpenAiPricingConfig {

	private static final Pattern DATE_SUFFIX = Pattern.compile("-\\d{4}-\\d{2}-\\d{2}$");

	private final Map<String, ModelRates> models = new LinkedHashMap<>();

	public Map<String, ModelRates> getModels() {
		return models;
	}

	public ModelRates getRatesForModel(String modelName) {
		if (modelName == null || modelName.isBlank()) {
			return null;
		}
		return models.get(normalizeModelName(modelName));
	}

	public Double calculateCostUsd(String modelName, long inputTokens, long outputTokens) {
		ModelRates rates = getRatesForModel(modelName);
		if (rates == null) {
			return null;
		}
		return (inputTokens / 1_000_000.0) * rates.getInput() + (outputTokens / 1_000_000.0) * rates.getOutput();
	}

	static String normalizeModelName(String modelName) {
		return DATE_SUFFIX.matcher(modelName).replaceFirst("");
	}

	public static class ModelRates {

		private double input;
		private double output;

		public ModelRates() {
		}

		public ModelRates(double input, double output) {
			this.input = input;
			this.output = output;
		}

		public double getInput() {
			return input;
		}

		public void setInput(double input) {
			this.input = input;
		}

		public double getOutput() {
			return output;
		}

		public void setOutput(double output) {
			this.output = output;
		}
	}
}
