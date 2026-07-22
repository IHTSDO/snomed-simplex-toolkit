package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationSearchServiceTest {

	@Mock
	private ElasticsearchOperations elasticsearchOperations;

	private SnolateTranslationSearchService service;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationSearchService(elasticsearchOperations);
	}

	@Test
	void escapeWildcard_escapesMetacharacters() {
		assertThat(SnolateTranslationSearchService.escapeWildcard("a*b?c\\d")).isEqualTo("a\\*b\\?c\\\\d");
	}

	@Test
	void normalizeOptionalSearchTerm_trimsAndReturnsNullForBlankOrTooShort() {
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm(null)).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm("   ")).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm("a")).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm(" diabetes ")).isEqualTo("diabetes");
	}

	@Test
	void normalizeOptionalConceptCodeSearch_detectsWholeCodeOrRejectsInvalidNumeric() {
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch(null)).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch("asthma")).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch("66379009x")).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch("1")).isEmpty();
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch("66379009")).isEqualTo("66379009");
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch(" 66379009 ")).isEqualTo("66379009");
		assertThat(SnolateTranslationSearchService.normalizeOptionalConceptCodeSearch("1234567890123456789")).isEmpty();
	}

	@Test
	void pageUnitsInSet_returnsEmptyPageWhenEnglishCodesFilterIsEmpty() throws ServiceExceptionWithStatusCode {
		Pageable pageable = PageRequest.of(0, 25, Sort.by("statusSort", "order", "code"));

		Page<TranslationUnit> page = service.pageUnitsInSet("set", "en-123", pageable, TranslationStatus.APPROVED,
				List.of(), null);

		assertThat(page.getTotalElements()).isZero();
		assertThat(page.getContent()).isEmpty();
		verify(elasticsearchOperations, never()).search(any(Query.class), eq(TranslationUnit.class));
	}

	@Test
	void findSourceCodesByTermSubstring_returnsEmptyListForBlankTerm() throws ServiceExceptionWithStatusCode {
		assertThat(service.findSourceCodesByTermSubstring("  ")).isEmpty();
		assertThat(service.findSourceCodesByTermSubstring("a")).isEmpty();
		verify(elasticsearchOperations, never()).search(any(Query.class), eq(TranslationSource.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void findSourceCodesByTermSubstring_returnsMatchingConceptCodes() throws ServiceExceptionWithStatusCode {
		TranslationSource source = new TranslationSource("100", "Diabetes mellitus", 0);
		SearchHit<TranslationSource> hit = org.mockito.Mockito.mock(SearchHit.class);
		when(hit.getContent()).thenReturn(source);
		SearchHits<TranslationSource> searchHits = org.mockito.Mockito.mock(SearchHits.class);
		when(searchHits.getSearchHits()).thenReturn(List.of(hit));
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationSource.class))).thenReturn(searchHits);

		List<String> codes = service.findSourceCodesByTermSubstring("diabetes");

		assertThat(codes).containsExactly("100");
	}

	@Test
	void englishSourceSearchMaxResults_isBelowElasticsearchTermsLimit() {
		assertThat(SnolateTranslationSearchService.ENGLISH_SOURCE_SEARCH_MAX_RESULTS)
				.isEqualTo(50_000)
				.isLessThan(65_536);
	}

	@Test
	void pageUnitsInSet_throwsWhenEnglishCodesExceedSoftLimit() {
		Pageable pageable = PageRequest.of(0, 25, Sort.by("statusSort", "order", "code"));
		List<String> tooMany = java.util.stream.IntStream.range(0, SnolateTranslationSearchService.ENGLISH_SOURCE_SEARCH_MAX_RESULTS + 1)
				.mapToObj(String::valueOf)
				.toList();

		assertThatThrownBy(() -> service.pageUnitsInSet("set", "en-123", pageable, null, tooMany, null))
				.isInstanceOf(ServiceExceptionWithStatusCode.class)
				.hasMessageContaining("too many concepts")
				.hasMessageContaining("50,000");

		verify(elasticsearchOperations, never()).search(any(Query.class), eq(TranslationUnit.class));
	}

	private static final Sort ROWS_SORT = Sort.by("statusSort", "order", "code");

	@Test
	@SuppressWarnings("unchecked")
	void listAllSourceCodesByOrder_usesSearchAfterForMultiplePages() {
		AtomicInteger callCount = new AtomicInteger();
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationSource.class))).thenAnswer(invocation -> {
			Query query = invocation.getArgument(0);
			int call = callCount.getAndIncrement();
			if (call == 0) {
				return mockTranslationSourceSearchHits(sourceOrderHits(5_000, 0));
			}
			assertThat(((CriteriaQuery) query).getSearchAfter()).isNotNull();
			return mockTranslationSourceSearchHits(sourceOrderHits(2, 5_000));
		});

		List<Long> codes = service.listAllSourceCodesByOrder();

		assertThat(codes).hasSize(5_002);
		assertThat(codes.get(0)).isZero();
		assertThat(codes.get(5_000)).isEqualTo(5_000L);
		verify(elasticsearchOperations, times(2)).search(any(Query.class), eq(TranslationSource.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void mapAllTranslationSourcesByCode_usesSearchAfterForMultiplePages() {
		AtomicInteger callCount = new AtomicInteger();
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationSource.class))).thenAnswer(invocation -> {
			int call = callCount.getAndIncrement();
			if (call == 0) {
				return mockTranslationSourceSearchHits(sourceCodeHits(5_000));
			}
			return mockTranslationSourceSearchHits(sourceCodeHits(1, "9999"));
		});

		var map = service.mapAllTranslationSourcesByCode();

		assertThat(map).hasSize(5_001);
		assertThat(map.get("0").getTerm()).isEqualTo("term-0");
		assertThat(map.get("9999").getTerm()).isEqualTo("term-9999");
		verify(elasticsearchOperations, times(2)).search(any(Query.class), eq(TranslationSource.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void pageUnitsInSet_usesOffsetPaginationWithinResultWindow() throws ServiceExceptionWithStatusCode {
		Pageable pageable = PageRequest.of(0, 25, ROWS_SORT);
		SearchHits<TranslationUnit> searchHits = mockPageSearchHits(List.of(mockHit("100", List.of(1, 0, "100"))), 1);

		when(elasticsearchOperations.search(any(Query.class), eq(TranslationUnit.class))).thenReturn(searchHits);

		Page<TranslationUnit> page = service.pageUnitsInSet("set", "en-123", pageable);

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getTotalElements()).isEqualTo(1);

		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(elasticsearchOperations, times(1)).search(captor.capture(), eq(TranslationUnit.class));
		CriteriaQuery query = (CriteriaQuery) captor.getValue();
		assertThat(query.getSearchAfter()).isNull();
		assertThat(query.getPageable().getOffset()).isZero();
		assertThat(query.getPageable().getPageSize()).isEqualTo(25);
	}

	@Test
	@SuppressWarnings("unchecked")
	void pageUnitsInSet_usesSearchAfterBeyondResultWindow() throws ServiceExceptionWithStatusCode {
		Pageable pageable = PageRequest.of(123, 100, ROWS_SORT);
		AtomicInteger callCount = new AtomicInteger();
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationUnit.class))).thenAnswer(invocation -> {
			Query query = invocation.getArgument(0);
			int call = callCount.getAndIncrement();
			if (call < 3) {
				int batchSize = query.getPageable().getPageSize();
				return mockSkipSearchHits(skipBatchHits(batchSize));
			}
			return mockPageSearchHits(contentHits(100, "final"), 12_400);
		});

		Page<TranslationUnit> page = service.pageUnitsInSet("set", "en-123", pageable);

		assertThat(page.getContent()).hasSize(100);
		assertThat(page.getTotalElements()).isEqualTo(12_400);
		assertThat(page.getContent().get(0).getCode()).startsWith("final-");

		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(elasticsearchOperations, times(4)).search(captor.capture(), eq(TranslationUnit.class));
		List<Query> queries = captor.getAllValues();
		assertThat(((CriteriaQuery) queries.get(0)).getSearchAfter()).isNull();
		assertThat(((CriteriaQuery) queries.get(1)).getSearchAfter()).isNotNull();
		assertThat(((CriteriaQuery) queries.get(3)).getSearchAfter()).isNotNull();
		assertThat(queries.get(3).getPageable().getPageSize()).isEqualTo(100);
	}

	@Test
	@SuppressWarnings("unchecked")
	void pageUnitsInSet_deepPagePastEndReturnsEmptyPage() throws ServiceExceptionWithStatusCode {
		Pageable pageable = PageRequest.of(123, 100, ROWS_SORT);
		AtomicInteger callCount = new AtomicInteger();
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationUnit.class))).thenAnswer(invocation -> {
			if (callCount.getAndIncrement() == 0) {
				return mockSkipSearchHits(skipBatchHits(5_000));
			}
			return mockSkipSearchHits(List.of());
		});

		Page<TranslationUnit> page = service.pageUnitsInSet("set", "en-123", pageable);

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
	}

	@Test
	@SuppressWarnings("unchecked")
	void pageUnitsInSet_filteredDeepPageUsesSearchAfter() throws ServiceExceptionWithStatusCode {
		Pageable pageable = PageRequest.of(123, 100, ROWS_SORT);
		AtomicInteger callCount = new AtomicInteger();
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationUnit.class))).thenAnswer(invocation -> {
			Query query = invocation.getArgument(0);
			int call = callCount.getAndIncrement();
			if (call < 3) {
				int batchSize = query.getPageable().getPageSize();
				return mockSkipSearchHits(skipBatchHits(batchSize));
			}
			return mockPageSearchHits(contentHits(50, "filtered"), 12_400);
		});

		Page<TranslationUnit> page = service.pageUnitsInSet("set", "en-123", pageable, null, List.of("66379009"), null);

		assertThat(page.getContent()).hasSize(50);

		ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
		verify(elasticsearchOperations, times(4)).search(captor.capture(), eq(TranslationUnit.class));
		assertThat(captor.getAllValues().get(0)).isInstanceOf(NativeQuery.class);
		assertThat(captor.getAllValues().get(3)).isInstanceOf(NativeQuery.class);
	}

	@SuppressWarnings("unchecked")
	private SearchHit<TranslationUnit> mockHit(String code, List<Object> sortValues) {
		SearchHit<TranslationUnit> hit = mock(SearchHit.class);
		TranslationUnit unit = new TranslationUnit();
		unit.setCode(code);
		when(hit.getContent()).thenReturn(unit);
		lenient().when(hit.getSortValues()).thenReturn(sortValues);
		return hit;
	}

	@SuppressWarnings("unchecked")
	private SearchHits<TranslationUnit> mockSkipSearchHits(List<SearchHit<TranslationUnit>> hits) {
		SearchHits<TranslationUnit> searchHits = mock(SearchHits.class);
		when(searchHits.getSearchHits()).thenReturn(hits);
		return searchHits;
	}

	@SuppressWarnings("unchecked")
	private SearchHits<TranslationUnit> mockPageSearchHits(List<SearchHit<TranslationUnit>> hits, long total) {
		SearchHits<TranslationUnit> searchHits = mock(SearchHits.class);
		when(searchHits.getSearchHits()).thenReturn(hits);
		when(searchHits.getTotalHits()).thenReturn(total);
		return searchHits;
	}

	private List<SearchHit<TranslationUnit>> skipBatchHits(int count) {
		SearchHit<TranslationUnit> tail = mockSkipTailHit(count);
		if (count <= 1) {
			return List.of(tail);
		}
		return new AbstractList<>() {
			@Override
			public SearchHit<TranslationUnit> get(int index) {
				return index == count - 1 ? tail : placeholderHit();
			}

			@Override
			public int size() {
				return count;
			}
		};
	}

	@SuppressWarnings("unchecked")
	private SearchHit<TranslationUnit> mockSkipTailHit(int count) {
		SearchHit<TranslationUnit> hit = mock(SearchHit.class);
		when(hit.getSortValues()).thenReturn(List.of(count, 0, "skip-" + count));
		return hit;
	}

	@SuppressWarnings("unchecked")
	private SearchHit<TranslationUnit> placeholderHit() {
		return mock(SearchHit.class);
	}

	private List<SearchHit<TranslationUnit>> contentHits(int count, String codePrefix) {
		List<SearchHit<TranslationUnit>> hits = new java.util.ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			hits.add(mockHit(codePrefix + "-" + i, List.of(i, 0, codePrefix + "-" + i)));
		}
		return hits;
	}

	@SuppressWarnings("unchecked")
	private SearchHits<TranslationSource> mockTranslationSourceSearchHits(List<SearchHit<TranslationSource>> hits) {
		SearchHits<TranslationSource> searchHits = mock(SearchHits.class);
		when(searchHits.getSearchHits()).thenReturn(hits);
		return searchHits;
	}

	private List<SearchHit<TranslationSource>> sourceOrderHits(int count, int orderOffset) {
		List<SearchHit<TranslationSource>> hits = new java.util.ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			int order = orderOffset + i;
			List<Object> sortValues = i == count - 1 ? List.of(order, String.valueOf(order)) : List.of();
			hits.add(mockSourceHit(String.valueOf(order), "term-" + order, order, sortValues));
		}
		return hits;
	}

	private List<SearchHit<TranslationSource>> sourceCodeHits(int count) {
		return sourceCodeHits(count, "0");
	}

	private List<SearchHit<TranslationSource>> sourceCodeHits(int count, String codePrefix) {
		List<SearchHit<TranslationSource>> hits = new java.util.ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String code = codePrefix.equals("0") ? String.valueOf(i) : codePrefix;
			List<Object> sortValues = i == count - 1 ? List.of(code) : List.of();
			hits.add(mockSourceHit(code, "term-" + code, i, sortValues));
		}
		return hits;
	}

	@SuppressWarnings("unchecked")
	private SearchHit<TranslationSource> mockSourceHit(String code, String term, int order, List<Object> sortValues) {
		SearchHit<TranslationSource> hit = mock(SearchHit.class);
		when(hit.getContent()).thenReturn(new TranslationSource(code, term, order));
		lenient().when(hit.getSortValues()).thenReturn(sortValues);
		return hit;
	}
}
