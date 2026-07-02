package org.snomed.simplex.rest.pojos;

import java.util.ArrayList;
import java.util.List;

public class LlmUsageSummary {

	private String period;
	private String codesystem;
	private String model;
	private String startDate;
	private String endDate;
	private long inputTokens;
	private long outputTokens;
	private long totalTokens;
	private long requestCount;
	private long conceptsTranslated;
	private List<LlmUsageByModel> byModel = new ArrayList<>();
	private List<LlmUsageDailyBreakdown> dailyBreakdown = new ArrayList<>();

	public String getPeriod() {
		return period;
	}

	public void setPeriod(String period) {
		this.period = period;
	}

	public String getCodesystem() {
		return codesystem;
	}

	public void setCodesystem(String codesystem) {
		this.codesystem = codesystem;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getStartDate() {
		return startDate;
	}

	public void setStartDate(String startDate) {
		this.startDate = startDate;
	}

	public String getEndDate() {
		return endDate;
	}

	public void setEndDate(String endDate) {
		this.endDate = endDate;
	}

	public long getInputTokens() {
		return inputTokens;
	}

	public void setInputTokens(long inputTokens) {
		this.inputTokens = inputTokens;
	}

	public long getOutputTokens() {
		return outputTokens;
	}

	public void setOutputTokens(long outputTokens) {
		this.outputTokens = outputTokens;
	}

	public long getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(long totalTokens) {
		this.totalTokens = totalTokens;
	}

	public long getRequestCount() {
		return requestCount;
	}

	public void setRequestCount(long requestCount) {
		this.requestCount = requestCount;
	}

	public long getConceptsTranslated() {
		return conceptsTranslated;
	}

	public void setConceptsTranslated(long conceptsTranslated) {
		this.conceptsTranslated = conceptsTranslated;
	}

	public List<LlmUsageByModel> getByModel() {
		return byModel;
	}

	public void setByModel(List<LlmUsageByModel> byModel) {
		this.byModel = byModel;
	}

	public List<LlmUsageDailyBreakdown> getDailyBreakdown() {
		return dailyBreakdown;
	}

	public void setDailyBreakdown(List<LlmUsageDailyBreakdown> dailyBreakdown) {
		this.dailyBreakdown = dailyBreakdown;
	}
}
