package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationSource;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SnolateTranslationSourceRepository extends ElasticsearchRepository<TranslationSource, String> {
}
