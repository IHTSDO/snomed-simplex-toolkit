package org.snomed.simplex.snolate.repository;

import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranslationUnitRepository extends JpaRepository<TranslationUnit, String> {

	List<TranslationUnit> findAllByStatus(TranslationStatus status);
}
