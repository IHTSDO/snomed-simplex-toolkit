package org.snomed.simplex.rest.pojos;

import java.util.List;

/**
 * Sample translation row for the dashboard (WeblateUnit-compatible JSON shape).
 */
public class TranslationUnitRow {

	private String id;
	private List<String> source;
	private List<String> target;
	private String context;
	private String explanation;
	/** {@link org.snomed.simplex.snolate.domain.TranslationStatus} name, or null if no unit exists yet. */
	private String status;

	public TranslationUnitRow() {
	}

	public TranslationUnitRow(List<String> source, List<String> target, String context, String explanation) {
		this(source, target, context, explanation, null);
	}

	public TranslationUnitRow(List<String> source, List<String> target, String context, String explanation, String status) {
		this.source = source;
		this.target = target;
		this.context = context;
		this.explanation = explanation;
		this.status = status;
	}

	public List<String> getValue() {
		return getSource();
	}

	public String getKey() {
		return getContext();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void blankLabels() {
		// compatibility with WeblateUnit
	}
}
