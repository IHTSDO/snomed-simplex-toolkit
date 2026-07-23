package org.snomed.simplex.snolate.sets;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.http.HttpStatus;
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

	private static final Sort TRANSLATION_SOURCE_ORDER_STREAM_SORT = Sort.by(
			Sort.Order.asc(TranslationSource.Fields.ORDER),
			Sort.Order.asc("_doc"));

	private static final Sort DEFAULT_STREAM_SORT = Sort.by(
			Sort.Order.asc("_doc"));

	private static final int STREAM_PAGE_SIZE = 5_000;


	/** Elasticsearch default {@code index.max_result_window}; offset pagination must stay within this. */
	private static final int ELASTICSEARCH_MAX_RESULT_WINDOW = 10_000;

	private static final int SOURCE_TERM_SEARCH_PAGE_SIZE = 5_000;

	/**
	 * Cap English source matches before building an Elasticsearch {@code terms} query on concept ids
	 * (default index limit is 65,536).
	 */
	public static final int ENGLISH_SOURCE_SEARCH_MAX_RESULTS = 50_000;

	private static final String TRANSLATION_SOURCE_TERM_FIELD = "term";

	private static final String TRANSLATION_UNIT_TERMS_FIELD = "terms";

	private final ElasticsearchOperations elasticsearchOperations;

	public SnolateTranslationSearchService(ElasticsearchOperations elasticsearchOperations) {
		this.elasticsearchOperations = elasticsearchOperations;
	}

	private static Criteria unitsInSetCriteria(String compositeSetCode, String compositeLanguageCode) {
		return new Criteria(TranslationUnit.Fields.MEMBER_OF).is(compositeSetCode)
				.and(new Criteria(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).is(compositeLanguageCode));
	}

	public Page<TranslationUnit> pageUnitsInSet(String compositeSetCode, String compositeLanguageCode, Pageable pageable)
			throws ServiceExceptionWithStatusCode {
		return pageUnitsInSet(compositeSetCode, compositeLanguageCode, pageable, null, null, null);
	}

	public Page<TranslationUnit> pageUnitsInSet(String compositeSetCode, String compositeLanguageCode, Pageable pageable,
			TranslationStatus statusFilter) throws ServiceExceptionWithStatusCode {
		return pageUnitsInSet(compositeSetCode, compositeLanguageCode, pageable, statusFilter, null, null);
	}

	public Page<TranslationUnit> pageUnitsInSet(String compositeSetCode, String compositeLanguageCode, Pageable pageable,
			TranslationStatus statusFilter, Collection<String> englishConceptCodes, String targetTerm)
			throws ServiceExceptionWithStatusCode {
		if (englishConceptCodes != null && englishConceptCodes.isEmpty()) {
			return Page.empty(pageable);
		}
		if (englishConceptCodes != null && englishConceptCodes.size() > ENGLISH_SOURCE_SEARCH_MAX_RESULTS) {
			throw englishSearchTooBroadException();
		}
		String trimmedTarget = normalizeOptionalSearchTerm(targetTerm);
		if (englishConceptCodes == null && trimmedTarget == null) {
			return pageUnitsInSetWithCriteria(compositeSetCode, compositeLanguageCode, pageable, statusFilter);
		}
		Query query = buildUnitsInSetQuery(compositeSetCode, compositeLanguageCode, statusFilter, englishConceptCodes, trimmedTarget);
		return paginateSearch(pageable, (p, searchAfter, trackTotal) -> {
			NativeQueryBuilder builder = NativeQuery.builder()
					.withQuery(query)
					.withPageable(p)
					.withTrackTotalHits(trackTotal);
			if (searchAfter != null) {
				builder.withSearchAfter(searchAfter);
			}
			return elasticsearchOperations.search(builder.build(), TranslationUnit.class);
		});
	}

	/**
	 * Concept ids whose English {@link TranslationSource#getTerm()} contains {@code term} (case-insensitive substring).
	 * Returns an empty list when nothing matches.
	 *
	 * @throws ServiceExceptionWithStatusCode when matches exceed {@link #ENGLISH_SOURCE_SEARCH_MAX_RESULTS}
	 */
	public List<String> findSourceCodesByTermSubstring(String term) throws ServiceExceptionWithStatusCode {
		String trimmed = normalizeOptionalSearchTerm(term);
		if (trimmed == null) {
			return List.of();
		}
		Query query = caseInsensitiveSubstringWildcardQuery(TRANSLATION_SOURCE_TERM_FIELD, trimmed);
		List<String> codes = new ArrayList<>();
		List<Object> searchAfter = null;
		boolean hasMore = true;
		while (hasMore) {
			SearchHits<TranslationSource> searchHits = searchTranslationSources(query, searchAfter);
			List<SearchHit<TranslationSource>> hits = searchHits.getSearchHits();
			if (hits.isEmpty()) {
				hasMore = false;
			} else {
				SourceCodeScanAction action = appendMatchingSourceCodes(hits, codes);
				if (action == SourceCodeScanAction.TOO_BROAD) {
					throw englishSearchTooBroadException();
				}
				if (action == SourceCodeScanAction.STOP || hits.size() < SOURCE_TERM_SEARCH_PAGE_SIZE) {
					hasMore = false;
				} else {
					searchAfter = extractSearchAfter(hits);
				}
			}
		}
		return codes;
	}

	private SearchHits<TranslationSource> searchTranslationSources(Query query, List<Object> searchAfter) {
		NativeQueryBuilder builder = NativeQuery.builder()
				.withQuery(query)
				.withPageable(PageRequest.of(0, SOURCE_TERM_SEARCH_PAGE_SIZE, Sort.by(Sort.Order.asc("order"))))
				.withTrackTotalHits(false);
		if (searchAfter != null) {
			builder.withSearchAfter(searchAfter);
		}
		return elasticsearchOperations.search(builder.build(), TranslationSource.class);
	}

	private enum SourceCodeScanAction {
		CONTINUE, STOP, TOO_BROAD
	}

	private static SourceCodeScanAction appendMatchingSourceCodes(List<SearchHit<TranslationSource>> hits, List<String> codes) {
		for (int i = 0; i < hits.size(); i++) {
			TranslationSource source = hits.get(i).getContent();
			if (source.getCode() != null) {
				codes.add(source.getCode());
			}
			if (codes.size() >= ENGLISH_SOURCE_SEARCH_MAX_RESULTS) {
				boolean moreResultsExist = i < hits.size() - 1 || hits.size() >= SOURCE_TERM_SEARCH_PAGE_SIZE;
				return moreResultsExist ? SourceCodeScanAction.TOO_BROAD : SourceCodeScanAction.STOP;
			}
		}
		return SourceCodeScanAction.CONTINUE;
	}

	private static List<Object> extractSearchAfter(List<SearchHit<TranslationSource>> hits) {
		List<Object> searchAfter = hits.get(hits.size() - 1).getSortValues();
		if (searchAfter.isEmpty()) {
			throw new IllegalStateException(
					"Elasticsearch returned no sort values for search_after; cannot continue English term search.");
		}
		return searchAfter;
	}

	private static ServiceExceptionWithStatusCode englishSearchTooBroadException() {
		return new ServiceExceptionWithStatusCode(
				"English search matched too many concepts (maximum %,d). Please narrow your search."
						.formatted(ENGLISH_SOURCE_SEARCH_MAX_RESULTS),
				HttpStatus.BAD_REQUEST,
				JobStatus.USER_CONTENT_ERROR);
	}

	private Page<TranslationUnit> pageUnitsInSetWithCriteria(String compositeSetCode, String compositeLanguageCode, Pageable pageable,
			TranslationStatus statusFilter) {
		Criteria c = unitsInSetCriteria(compositeSetCode, compositeLanguageCode);
		if (statusFilter != null) {
			c = c.and(new Criteria(TranslationUnit.Fields.STATUS).is(statusFilter.name()));
		}
		Criteria criteria = c;
		return paginateSearch(pageable, (p, searchAfter, trackTotal) -> {
			CriteriaQuery query = new CriteriaQuery(criteria);
			query.setPageable(p);
			query.setTrackTotalHits(trackTotal);
			if (searchAfter != null) {
				query.setSearchAfter(searchAfter);
			}
			return elasticsearchOperations.search(query, TranslationUnit.class);
		});
	}

	@FunctionalInterface
	private interface UnitSearchExecutor {
		SearchHits<TranslationUnit> search(Pageable pageable, List<Object> searchAfter, boolean trackTotalHits);
	}

	/**
	 * Offset pagination when within Elasticsearch's result window; otherwise skips to the requested offset
	 * via {@code search_after} batches, then fetches the page.
	 */
	private Page<TranslationUnit> paginateSearch(Pageable pageable, UnitSearchExecutor executor) {
		long offset = pageable.getOffset();
		int pageSize = pageable.getPageSize();
		Sort sort = pageable.getSort();

		if (offset + pageSize <= ELASTICSEARCH_MAX_RESULT_WINDOW) {
			SearchHits<TranslationUnit> searchHits = executor.search(pageable, null, true);
			return toPage(searchHits, pageable);
		}

		List<Object> searchAfter = null;
		long remaining = offset;
		while (remaining > 0) {
			int batchSize = (int) Math.min(remaining, STREAM_PAGE_SIZE);
			SearchHits<TranslationUnit> skipHits = executor.search(PageRequest.of(0, batchSize, sort), searchAfter, false);
			List<SearchHit<TranslationUnit>> hits = skipHits.getSearchHits();
			if (hits.isEmpty()) {
				return Page.empty(pageable);
			}
			remaining -= hits.size();
			if (remaining > 0 && hits.size() < batchSize) {
				return Page.empty(pageable);
			}
			searchAfter = hits.get(hits.size() - 1).getSortValues();
			if (searchAfter.isEmpty()) {
				throw new IllegalStateException(
						"Elasticsearch returned no sort values for search_after; cannot continue deep pagination.");
			}
		}

		SearchHits<TranslationUnit> searchHits = executor.search(PageRequest.of(0, pageSize, sort), searchAfter, true);
		return toPage(searchHits, pageable);
	}

	private static Page<TranslationUnit> toPage(SearchHits<TranslationUnit> searchHits, Pageable pageable) {
		List<TranslationUnit> content = searchHits.getSearchHits().stream().map(SearchHit::getContent).toList();
		return new PageImpl<>(content, pageable, searchHits.getTotalHits());
	}

	private static Query buildUnitsInSetQuery(String compositeSetCode, String compositeLanguageCode,
			TranslationStatus statusFilter, Collection<String> englishConceptCodes, String targetTerm) {
		return Query.of(q -> q.bool(b -> {
			b.filter(f -> f.term(t -> t.field(TranslationUnit.Fields.MEMBER_OF).value(compositeSetCode)));
			b.filter(f -> f.term(t -> t.field(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).value(compositeLanguageCode)));
			if (statusFilter != null) {
				b.filter(f -> f.term(t -> t.field(TranslationUnit.Fields.STATUS).value(statusFilter.name())));
			}
			if (englishConceptCodes != null) {
				List<FieldValue> values = englishConceptCodes.stream().map(FieldValue::of).toList();
				b.filter(f -> f.terms(t -> t.field(TranslationUnit.Fields.CODE).terms(tv -> tv.value(values))));
			}
			if (targetTerm != null) {
				b.filter(caseInsensitiveSubstringWildcardQuery(TRANSLATION_UNIT_TERMS_FIELD, targetTerm));
			}
			return b;
		}));
	}

	private static Query caseInsensitiveSubstringWildcardQuery(String field, String term) {
		String pattern = "*" + escapeWildcard(term) + "*";
		return Query.of(q -> q.wildcard(w -> w.field(field).value(pattern).caseInsensitive(true)));
	}

	static String escapeWildcard(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char ch = raw.charAt(i);
			if (ch == '*' || ch == '?' || ch == '\\') {
				out.append('\\');
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static final int TERM_SEARCH_MIN_LENGTH = 2;

	private static final int CONCEPT_CODE_MAX_LENGTH = 18;

	public static String normalizeOptionalSearchTerm(String term) {
		if (term == null) {
			return null;
		}
		String trimmed = term.trim();
		if (trimmed.length() < TERM_SEARCH_MIN_LENGTH) {
			return null;
		}
		return trimmed;
	}

	/**
	 * Detects English-search input that should filter by whole concept {@code code} (digits only).
	 *
	 * @return {@code null} when {@code term} is not an all-digit string (caller should use term search);
	 *         empty string when digits-only but length is outside {@value #TERM_SEARCH_MIN_LENGTH}–{@value #CONCEPT_CODE_MAX_LENGTH}
	 *         (caller should return empty results); otherwise the trimmed concept code
	 */
	public static String normalizeOptionalConceptCodeSearch(String term) {
		if (term == null) {
			return null;
		}
		String trimmed = term.trim();
		if (!trimmed.matches("\\d+")) {
			return null;
		}
		if (trimmed.length() < TERM_SEARCH_MIN_LENGTH || trimmed.length() > CONCEPT_CODE_MAX_LENGTH) {
			return "";
		}
		return trimmed;
	}

	public long countUnitsInSet(String compositeSetCode, String compositeLanguageCode) {
		return elasticsearchOperations.count(
				new CriteriaQuery(unitsInSetCriteria(compositeSetCode, compositeLanguageCode)),
				TranslationUnit.class);
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
		forEachMatching(unitsInSetCriteria(compositeSetCode, compositeLanguageCode),
				"cannot continue streaming units in set", consumer);
	}

	/**
	 * Visits every translation unit for a language/refset bucket using {@code search_after} paging.
	 */
	public void forEachUnitByCompositeLanguageCode(String compositeLanguageCode, Consumer<TranslationUnit> consumer) {
		Criteria criteria = new Criteria(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).is(compositeLanguageCode);
		forEachMatching(criteria, "cannot continue streaming units by composite language code", consumer);
	}

	private void forEachMatching(Criteria criteria, String searchAfterFailureMessage, Consumer<TranslationUnit> consumer) {
		List<Object> searchAfter = null;
		boolean hasMore = true;
		while (hasMore) {
			CriteriaQuery query = new CriteriaQuery(criteria);
			query.setPageable(PageRequest.of(0, STREAM_PAGE_SIZE, UNITS_IN_SET_STREAM_SORT));
			query.setTrackTotalHits(false);
			if (searchAfter != null) {
				query.setSearchAfter(searchAfter);
			}
			SearchHits<TranslationUnit> searchHits = elasticsearchOperations.search(query, TranslationUnit.class);
			List<SearchHit<TranslationUnit>> hits = searchHits.getSearchHits();
			if (hits.isEmpty()) {
				hasMore = false;
			} else {
				for (SearchHit<TranslationUnit> hit : hits) {
					consumer.accept(hit.getContent());
				}
				if (hits.size() < STREAM_PAGE_SIZE) {
					hasMore = false;
				} else {
					searchAfter = hits.get(hits.size() - 1).getSortValues();
					if (searchAfter.isEmpty()) {
						throw new IllegalStateException(
								"Elasticsearch returned no sort values for search_after; " + searchAfterFailureMessage + ".");
					}
				}
			}
		}
	}

	public List<TranslationUnit> listAllUnitsInSet(String compositeSetCode, String compositeLanguageCode) {
		List<TranslationUnit> all = new ArrayList<>();
		forEachUnitInSet(compositeSetCode, compositeLanguageCode, all::add);
		return all;
	}

	/**
	 * Returns every translation source concept id in hierarchy order using {@code search_after} paging.
	 */
	public List<Long> listAllSourceCodesByOrder() {
		List<Long> codes = new ArrayList<>();
		forEachTranslationSourceByOrder(source -> codes.add(Long.parseLong(source.getCode())));
		return codes;
	}

	/**
	 * Returns every translation source keyed by concept id using {@code search_after} paging.
	 */
	public Map<String, TranslationSource> mapAllTranslationSourcesByCode() {
		Map<String, TranslationSource> map = new HashMap<>();
		forEachTranslationSourceByCode(source -> map.put(source.getCode(), source));
		return map;
	}

	/**
	 * Visits every translation source in hierarchy order using {@code search_after} paging.
	 */
	public void forEachTranslationSourceByOrder(Consumer<TranslationSource> consumer) {
		forEachTranslationSource(new Criteria(), TRANSLATION_SOURCE_ORDER_STREAM_SORT,
				"cannot continue streaming translation sources by order", consumer);
	}

	/**
	 * Visits every translation source ordered by concept id using {@code search_after} paging.
	 */
	public void forEachTranslationSourceByCode(Consumer<TranslationSource> consumer) {
		forEachTranslationSource(new Criteria(), DEFAULT_STREAM_SORT,
				"cannot continue streaming translation sources by code", consumer);
	}

	private void forEachTranslationSource(Criteria criteria, Sort sort, String searchAfterFailureMessage,
			Consumer<TranslationSource> consumer) {
		List<Object> searchAfter = null;
		boolean hasMore = true;
		while (hasMore) {
			CriteriaQuery query = new CriteriaQuery(criteria);
			query.setPageable(PageRequest.of(0, STREAM_PAGE_SIZE, sort));
			query.setTrackTotalHits(false);
			if (searchAfter != null) {
				query.setSearchAfter(searchAfter);
			}
			SearchHits<TranslationSource> searchHits = elasticsearchOperations.search(query, TranslationSource.class);
			List<SearchHit<TranslationSource>> hits = searchHits.getSearchHits();
			if (hits.isEmpty()) {
				hasMore = false;
			} else {
				for (SearchHit<TranslationSource> hit : hits) {
					consumer.accept(hit.getContent());
				}
				if (hits.size() < STREAM_PAGE_SIZE) {
					hasMore = false;
				} else {
					searchAfter = hits.get(hits.size() - 1).getSortValues();
					if (searchAfter.isEmpty()) {
						throw new IllegalStateException(
								"Elasticsearch returned no sort values for search_after; " + searchAfterFailureMessage + ".");
					}
				}
			}
		}
	}

	public Map<String, Map<String, Long>> countStatusInSubsetBatch(String compositeLanguageCode, Collection<String> compositeSetCodes) {
		Map<String, Map<String, Long>> map = new HashMap<>();
		for (String setCode : compositeSetCodes) {
			Map<String, Long> statusCounts = new LinkedHashMap<>();
			for (TranslationStatus status : TranslationStatus.values()) {
				Criteria c = new Criteria(TranslationUnit.Fields.COMPOSITE_LANGUAGE_CODE).is(compositeLanguageCode)
						.and(new Criteria(TranslationUnit.Fields.MEMBER_OF).is(setCode))
						.and(new Criteria(TranslationUnit.Fields.STATUS).is(status.name()));
				long total = elasticsearchOperations.count(new CriteriaQuery(c), TranslationUnit.class);
				statusCounts.put(status.name(), total);
			}
			map.put(setCode, statusCounts);
		}
		return map;
	}
}
