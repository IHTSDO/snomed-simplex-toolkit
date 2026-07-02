package org.snomed.simplex.rest.pojos;

public class LlmUsageByModel {

	private String model;
	private String provider;
	private long inputTokens;
	private long outputTokens;
	private long totalTokens;
	private long requestCount;

	public LlmUsageByModel() {
	}

	public LlmUsageByModel(String model, String provider, long inputTokens, long outputTokens, long requestCount) {
		this.model = model;
		this.provider = provider;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.totalTokens = inputTokens + outputTokens;
		this.requestCount = requestCount;
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
}
