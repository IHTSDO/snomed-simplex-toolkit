package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Description extends Component {

	private String descriptionId;
	private String term;
	private String conceptId;
	private Type type;
	private String lang;
	private CaseSignificance caseSignificance;
	private Map<String, Acceptability> acceptabilityMap;
	private String inactivationIndicator;
	private Map<String, Set<String>> associationTargets;

	public enum Type {

		FSN("900000000000003001"), SYNONYM("900000000000013009"), TEXT_DEFINITION("900000000000550004");

		private final String conceptId;

		Type(String conceptId) {
			this.conceptId = conceptId;
		}

		public static Type fromConceptId(String conceptId) {
			for (Type value : values()) {
				if (value.conceptId.equals(conceptId)) {
					return value;
				}
			}
			return null;
		}

	}
	public enum Acceptability {

		PREFERRED("900000000000548007"), ACCEPTABLE("900000000000549004");

		private final String conceptId;

		Acceptability(String conceptId) {
			this.conceptId = conceptId;
		}

		public static Acceptability fromConceptId(String conceptId) {
			for (Acceptability value : values()) {
				if (value.conceptId.equals(conceptId)) {
					return value;
				}
			}
			return null;
		}

	}
	public enum CaseSignificance {

		ENTIRE_TERM_CASE_SENSITIVE("900000000000017005"), CASE_INSENSITIVE("900000000000448009"), INITIAL_CHARACTER_CASE_INSENSITIVE("900000000000020002");

		private final String conceptId;

		CaseSignificance(String conceptId) {
			this.conceptId = conceptId;
		}

		public static CaseSignificance fromConceptId(String conceptId) {
			for (CaseSignificance value : values()) {
				if (value.conceptId.equals(conceptId)) {
					return value;
				}
			}
			return null;
		}

		public String getConceptId() {
			return conceptId;
		}
	}
	public Description() {
	}

	public Description(Type type, String lang, String term, CaseSignificance caseSignificance) {
		this(type, lang, term, caseSignificance, new HashMap<>());
	}

	public Description(Type type, String lang, String term, CaseSignificance caseSignificance, String langRefset, Acceptability acceptability) {
		this(type, lang, term, caseSignificance, Map.of(langRefset, acceptability));
	}

	public Description(Type type, String lang, String term, CaseSignificance caseSignificance, Map<String, Acceptability> acceptabilityMap) {
		this.type = type;
		this.lang = lang;
		this.term = term;
		this.caseSignificance = caseSignificance;
		this.acceptabilityMap = acceptabilityMap;
	}

	@Override
	@JsonIgnore
	public String getId() {
		return descriptionId;
	}

	public Description addAcceptability(String langRefsetId, Acceptability acceptability) {
		acceptabilityMap.put(langRefsetId, acceptability);
		return this;
	}

	public String getDescriptionId() {
		return descriptionId;
	}

	public void setDescriptionId(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public Description setTerm(String term) {
		this.term = term;
		return this;
	}

	public CaseSignificance getCaseSignificance() {
		return caseSignificance;
	}

	public void setCaseSignificance(CaseSignificance caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public Map<String, Acceptability> getAcceptabilityMap() {
		return acceptabilityMap;
	}

	public void setAcceptabilityMap(Map<String, Acceptability> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
	}

	public String getInactivationIndicator() {
		return inactivationIndicator;
	}

	public Map<String, Set<String>> getAssociationTargets() {
		return associationTargets;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Description that = (Description) o;
		return Objects.equals(term, that.term) && Objects.equals(conceptId, that.conceptId) && Objects.equals(lang, that.lang);
	}

	@Override
	public int hashCode() {
		return Objects.hash(term, conceptId, lang);
	}

	@Override
	public String toString() {
		return "Description{" +
				"lang='" + lang + '\'' +
				", term='" + term + '\'' +
				", caseSignificance='" + caseSignificance + '\'' +
				", acceptabilityMap=" + acceptabilityMap +
				'}';
	}
}
