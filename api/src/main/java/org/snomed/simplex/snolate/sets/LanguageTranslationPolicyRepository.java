package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.LanguageTranslationPolicy;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface LanguageTranslationPolicyRepository extends ElasticsearchRepository<LanguageTranslationPolicy, String> {

	List<LanguageTranslationPolicy> findByCodesystemOrderByLanguageDialectName(String codesystem);

	Optional<LanguageTranslationPolicy> findByCodesystemAndRefset(String codesystem, String refset);
}
