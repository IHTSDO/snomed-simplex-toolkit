package org.snomed.simplex.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.service.StreamUtils;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;



class SnowstormExportClient {

	private static final Logger logger = LoggerFactory.getLogger(SnowstormExportClient.class);

	private final RestTemplate restTemplateRf2Export;
	private final ObjectMapper objectMapper;

	SnowstormExportClient(RestTemplate restTemplateRf2Export, ObjectMapper objectMapper) {
		this.restTemplateRf2Export = restTemplateRf2Export;
		this.objectMapper = objectMapper;
	}

	public void exportRF2(OutputStream outputStream, SnowstormExportConfiguration exportConfiguration) throws ServiceException {
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("branchPath", exportConfiguration.getCodeSystem().getWorkingBranchPath());
		requestBody.put("type", exportConfiguration.getExportType().name());
		if (exportConfiguration.getTransientEffectiveTime() != null) {
			requestBody.put("transientEffectiveTime", exportConfiguration.getTransientEffectiveTime());
		}
		if (exportConfiguration.getModuleIds() != null) {
			requestBody.put("moduleIds", exportConfiguration.getModuleIds());
		}
		if (exportConfiguration.isLanguageOnly()) {
			requestBody.put("languageOnly", true);
		}
		URI location = restTemplateRf2Export.execute("/exports", HttpMethod.POST,
				httpRequest -> {
					httpRequest.getHeaders().add("Content-Type", "application/json");
					objectMapper.writeValue(httpRequest.getBody(), requestBody);
				},
				httpResponse -> httpResponse.getHeaders().getLocation());
		if (location == null) {
			throw new ServiceException("Snowstorm did not return a location header for the RF2 export.");
		}
		String archiveLocation = location + "/archive";
		logger.info("Downloading export from {}", archiveLocation);
		restTemplateRf2Export.execute(archiveLocation, HttpMethod.GET,
				httpRequest -> httpRequest.getHeaders().add("Accept", "application/zip"), httpResponse -> {
			StreamUtils.copyViaTempFile(httpResponse.getBody(), outputStream, false);
			return null;
		});
	}
}
