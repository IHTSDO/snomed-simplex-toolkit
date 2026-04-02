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

	Optional<TranslationUnit> findByCodeAndLanguageCode(String code, String languageCode);

	@Query("SELECT COUNT(tu) FROM TranslationUnit tu WHERE tu.languageCode = :lang AND tu.code IN (SELECT ts.code FROM TranslationSource ts WHERE :setCode MEMBER OF ts.sets) AND SIZE(tu.terms) > 0")
	long countTranslatedInSubset(@Param("lang") String languageCode, @Param("setCode") String compositeSetCode);

	@Query("SELECT COUNT(tu) FROM TranslationUnit tu WHERE tu.languageCode = :lang AND tu.code IN (SELECT ts.code FROM TranslationSource ts WHERE :setCode MEMBER OF ts.sets) AND tu.status IN ('NEEDS_EDIT', 'FOR_REVIEW')")
	long countOutstandingReviewInSubset(@Param("lang") String languageCode, @Param("setCode") String compositeSetCode);
}
