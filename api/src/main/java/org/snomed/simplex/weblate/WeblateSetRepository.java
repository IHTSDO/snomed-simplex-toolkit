package org.snomed.simplex.weblate;

import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface WeblateSetRepository extends ElasticsearchRepository<WeblateTranslationSet, String> {

	List<WeblateTranslationSet> findByCodesystem(String codesystem);

	List<WeblateTranslationSet> findByCodesystemAndRefset(String codesystem, String refsetId);

	Optional<WeblateTranslationSet> findByCodesystemAndLabelAndRefset(String codesystem, String label, String refset);
}
