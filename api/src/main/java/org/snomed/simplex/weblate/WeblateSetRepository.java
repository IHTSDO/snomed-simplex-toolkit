package org.snomed.simplex.weblate;

import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface WeblateSetRepository extends ElasticsearchRepository<WeblateTranslationSet, String> {

	List<WeblateTranslationSet> findByCodesystemOrderByName(String codesystem);

	List<WeblateTranslationSet> findByCodesystemAndRefsetOrderByName(String codesystem, String refsetId);

	Optional<WeblateTranslationSet> findByCodesystemAndLabelAndRefsetOrderByName(String codesystem, String label, String refset);
}
