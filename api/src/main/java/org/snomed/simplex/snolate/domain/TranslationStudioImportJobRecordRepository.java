package org.snomed.simplex.snolate.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Optional;

public interface TranslationStudioImportJobRecordRepository extends ElasticsearchRepository<TranslationStudioImportJobRecord, String> {

	Page<TranslationStudioImportJobRecord> findByCodesystemOrderByCreatedDesc(String codesystem, Pageable pageable);

	Page<TranslationStudioImportJobRecord> findByCodesystemAndRefsetIdOrderByCreatedDesc(String codesystem, String refsetId, Pageable pageable);

	Optional<TranslationStudioImportJobRecord> findByCodesystemAndId(String codesystem, String id);
}
