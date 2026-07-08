package org.snomed.simplex.util;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class ElasticsearchExceptionSupport {

	private static final String GENERIC_MESSAGE = "Unexpected error.";
	public static final String UNKNOWN = "unknown";

	private ElasticsearchExceptionSupport() {
	}

	public static boolean isElasticsearchFailure(Throwable throwable) {
		return findUncategorized(throwable).isPresent() || findClientException(throwable).isPresent();
	}

	public static Optional<UncategorizedElasticsearchException> findUncategorized(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof UncategorizedElasticsearchException uncategorized) {
				return Optional.of(uncategorized);
			}
		}
		return Optional.empty();
	}

	public static Optional<ElasticsearchException> findClientException(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof ElasticsearchException elasticsearchException) {
				return Optional.of(elasticsearchException);
			}
		}
		return Optional.empty();
	}

	public static ServiceException wrapWithCause(Throwable original) {
		return new ServiceException(GENERIC_MESSAGE, original);
	}

	public static String buildShortSummary(Throwable throwable) {
		if (throwable == null) {
			return UNKNOWN;
		}
		Optional<ElasticsearchException> clientException = findClientException(throwable);
		if (clientException.isPresent()) {
			ErrorCause error = clientException.get().error();
			if (error != null && error.rootCause() != null && !error.rootCause().isEmpty()) {
				ErrorCause rootCause = error.rootCause().get(0);
				if (rootCause.reason() != null && !rootCause.reason().isBlank()) {
					return rootCause.reason();
				}
			}
			if (error != null && error.reason() != null && !error.reason().isBlank()) {
				return error.reason();
			}
		}
		Optional<UncategorizedElasticsearchException> uncategorized = findUncategorized(throwable);
		if (uncategorized.isPresent() && uncategorized.get().getMessage() != null) {
			return uncategorized.get().getMessage();
		}
		return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
	}

	public static String buildLogDetails(Throwable throwable) {
		if (throwable == null) {
			return "none";
		}

		StringJoiner details = new StringJoiner(" ");
		findClientException(throwable).ifPresent(clientException -> appendClientExceptionDetails(details, clientException));
		findUncategorized(throwable).ifPresent(uncategorized -> appendUncategorizedDetails(details, uncategorized));

		if (details.length() == 0) {
			return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
		}
		return details.toString();
	}

	private static void appendClientExceptionDetails(StringJoiner details, ElasticsearchException clientException) {
		details.add("status=" + clientException.status());
		if (clientException.endpointId() != null) {
			details.add("endpoint=" + clientException.endpointId());
		}
		ErrorCause error = clientException.error();
		if (error != null) {
			appendErrorCause(details, error);
			appendRootCauses(details, error.rootCause());
		}
	}

	private static void appendUncategorizedDetails(StringJoiner details, UncategorizedElasticsearchException uncategorized) {
		if (uncategorized.getStatusCode() != null) {
			details.add("status=" + uncategorized.getStatusCode());
		}
		if (uncategorized.getMessage() != null) {
			details.add("message=" + uncategorized.getMessage());
		}
		String responseBody = uncategorized.getResponseBody();
		if (responseBody != null && !responseBody.isBlank()) {
			details.add("responseBody=" + responseBody);
		}
	}

	private static void appendErrorCause(StringJoiner details, ErrorCause errorCause) {
		if (errorCause.type() != null) {
			details.add("type=" + errorCause.type());
		}
		if (errorCause.reason() != null) {
			details.add("reason=" + errorCause.reason());
		}
	}

	private static void appendRootCauses(StringJoiner details, List<ErrorCause> rootCauses) {
		if (rootCauses == null || rootCauses.isEmpty()) {
			return;
		}
		List<String> formattedRootCauses = new ArrayList<>();
		for (ErrorCause rootCause : rootCauses) {
			formattedRootCauses.add(formatErrorCause(rootCause));
		}
		details.add("rootCauses=[" + String.join(", ", formattedRootCauses) + "]");
	}

	private static String formatErrorCause(ErrorCause errorCause) {
		String type = errorCause.type() != null ? errorCause.type() : UNKNOWN;
		String reason = errorCause.reason() != null ? errorCause.reason() : UNKNOWN;
		return "{type=" + type + ", reason=" + reason + "}";
	}

}
