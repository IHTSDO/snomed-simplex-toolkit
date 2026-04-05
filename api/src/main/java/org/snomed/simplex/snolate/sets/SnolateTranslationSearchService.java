package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SnolateTranslationSearchService {

	private final ElasticsearchOperations elasticsearchOperations;

	public SnolateTranslationSearchService(ElasticsearchOperations elasticsearchOperations) {
		this.elasticsearchOperations = elasticsearchOperations;
	}

	public Page<TranslationUnit> pageUnitsInSet(String compositeSetCode, String compositeLanguageCode, Pageable pageable) {
		Criteria c = new Criteria("memberOf").is(compositeSetCode).and(new Criteria("compositeLanguageCode").is(compositeLanguageCode));
		CriteriaQuery query = new CriteriaQuery(c);
		query.setPageable(pageable);
		SearchHits<TranslationUnit> searchHits = elasticsearchOperations.search(query, TranslationUnit.class);
		List<TranslationUnit> content = searchHits.getSearchHits().stream().map(SearchHit::getContent).toList();
		return new PageImpl<>(content, pageable, searchHits.getTotalHits());
	}

	public Map<String, Long> countTranslatedInSubsetBatch(String compositeLanguageCode, Collection<String> compositeSetCodes) {
		Map<String, Long> map = new HashMap<>();
		for (String setCode : compositeSetCodes) {
			Criteria c = new Criteria("compositeLanguageCode").is(compositeLanguageCode)
					.and(new Criteria("memberOf").is(setCode))
					.and(new Criteria("hasTerms").is(true));
			long total = elasticsearchOperations.count(new CriteriaQuery(c), TranslationUnit.class);
			map.put(setCode, total);
		}
		return map;
	}

	public List<TranslationUnit> listAllUnitsInSet(String compositeSetCode, String compositeLanguageCode) {
		List<TranslationUnit> all = new ArrayList<>();
		int pageNumber = 0;
		final int pageSize = 5_000;
		Page<TranslationUnit> chunk;
		do {
			chunk = pageUnitsInSet(compositeSetCode, compositeLanguageCode,
					org.springframework.data.domain.PageRequest.of(pageNumber++, pageSize, org.springframework.data.domain.Sort.by("code")));
			all.addAll(chunk.getContent());
		} while (chunk.hasNext());
		return all;
	}

	public Map<String, Long> countOutstandingReviewInSubsetBatch(String compositeLanguageCode, Collection<String> compositeSetCodes) {
		Map<String, Long> map = new HashMap<>();
		List<Object> outstanding = List.of(TranslationStatus.NEEDS_EDIT.name(), TranslationStatus.FOR_REVIEW.name());
		for (String setCode : compositeSetCodes) {
			Criteria c = new Criteria("compositeLanguageCode").is(compositeLanguageCode)
					.and(new Criteria("memberOf").is(setCode))
					.and(new Criteria("status").in(outstanding));
			long total = elasticsearchOperations.count(new CriteriaQuery(c), TranslationUnit.class);
			map.put(setCode, total);
		}
		return map;
	}
}
