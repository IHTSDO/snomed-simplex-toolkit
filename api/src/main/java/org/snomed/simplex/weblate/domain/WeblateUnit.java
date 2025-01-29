package org.snomed.simplex.weblate.domain;

import java.util.List;

public class WeblateUnit {

	private String id;
	private List<String> source;
	private List<String> target;
	private String context;
	private String note;
	private String explanation;

	public WeblateUnit() {
	}

	public WeblateUnit(List<String> source, List<String> target, String context, String note, String explanation) {
		this.source = source;
		this.target = target;
		this.context = context;
		this.note = note;
		this.explanation = explanation;
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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
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
