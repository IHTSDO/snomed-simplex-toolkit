package org.snomed.simplex.rest.pojos;

public class LlmUsageDailyBreakdown {

	private String date;
	private String codesystem;
	private String model;
	private String provider;
	private long inputTokens;
	private long outputTokens;
	private long totalTokens;
	private long requestCount;
	private long conceptsTranslated;

	public LlmUsageDailyBreakdown() {
	}

	public LlmUsageDailyBreakdown(String date, String codesystem, String model, String provider,
			long inputTokens, long outputTokens, long requestCount) {
		this(date, codesystem, model, provider, inputTokens, outputTokens, requestCount, 0);
	}

	public LlmUsageDailyBreakdown(String date, String codesystem, String model, String provider,
			long inputTokens, long outputTokens, long requestCount, long conceptsTranslated) {
		this.date = date;
		this.codesystem = codesystem;
		this.model = model;
		this.provider = provider;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.totalTokens = inputTokens + outputTokens;
		this.requestCount = requestCount;
		this.conceptsTranslated = conceptsTranslated;
	}

	public String getDate() {
		return date;
	}

	public String getCodesystem() {
		return codesystem;
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
}
