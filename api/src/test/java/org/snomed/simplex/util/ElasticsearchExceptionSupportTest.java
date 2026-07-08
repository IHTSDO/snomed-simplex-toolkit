package org.snomed.simplex.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;

import static org.junit.jupiter.api.Assertions.*;

class ElasticsearchExceptionSupportTest {

	@Test
	void isElasticsearchFailureDetectsWrappedElasticsearchException() {
		Throwable failure = productionLikeFailureChain();

		assertTrue(ElasticsearchExceptionSupport.isElasticsearchFailure(failure));
	}

	@Test
	void isElasticsearchFailureReturnsFalseForNonElasticsearchException() {
		assertFalse(ElasticsearchExceptionSupport.isElasticsearchFailure(new RuntimeException("boom")));
	}

	@Test
	void wrapWithCauseAlwaysUsesGenericMessage() {
		ServiceException serviceException = ElasticsearchExceptionSupport.wrapWithCause(productionLikeFailureChain());

		assertEquals("Unexpected error.", serviceException.getMessage());
		assertNotNull(serviceException.getCause());
		assertTrue(ElasticsearchExceptionSupport.isElasticsearchFailure(serviceException));
	}

	@Test
	void buildShortSummaryExtractsRootCauseReason() {
		String summary = ElasticsearchExceptionSupport.buildShortSummary(productionLikeFailureChain());

		assertEquals("failed to create query", summary);
	}

	@Test
	void buildLogDetailsIncludesStatusRootCauseAndResponseBody() {
		UncategorizedElasticsearchException uncategorized = uncategorizedException();

		String details = ElasticsearchExceptionSupport.buildLogDetails(uncategorized);

		assertTrue(details.contains("status=503"));
		assertTrue(details.contains("endpoint=[es/search]"));
		assertTrue(details.contains("type=search_phase_execution_exception"));
		assertTrue(details.contains("reason=all shards failed"));
		assertTrue(details.contains("rootCauses=[{type=query_shard_exception, reason=failed to create query}]"));
		assertTrue(details.contains("responseBody="));
	}

	@Test
	void buildLogDetailsWorksForProductionLikeCauseChain() {
		String details = ElasticsearchExceptionSupport.buildLogDetails(productionLikeFailureChain());

		assertTrue(details.contains("status=503"));
		assertTrue(details.contains("type=search_phase_execution_exception"));
		assertTrue(details.contains("query_shard_exception"));
		assertTrue(details.contains("responseBody="));
	}

	@Test
	void findUncategorizedWalksCauseChain() {
		ServiceException wrapped = ElasticsearchExceptionSupport.wrapWithCause(uncategorizedException());

		assertTrue(ElasticsearchExceptionSupport.findUncategorized(wrapped).isPresent());
	}

	@Test
	void findClientExceptionWalksCauseChain() {
		ServiceException wrapped = ElasticsearchExceptionSupport.wrapWithCause(uncategorizedException());

		assertTrue(ElasticsearchExceptionSupport.findClientException(wrapped).isPresent());
	}

	private Throwable productionLikeFailureChain() {
		return new ServiceException("Unexpected error.", uncategorizedException());
	}

	private UncategorizedElasticsearchException uncategorizedException() {
		ErrorCause rootCause = ErrorCause.of(builder -> builder
				.type("query_shard_exception")
				.reason("failed to create query"));
		ErrorCause error = ErrorCause.of(builder -> builder
				.type("search_phase_execution_exception")
				.reason("all shards failed")
				.rootCause(rootCause));
		ErrorResponse response = ErrorResponse.of(builder -> builder
				.status(503)
				.error(error));
		ElasticsearchException clientException = new ElasticsearchException(
				"[es/search] failed: [search_phase_execution_exception] all shards failed", response);
		return new UncategorizedElasticsearchException(
				"[es/search] failed: [search_phase_execution_exception] all shards failed",
				503,
				"{\"error\":{\"type\":\"search_phase_execution_exception\",\"reason\":\"all shards failed\"}}",
				clientException);
	}

}
