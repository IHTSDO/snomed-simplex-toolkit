package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SnolateTranslationUnitRepository extends ElasticsearchRepository<TranslationUnit, String> {

	List<TranslationUnit> findAllByCompositeLanguageCode(String compositeLanguageCode);

	List<TranslationUnit> findAllByCompositeLanguageCodeAndCodeIn(String compositeLanguageCode, Collection<String> codes);

	Optional<TranslationUnit> findByCodeAndCompositeLanguageCode(String code, String compositeLanguageCode);
}
