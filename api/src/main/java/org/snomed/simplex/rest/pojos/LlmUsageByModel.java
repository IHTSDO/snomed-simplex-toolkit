package org.snomed.simplex.rest.pojos;

public class LlmUsageByModel {

	private String model;
	private String provider;
	private long inputTokens;
	private long outputTokens;
	private long totalTokens;
	private long requestCount;
	private long conceptsTranslated;
	private Double costUsd;

	public LlmUsageByModel() {
	}

	public LlmUsageByModel(String model, String provider, long inputTokens, long outputTokens, long requestCount) {
		this(model, provider, inputTokens, outputTokens, requestCount, 0, null);
	}

	public LlmUsageByModel(String model, String provider, long inputTokens, long outputTokens, long requestCount, Double costUsd) {
		this(model, provider, inputTokens, outputTokens, requestCount, 0, costUsd);
	}

	public LlmUsageByModel(String model, String provider, long inputTokens, long outputTokens, long requestCount,
			long conceptsTranslated, Double costUsd) {
		this.model = model;
		this.provider = provider;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.totalTokens = inputTokens + outputTokens;
		this.requestCount = requestCount;
		this.conceptsTranslated = conceptsTranslated;
		this.costUsd = costUsd;
	}

	public String getModel() {
		return model;
	}

	public String getProvider() {
		return provider;
	}

	public long getInputTokens() {
		return inputTokens;
	}

	public long getOutputTokens() {
		return outputTokens;
	}

	public long getTotalTokens() {
		return totalTokens;
	}

	public long getRequestCount() {
		return requestCount;
	}

	public long getConceptsTranslated() {
		return conceptsTranslated;
	}

	public Double getCostUsd() {
		return costUsd;
	}
}
