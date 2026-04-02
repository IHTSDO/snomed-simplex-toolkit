package org.snomed.simplex.snolate.sets;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface SnolateSetRepository extends ElasticsearchRepository<SnolateTranslationSet, String> {

	List<SnolateTranslationSet> findByCodesystemOrderByName(String codesystem);

	List<SnolateTranslationSet> findByCodesystemAndRefsetOrderByName(String codesystem, String refsetId);

	Optional<SnolateTranslationSet> findByCodesystemAndLabelAndRefsetOrderByName(String codesystem, String label, String refset);
}
