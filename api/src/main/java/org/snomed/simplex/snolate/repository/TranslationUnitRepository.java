package org.snomed.simplex.snolate.repository;

import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.domain.TranslationUnitId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranslationUnitRepository extends JpaRepository<TranslationUnit, TranslationUnitId> {

	List<TranslationUnit> findAllByLanguageCode(String languageCode);

	List<TranslationUnit> findAllByLanguageCodeAndStatus(String languageCode, TranslationStatus status);
}
