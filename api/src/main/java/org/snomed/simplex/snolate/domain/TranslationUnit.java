package org.snomed.simplex.snolate.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Document(indexName = "#{@indexNameProvider.indexName('snolate-translation-unit')}")
public class TranslationUnit {

	public static final class Fields {
		private Fields() {}
		public static final String CODE = "code";
		public static final String ORDER = "order";
		public static final String MEMBER_OF = "memberOf";
		public static final String COMPOSITE_LANGUAGE_CODE = "compositeLanguageCode";
		public static final String HAS_TERMS = "hasTerms";
		public static final String STATUS = "status";
	}

	@Id
	private String id;

	@Field(type = FieldType.Keyword)
	private String code;

	@Field(type = FieldType.Keyword)
	private String refsetId;

	@Field(type = FieldType.Keyword)
	private String languageCode;

	@Field(type = FieldType.Keyword)
	private String compositeLanguageCode;

	@Field(type = FieldType.Integer)
	private int order;

	@Field(type = FieldType.Integer)
	private int statusSort;

	@Field(type = FieldType.Keyword)
	private List<String> terms = new ArrayList<>();

	@Field(type = FieldType.Boolean)
	private boolean hasTerms;

	@Field(type = FieldType.Keyword)
	private TranslationStatus status;

	@Field(type = FieldType.Keyword)
	private List<String> memberOf = new ArrayList<>();

	public TranslationUnit() {
	}

	public TranslationUnit(String code, String refsetId, String languageCode, String compositeLanguageCode, int order,
			List<String> terms, TranslationStatus status, Set<String> memberOf) {
		this.code = code;
		this.refsetId = refsetId;
		this.languageCode = languageCode;
		this.compositeLanguageCode = compositeLanguageCode;
		this.order = order;
		setTerms(terms);
		setStatus(status);
		this.memberOf = memberOf != null ? new ArrayList<>(memberOf) : new ArrayList<>();
	}

	/** Legacy-style convenience: builds a unit for merge/import paths; caller sets refset/language/memberOf/order if known */
	public TranslationUnit(String code, String compositeLanguageCode, List<String> terms, TranslationStatus status) {
		this.code = code;
		this.compositeLanguageCode = compositeLanguageCode;
		this.refsetId = "";
		this.languageCode = "";
		this.order = 0;
		setTerms(terms);
		setStatus(status);
		this.memberOf = new ArrayList<>();
	}

	public static TranslationUnit shellMember(String code, String refsetId, String languageCode, String compositeLanguageCode,
			int sourceOrder, String compositeSetCode) {
		TranslationUnit u = new TranslationUnit();
		u.setCode(code);
		u.setRefsetId(refsetId);
		u.setLanguageCode(languageCode);
		u.setCompositeLanguageCode(compositeLanguageCode);
		u.setOrder(sourceOrder);
		u.setTerms(new ArrayList<>());
		u.setStatus(TranslationStatus.NOT_STARTED);
		u.setMemberOf(new LinkedHashSet<>(Set.of(compositeSetCode)));
		return u;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getRefsetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getLanguageCode() {
		return languageCode;
	}

	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	public String getCompositeLanguageCode() {
		return compositeLanguageCode;
	}

	public void setCompositeLanguageCode(String compositeLanguageCode) {
		this.compositeLanguageCode = compositeLanguageCode;
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public int getStatusSort() {
		return statusSort;
	}

	public void setStatusSort(int statusSort) {
		this.statusSort = statusSort;
	}

	public List<String> getTerms() {
		return terms;
	}

	public void setTerms(List<String> terms) {
		this.terms = terms != null ? new ArrayList<>(terms) : new ArrayList<>();
		this.hasTerms = !this.terms.isEmpty();
	}

	public TranslationStatus getStatus() {
		return status;
	}

	public void setStatus(TranslationStatus status) {
		this.status = status;
		this.statusSort = TranslationStatuses.sortOrdinal(status);
	}

	public List<String> getMemberOf() {
		return memberOf;
	}

	public void setMemberOf(Set<String> memberOf) {
		this.memberOf = memberOf != null ? new ArrayList<>(memberOf) : new ArrayList<>();
	}

	public void setMemberOfList(List<String> memberOf) {
		this.memberOf = memberOf != null ? new ArrayList<>(memberOf) : new ArrayList<>();
	}

	public boolean hasTermContent() {
		return hasTerms;
	}

	public boolean isHasTerms() {
		return hasTerms;
	}

	public void setHasTerms(boolean hasTerms) {
		this.hasTerms = hasTerms;
	}
}
