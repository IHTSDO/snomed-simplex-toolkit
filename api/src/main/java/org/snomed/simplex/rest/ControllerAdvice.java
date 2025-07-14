package org.snomed.simplex.rest;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.HTTPClientException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ControllerAdvice {

	private static final Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

	@ExceptionHandler({
			IllegalArgumentException.class,
			HttpRequestMethodNotSupportedException.class,
			HttpMediaTypeNotSupportedException.class,
			MethodArgumentNotValidException.class,
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class,
			HttpMessageNotReadableException.class
	})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleIllegalArgumentException(Exception exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.BAD_REQUEST);
		result.put("message", exception.getMessage());
		if (exception.getCause() != null) {
			result.put("causeMessage", exception.getCause().getMessage());
		}
		logger.info("bad request {}", exception.getMessage());
		logger.debug("bad request {}", exception.getMessage(), exception);
		return result;
	}

	@ExceptionHandler({
			IllegalStateException.class
	})
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, Object> handleIllegalStateException(Exception exception) {
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.CONFLICT);
		result.put("message", exception.getMessage());
		if (exception.getCause() != null) {
			result.put("causeMessage", exception.getCause().getMessage());
		}
		logger.info("illegal state {}", exception.getMessage());
		logger.debug("illegal state {}", exception.getMessage(), exception);
		return result;
	}

	@ExceptionHandler(ClientAbortException.class)
	@ResponseStatus(HttpStatus.OK)
	public void handleClientAbortException(Exception exception) {
		logger.info("A client aborted an HTTP connection, probably a page refresh during loading.");
		logger.debug("ClientAbortException.", exception);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void handleNotFound(Exception exception) {
		logger.debug("NoResourceFoundException", exception);
	}

	@ExceptionHandler(HTTPClientException.class)
	public ResponseEntity<HashMap<String, Object>> handleClientException(HTTPClientException clientException) {
		HttpStatusCodeException cause = clientException.getCause();
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", cause.getStatusCode());
		result.put("message", "Client error: " + cause.getMessage());
		logger.info("Http Client Exception.", clientException);
		return new ResponseEntity<>(result, cause.getStatusCode());
	}

	@ExceptionHandler(ServiceExceptionWithStatusCode.class)
	public ResponseEntity<HashMap<String, Object>> handleClientException(ServiceExceptionWithStatusCode exception) {
		int statusCode = exception.getStatusCode();
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", statusCode);
		result.put("message", exception.getMessage());
		String message = "Exception with status code: {} - {}";
		if (statusCode >= 500 && statusCode < 600) {
			logger.error(message, statusCode, exception.getMessage(), exception);
		} else {
			logger.info(message, statusCode, exception.getMessage());
			logger.debug(message, statusCode, exception.getMessage(), exception);
		}

		return new ResponseEntity<>(result, HttpStatusCode.valueOf(statusCode));
	}

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Map<String, Object> handleAccessDeniedException(AccessDeniedException exception) {
		logger.debug(exception.getMessage(), exception);
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.FORBIDDEN);
		result.put("message", exception.getMessage());
		return result;
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public Map<String,Object> handleException(Exception exception) {
		logger.error(exception.getMessage(), exception);
		HashMap<String, Object> result = new HashMap<>();
		result.put("error", HttpStatus.INTERNAL_SERVER_ERROR);
		result.put("message", exception.getMessage());
		return result;
	}

}
