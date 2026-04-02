package org.snomed.simplex.snolate.repository;

import org.snomed.simplex.snolate.domain.TranslationSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TranslationSourceRepository extends JpaRepository<TranslationSource, String> {

	List<TranslationSource> findAllByOrderByOrderAsc();

	List<TranslationSource> findAllByCodeIn(Collection<String> codes);

	@Query("SELECT DISTINCT t FROM TranslationSource t LEFT JOIN FETCH t.sets WHERE t.code IN :codes")
	List<TranslationSource> findAllByCodeInFetchingSets(@Param("codes") Collection<String> codes);

	@Query("SELECT t FROM TranslationSource t WHERE :setCode MEMBER OF t.sets")
	List<TranslationSource> findAllHavingSetMembership(@Param("setCode") String setCode);
}
