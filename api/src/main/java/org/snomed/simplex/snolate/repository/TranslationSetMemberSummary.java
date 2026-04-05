package org.snomed.simplex.snolate.repository;

/**
 * One row of a translation set member listing (concept id + English term from {@link org.snomed.simplex.snolate.domain.TranslationSource}).
 */
public interface TranslationSetMemberSummary {

	String getCode();

	String getTerm();
}
