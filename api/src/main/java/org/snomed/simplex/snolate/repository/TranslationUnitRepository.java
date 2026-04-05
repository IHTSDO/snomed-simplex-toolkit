package org.snomed.simplex.snolate.repository;

import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.domain.TranslationUnitId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TranslationUnitRepository extends JpaRepository<TranslationUnit, TranslationUnitId> {

	List<TranslationUnit> findAllByLanguageCode(String languageCode);

	List<TranslationUnit> findAllByLanguageCodeAndStatus(String languageCode, TranslationStatus status);

	List<TranslationUnit> findAllByLanguageCodeAndCodeIn(String languageCode, Collection<String> codes);

	@Query("SELECT DISTINCT u FROM TranslationUnit u LEFT JOIN FETCH u.terms WHERE u.languageCode = :lang AND u.code IN :codes")
	List<TranslationUnit> findAllByLanguageCodeAndCodeInWithTermsFetched(@Param("lang") String languageCode,
			@Param("codes") Collection<String> codes);

	Optional<TranslationUnit> findByCodeAndLanguageCode(String code, String languageCode);

	/**
	 * Native SQL: explicit join on {@code translation_source_set} and EXISTS on terms avoids
	 * Hibernate {@code MEMBER OF} / {@code SIZE(terms)} plans that do not scale to 100k+ members.
	 */
	@Query(value = """
			SELECT COUNT(DISTINCT tu.code)
			FROM translation_unit tu
			JOIN translation_source_set tss ON tss.translation_source_code = tu.code AND tss.set_code = :setCode
			WHERE tu.language_code = :lang
			AND EXISTS (
				SELECT 1 FROM translation_unit_term tut
				WHERE tut.translation_unit_code = tu.code AND tut.language_code = tu.language_code)
			""", nativeQuery = true)
	long countTranslatedInSubset(@Param("lang") String languageCode, @Param("setCode") String compositeSetCode);

	@Query(value = """
			SELECT COUNT(DISTINCT tu.code)
			FROM translation_unit tu
			JOIN translation_source_set tss ON tss.translation_source_code = tu.code AND tss.set_code = :setCode
			WHERE tu.language_code = :lang
			AND tu.status IN ('NEEDS_EDIT', 'FOR_REVIEW')
			""", nativeQuery = true)
	long countOutstandingReviewInSubset(@Param("lang") String languageCode, @Param("setCode") String compositeSetCode);

	@Query(value = """
			SELECT tss.set_code, COUNT(DISTINCT tu.code)
			FROM translation_unit tu
			JOIN translation_source_set tss ON tss.translation_source_code = tu.code
			WHERE tu.language_code = :lang
			AND tss.set_code IN (:setCodes)
			AND EXISTS (
				SELECT 1 FROM translation_unit_term tut
				WHERE tut.translation_unit_code = tu.code AND tut.language_code = tu.language_code)
			GROUP BY tss.set_code
			""", nativeQuery = true)
	List<Object[]> countTranslatedInSubsetBatch(@Param("lang") String languageCode,
			@Param("setCodes") Collection<String> compositeSetCodes);

	@Query(value = """
			SELECT tss.set_code, COUNT(DISTINCT tu.code)
			FROM translation_unit tu
			JOIN translation_source_set tss ON tss.translation_source_code = tu.code
			WHERE tu.language_code = :lang
			AND tss.set_code IN (:setCodes)
			AND tu.status IN ('NEEDS_EDIT', 'FOR_REVIEW')
			GROUP BY tss.set_code
			""", nativeQuery = true)
	List<Object[]> countOutstandingReviewInSubsetBatch(@Param("lang") String languageCode,
			@Param("setCodes") Collection<String> compositeSetCodes);
}
