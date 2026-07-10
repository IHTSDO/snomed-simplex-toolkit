package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
	void normalizeOptionalSearchTerm_trimsAndReturnsNullForBlank() {
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm(null)).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm("   ")).isNull();
		assertThat(SnolateTranslationSearchService.normalizeOptionalSearchTerm(" diabetes ")).isEqualTo("diabetes");
	}

	@Test
	void pageUnitsInSet_returnsEmptyPageWhenEnglishCodesFilterIsEmpty() {
		Pageable pageable = PageRequest.of(0, 25, Sort.by("statusSort", "order", "code"));

		Page<TranslationUnit> page = service.pageUnitsInSet("set", "en-123", pageable, TranslationStatus.APPROVED,
				List.of(), null);

		assertThat(page.getTotalElements()).isZero();
		assertThat(page.getContent()).isEmpty();
		verify(elasticsearchOperations, never()).search(any(Query.class), eq(TranslationUnit.class));
	}

	@Test
	void findSourceCodesByTermSubstring_returnsEmptyListForBlankTerm() {
		assertThat(service.findSourceCodesByTermSubstring("  ")).isEmpty();
		verify(elasticsearchOperations, never()).search(any(Query.class), eq(TranslationSource.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void findSourceCodesByTermSubstring_returnsMatchingConceptCodes() {
		TranslationSource source = new TranslationSource("100", "Diabetes mellitus", 0);
		SearchHit<TranslationSource> hit = org.mockito.Mockito.mock(SearchHit.class);
		when(hit.getContent()).thenReturn(source);
		SearchHits<TranslationSource> searchHits = org.mockito.Mockito.mock(SearchHits.class);
		when(searchHits.getSearchHits()).thenReturn(List.of(hit));
		when(elasticsearchOperations.search(any(Query.class), eq(TranslationSource.class))).thenReturn(searchHits);

		List<String> codes = service.findSourceCodesByTermSubstring("diabetes");

		assertThat(codes).containsExactly("100");
	}
}
