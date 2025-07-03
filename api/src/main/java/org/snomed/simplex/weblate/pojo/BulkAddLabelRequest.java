package org.snomed.simplex.weblate.pojo;

import com.fasterxml.jackson.annotation.JsonGetter;

import java.util.List;

public class BulkAddLabelRequest {

	private final String projectSlug;
	private final Integer labelId;
	private final List<String> contextIds;

	public BulkAddLabelRequest(String projectSlug, Integer labelId, List<String> contextIds) {
		this.projectSlug = projectSlug;
		this.labelId = labelId;
		this.contextIds = contextIds;
	}

	@JsonGetter("project_slug")
	public String getProjectSlug() {
		return projectSlug;
	}

	@JsonGetter("label_id")
	public Integer getLabelId() {
		return labelId;
	}

	@JsonGetter("context_ids")
	public List<String> getContextIds() {
		return contextIds;
	}

}
