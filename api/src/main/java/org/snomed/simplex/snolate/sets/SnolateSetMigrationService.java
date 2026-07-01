package org.snomed.simplex.snolate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.config.IndexNameProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class SnolateSetMigrationService implements ApplicationRunner {

	private static final String REMOVED_FIELD = "aiLanguageAdvice";
	private static final String REMOVE_FIELD_SCRIPT = "ctx._source.remove('" + REMOVED_FIELD + "')";

	private final ElasticsearchOperations elasticsearchOperations;
	private final IndexNameProvider indexNameProvider;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnolateSetMigrationService(ElasticsearchOperations elasticsearchOperations, IndexNameProvider indexNameProvider) {
		this.elasticsearchOperations = elasticsearchOperations;
		this.indexNameProvider = indexNameProvider;
	}

	@Override
	public void run(ApplicationArguments args) {
		removeAiLanguageAdviceField();
	}

	public void removeAiLanguageAdviceField() {
		IndexCoordinates index = IndexCoordinates.of(indexNameProvider.indexName("snolate-set"));
		CriteriaQuery query = new CriteriaQuery(new Criteria(REMOVED_FIELD).exists());
		UpdateQuery updateQuery = UpdateQuery.builder(query)
				.withScriptType(ScriptType.INLINE)
				.withLang("painless")
				.withScript(REMOVE_FIELD_SCRIPT)
				.build();

		ByQueryResponse response = elasticsearchOperations.updateByQuery(updateQuery, index);
		logger.info("Snolate set migration: removed {} from {} document(s) in index {}",
				REMOVED_FIELD, response.getUpdated(), index.getIndexName());
	}
}
