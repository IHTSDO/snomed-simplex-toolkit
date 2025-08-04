package org.snomed.simplex.weblate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static org.snomed.simplex.weblate.WeblateClient.UNITS_RESPONSE_TYPE;
import static org.snomed.simplex.weblate.WeblateClient.getUnitQuery;

public class WeblateUnitStream implements UnitSupplier {

	private final WeblateClient weblateClient;
	private String nextUrl;
	private WeblatePage<WeblateUnit> page;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Iterator<WeblateUnit> iterator;
	public WeblateUnitStream(String projectSlug, String componentSlug, int startPage, WeblateClient weblateClient) {
		nextUrl = "%s&page=%s".formatted(getUnitQuery(UnitQueryBuilder.of(projectSlug, componentSlug)
				.pageSize(1000)
				.fastestSort(true)), startPage);
		this.weblateClient = weblateClient;
	}

	public WeblateUnit get() throws ServiceExceptionWithStatusCode {

		if (iterator != null && iterator.hasNext()) {
			return iterator.next();
		}

		if (page == null || nextUrl != null) {
			try {
				LoggerFactory.getLogger(getClass()).info("Getting next unit batch from {}", nextUrl);
				ResponseEntity<WeblatePage<WeblateUnit>> response = weblateClient.getRestTemplate().exchange(nextUrl,
						HttpMethod.GET, null, UNITS_RESPONSE_TYPE);

				page = response.getBody();
				if (page != null) {
					nextUrl = page.next();
					if (nextUrl != null) {
						logPercentComplete();
						nextUrl = nextUrl.substring(nextUrl.indexOf("/units/"));
						nextUrl = URLDecoder.decode(nextUrl, StandardCharsets.UTF_8);
					}
					iterator = page.results().iterator();
					return iterator.next();
				}
			} catch (HttpClientErrorException e) {
				weblateClient.handleSharedCodeSystemError("Failed to fetch translation units.", HttpStatus.INTERNAL_SERVER_ERROR, e);
			}
		}

		// Nothing left
		return null;
	}

	private void logPercentComplete() {
		if (logger.isInfoEnabled()) {
			String[] split = nextUrl.split("&");
			String pageNum = split[1];
			pageNum = pageNum.split("=")[1];
			int done = Integer.parseInt(pageNum) * 100;
			if (done % 1000 == 0) {
				int percentComplete = Math.round(((float) done / page.count()) * 100);
				logger.info("Completed {}/{}, {}%", String.format("%,d", done), String.format("%,d", page.count()), percentComplete);
			}
		}
	}
}
