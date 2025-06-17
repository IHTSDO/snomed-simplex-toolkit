package org.snomed.simplex.client.rvf;

import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.srs.ReleaseServiceClient;
import org.snomed.simplex.domain.JobStatus;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.SpreadsheetService;
import org.snomed.simplex.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
public class ValidationServiceClient {

	private static final String RVF_TS = "RVF_TS";
	private static final String VALIDATION_RESPONSE_QUEUE = "termserver-release-validation.response";

	private final RestTemplate restTemplate;
	private final String queuePrefix;
	private final SpreadsheetService spreadsheetService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValidationServiceClient(@Value("${rvf.url}") String rvfUrl, @Value("${jms.queue.prefix}") String queuePrefix,
								   @Autowired SpreadsheetService spreadsheetService) {
		this.restTemplate = new RestTemplateBuilder()
				.rootUri(rvfUrl)
				.interceptors((request, body, execution) -> {
					// Add authentication token to RVF request
					String authenticationToken = SecurityUtil.getAuthenticationToken();
					request.getHeaders().add("Cookie", authenticationToken);
					return execution.execute(request, body);
				})
				.messageConverters(new MappingJackson2HttpMessageConverter(), new FormHttpMessageConverter(), new ByteArrayHttpMessageConverter())
				.additionalRequestCustomizers(request -> request.getHeaders().add("Cookie", getAuthenticationToken()))
				.build();
		this.queuePrefix = queuePrefix;
		this.spreadsheetService = spreadsheetService;
	}

	private String getAuthenticationToken() {
		SecurityContext securityContext = SecurityContextHolder.getContext();
		if (securityContext != null) {
			Authentication authentication = securityContext.getAuthentication();
			if (authentication != null) {
				return (String) authentication.getCredentials();
			}
		}
		return null;
	}

	public URI startValidation(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		File tempFile = null;
		try {
			String branchPath;
			String effectiveTime = new SimpleDateFormat("yyyyMMdd").format(new Date());
			Long headTimestamp;
			try {
				// Export RF2 delta
				logger.info("Exporting delta for validation, {}", codeSystem.getShortName());
				tempFile = File.createTempFile("export-" + UUID.randomUUID(), ".zip");
				branchPath = codeSystem.getWorkingBranchPath();
				Branch branch = snowstormClient.getBranchOrThrow(branchPath);
				headTimestamp = branch.getHeadTimestamp();
				try (OutputStream outputStream = new FileOutputStream(tempFile)) {
					snowstormClient.exportRF2(outputStream, "DELTA", codeSystem, effectiveTime);
				}
			} catch (IOException e) {
				throw new ServiceException("Failed to export RF2 for validation.", e);
			}
			try {
				// Send to RVF
				HttpEntity<MultiValueMap<String, Object>> request = buildValidationRequest(codeSystem, branchPath, headTimestamp, tempFile, effectiveTime);
				return restTemplate.postForLocation("/run-post", request);
			} catch (IOException e) {
				throw new ServiceException("Failed to build validation request.", e);
			}
		} finally {
			if (tempFile != null) {
				if (!tempFile.delete()) {
					logger.warn("Failed to delete temp file {}", tempFile.getAbsoluteFile());
				}
			}
		}
	}

	public ValidationReport getValidation(String validationUrl) throws ServiceException {
		try {
			return restTemplate.getForEntity(validationUrl, ValidationReport.class).getBody();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch RVF validation report.", e);
		}
	}

	private HttpEntity<MultiValueMap<String, Object>> buildValidationRequest(CodeSystem codeSystem, String branchPath, Long headTimestamp, File zipFile, String effectiveTime) throws IOException, ServiceException {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

		MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
		ContentDisposition contentDisposition = ContentDisposition
				.builder("form-data")
				.name("file")
				.filename(zipFile.getName())
				.build();
		fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
		fileMap.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
		HttpEntity<byte[]> fileEntity = new HttpEntity<>(FileUtils.readFileToByteArray(zipFile), fileMap);

		String runId = Long.toString(System.currentTimeMillis());
		body.add("runId", runId);
		body.add("file", fileEntity);
		body.add("rf2DeltaOnly", "true");
		body.add("effectiveTime", effectiveTime);

		String previousPackage = "empty-rf2-snapshot.zip";
		CodeSystem.CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
		if (latestVersion != null) {
			if (latestVersion.releasePackage() == null) {
				throw new ServiceExceptionWithStatusCode("Failed to start validation. Latest release package is not set.",
						HttpStatus.CONFLICT, JobStatus.SYSTEM_ERROR);
			}
			previousPackage = latestVersion.releasePackage();
		}
		body.add("previousRelease", previousPackage);

		String dependencyPackage = codeSystem.getDependencyPackage();
		if (dependencyPackage != null) {
			body.add("dependencyRelease", codeSystem.getDependencyPackage());
		}

		body.add("groups", ReleaseServiceClient.VALIDATION_ASSERTION_GROUP_NAMES);
		body.add("enableDrools", "true");
		body.add("enableMRCMValidation", "true");
		body.add("branchPath", branchPath);
		body.add("contentHeadTimestamp", Long.toString(headTimestamp));
		body.add("responseQueue", queuePrefix + "." + VALIDATION_RESPONSE_QUEUE);
		body.add("droolsRulesGroups", ReleaseServiceClient.DROOLS_RULES_GROUP_NAMES);
		body.add("includedModules", codeSystem.getDefaultModuleOrThrow());
		body.add("failureExportMax", "100");
		String storageLocation = RVF_TS + "/Simplex_" + codeSystem.getShortName() + "/" + runId;
		body.add("storageLocation", storageLocation);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return new HttpEntity<>(body, headers);
	}

	public void downloadLatestValidationAsSpreadsheet(CodeSystem codeSystem, SnowstormClient snowstormClient,
													  ValidationReport validationReport, OutputStream outputStream) throws ServiceException {

		try (Workbook validationReportSpreadsheet = spreadsheetService.createValidationReportSpreadsheet(validationReport)) {
			validationReportSpreadsheet.write(outputStream);
		} catch (IOException e) {
			throw new ServiceException("Failed to write validation spreadsheet to API response stream.", e);
		}
	}

}
