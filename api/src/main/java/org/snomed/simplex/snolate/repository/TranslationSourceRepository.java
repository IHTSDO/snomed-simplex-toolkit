package org.snomed.simplex.snolate.repository;

import org.snomed.simplex.snolate.domain.TranslationSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranslationSourceRepository extends JpaRepository<TranslationSource, String> {

	List<TranslationSource> findAllByOrderByOrderAsc();
}
