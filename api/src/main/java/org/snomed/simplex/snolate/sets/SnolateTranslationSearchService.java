package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

@Service
public class SnolateTranslationSearchService {

	/**
	 * Stable ordering for {@code search_after} scans: {@code code} plus document {@code id} tie-breaker.
	 */
	private static final Sort UNITS_IN_SET_STREAM_SORT = Sort.by(
			Sort.Order.asc(TranslationUnit.Fields.ORDER),
			Sort.Order.asc(TranslationUnit.Fields.CODE));

	private static final int STREAM_PAGE_SIZE = 5_000;

	private final ElasticsearchOperations elasticsearchOperations;

	public SnolateTranslationSearchService(ElasticsearchOperations elasticsearchOperations) {
		this.elasticsearchOperations = elasticsearchOperations;
	}

	private static Criteria unitsInSetCriteria(String compositeSetCode, String compositeLanguageCode) {
		return new Criteria(TranslationUnit.Fields.MEMBER_OF).is(compositeSetCode)
				.and(new Criteria(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).is(compositeLanguageCode));
	}

	public Page<TranslationUnit> pageUnitsInSet(String compositeSetCode, String compositeLanguageCode, Pageable pageable) {
		Criteria c = unitsInSetCriteria(compositeSetCode, compositeLanguageCode);
		CriteriaQuery query = new CriteriaQuery(c);
		query.setPageable(pageable);
		// Elasticsearch defaults to a 10,000 hit cap on total unless track_total_hits is enabled.
		query.setTrackTotalHits(true);
		SearchHits<TranslationUnit> searchHits = elasticsearchOperations.search(query, TranslationUnit.class);
		List<TranslationUnit> content = searchHits.getSearchHits().stream().map(SearchHit::getContent).toList();
		return new PageImpl<>(content, pageable, searchHits.getTotalHits());
	}

	public Map<String, Long> countTranslatedInSubsetBatch(String compositeLanguageCode, Collection<String> compositeSetCodes) {
		Map<String, Long> map = new HashMap<>();
		for (String setCode : compositeSetCodes) {
			Criteria c = new Criteria(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).is(compositeLanguageCode)
					.and(new Criteria(TranslationUnit.Fields.MEMBER_OF).is(setCode))
					.and(new Criteria(TranslationUnit.Fields.HAS_TERMS).is(true));
			long total = elasticsearchOperations.count(new CriteriaQuery(c), TranslationUnit.class);
			map.put(setCode, total);
		}
		return map;
	}

	/**
	 * Visits every translation unit in the set using {@code search_after} paging (no {@code from} offsets beyond ES's result window).
	 */
	public void forEachUnitInSet(String compositeSetCode, String compositeLanguageCode, Consumer<TranslationUnit> consumer) {
		List<Object> searchAfter = null;
		while (true) {
			CriteriaQuery query = new CriteriaQuery(unitsInSetCriteria(compositeSetCode, compositeLanguageCode));
			query.setPageable(PageRequest.of(0, STREAM_PAGE_SIZE, UNITS_IN_SET_STREAM_SORT));
			query.setTrackTotalHits(false);
			if (searchAfter != null) {
				query.setSearchAfter(searchAfter);
			}
			SearchHits<TranslationUnit> searchHits = elasticsearchOperations.search(query, TranslationUnit.class);
			List<SearchHit<TranslationUnit>> hits = searchHits.getSearchHits();
			if (hits.isEmpty()) {
				break;
			}
			for (SearchHit<TranslationUnit> hit : hits) {
				consumer.accept(hit.getContent());
			}
			if (hits.size() < STREAM_PAGE_SIZE) {
				break;
			}
			searchAfter = hits.get(hits.size() - 1).getSortValues();
			if (searchAfter == null || searchAfter.isEmpty()) {
				throw new IllegalStateException(
						"Elasticsearch returned no sort values for search_after; cannot continue streaming units in set.");
			}
		}
	}

	public List<TranslationUnit> listAllUnitsInSet(String compositeSetCode, String compositeLanguageCode) {
		List<TranslationUnit> all = new ArrayList<>();
		forEachUnitInSet(compositeSetCode, compositeLanguageCode, all::add);
		return all;
	}

	public Map<String, Long> countOutstandingReviewInSubsetBatch(String compositeLanguageCode, Collection<String> compositeSetCodes) {
		Map<String, Long> map = new HashMap<>();
		List<Object> outstanding = List.of(TranslationStatus.NEEDS_EDIT.name(), TranslationStatus.FOR_REVIEW.name());
		for (String setCode : compositeSetCodes) {
			Criteria c = new Criteria(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).is(compositeLanguageCode)
					.and(new Criteria(TranslationUnit.Fields.MEMBER_OF).is(setCode))
					.and(new Criteria(TranslationUnit.Fields.STATUS).in(outstanding));
			long total = elasticsearchOperations.count(new CriteriaQuery(c), TranslationUnit.class);
			map.put(setCode, total);
		}
		return map;
	}
}
