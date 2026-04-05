package org.snomed.simplex.snolate.repository;

import org.snomed.simplex.snolate.domain.TranslationSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TranslationSourceRepository extends JpaRepository<TranslationSource, String> {

	List<TranslationSource> findAllByOrderByOrderAsc();

	List<TranslationSource> findAllByCodeIn(Collection<String> codes);

	@Query("SELECT DISTINCT t FROM TranslationSource t LEFT JOIN FETCH t.sets WHERE t.code IN :codes")
	List<TranslationSource> findAllByCodeInFetchingSets(@Param("codes") Collection<String> codes);

	@Query("SELECT t FROM TranslationSource t WHERE :setCode MEMBER OF t.sets")
	List<TranslationSource> findAllHavingSetMembership(@Param("setCode") String setCode);

	@Query("SELECT t FROM TranslationSource t WHERE :setCode MEMBER OF t.sets")
	Page<TranslationSource> findPageHavingSetMembership(@Param("setCode") String setCode, Pageable pageable);

	@Query("SELECT t FROM TranslationSource t WHERE t.code = :code AND :setCode MEMBER OF t.sets")
	Optional<TranslationSource> findByCodeHavingSetMembership(@Param("code") String code, @Param("setCode") String setCode);

	/**
	 * Members of a Snolate set, ordered by {@link org.snomed.simplex.snolate.domain.TranslationUnit} status:
	 * NEEDS_EDIT, FOR_REVIEW, APPROVED, then rows with no unit (not started).
	 */
	@Query(value = """
			SELECT s.code AS code, s.term AS term
			FROM translation_source s
			INNER JOIN translation_source_set tss ON tss.translation_source_code = s.code AND tss.set_code = :setCode
			LEFT JOIN translation_unit u ON u.code = s.code AND u.language_code = :lang
			ORDER BY
				CASE u.status
					WHEN 'NEEDS_EDIT' THEN 0
					WHEN 'FOR_REVIEW' THEN 1
					WHEN 'APPROVED' THEN 2
					ELSE 3
				END,
				s.code
			""",
			countQuery = """
					SELECT count(*)
					FROM translation_source s
					INNER JOIN translation_source_set tss ON tss.translation_source_code = s.code AND tss.set_code = :setCode
					""",
			nativeQuery = true)
	Page<TranslationSetMemberSummary> findRowsForSet(@Param("setCode") String setCode, @Param("lang") String lang, Pageable pageable);
}
