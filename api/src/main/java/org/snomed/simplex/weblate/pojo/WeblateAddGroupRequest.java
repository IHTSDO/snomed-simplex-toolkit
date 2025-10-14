package org.snomed.simplex.weblate.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WeblateAddGroupRequest {

	private String name;

	@JsonProperty("language_selection")
	private Integer languageSelection;

	@JsonProperty("project_selection")
	private Integer projectSelection;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getLanguageSelection() {
		return languageSelection;
	}

	public void setLanguageSelection(Integer languageSelection) {
		this.languageSelection = languageSelection;
	}

	public Integer getProjectSelection() {
		return projectSelection;
	}

	public void setProjectSelection(Integer projectSelection) {
		this.projectSelection = projectSelection;
	}
}
