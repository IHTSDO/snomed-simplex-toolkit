package org.snomed.simplex.weblate.domain;

import java.util.List;

public class WeblateUnit {

	private String id;
	private List<String> source;
	private List<String> target;
	private String context;
	private String explanation;

	public WeblateUnit() {
	}

	public WeblateUnit(List<String> source, List<String> target, String context, String explanation) {
		this.source = source;
		this.target = target;
		this.context = context;
		this.explanation = explanation;
	}

	// Weblate seems to require this duplicate of source
	public List<String> getValue() {
		return getSource();
	}

	public String getKey() {
		return getContext();
	}

	public String getId() {
		return id;
	}

	public List<String> getSource() {
		return source;
	}

	public void setSource(List<String> source) {
		this.source = source;
	}

	public List<String> getTarget() {
		return target;
	}

	public void setTarget(List<String> target) {
		this.target = target;
	}

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}

	public String getExplanation() {
		return explanation;
	}

	public void setExplanation(String explanation) {
		this.explanation = explanation;
	}

	@Override
	public String toString() {
		return "WeblateUnit{" +
				"context='" + context + '\'' +
				'}';
	}
}
