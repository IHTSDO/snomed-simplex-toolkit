package org.snomed.simplex.ai;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "#{@indexNameProvider.indexName('llm-usage-daily')}")
public class LlmUsageDaily {

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String codesystem;

	@Field(type = FieldType.Keyword)
	private String model;

	@Field(type = FieldType.Keyword)
	private String provider;

	@Field(type = FieldType.Keyword)
	private String date;

	@Field(type = FieldType.Long)
	private long inputTokens;

	@Field(type = FieldType.Long)
	private long outputTokens;

	@Field(type = FieldType.Long)
	private long requestCount;

	public LlmUsageDaily() {
	}

	public LlmUsageDaily(String id, String codesystem, String model, String provider, String date,
			long inputTokens, long outputTokens, long requestCount) {
		this.id = id;
		this.codesystem = codesystem;
		this.model = model;
		this.provider = provider;
		this.date = date;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.requestCount = requestCount;
	}

	public String getId() {
		return id;
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

	public String getDate() {
		return date;
	}

	public long getInputTokens() {
		return inputTokens;
	}

	public long getOutputTokens() {
		return outputTokens;
	}

	public long getRequestCount() {
		return requestCount;
	}

	public long getTotalTokens() {
		return inputTokens + outputTokens;
	}
}
