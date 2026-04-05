package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationSource;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface SnolateTranslationSourceRepository extends ElasticsearchRepository<TranslationSource, String> {

	List<TranslationSource> findAllByOrderByOrderAsc();
}
