package org.snomed.simplex.client.srs;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.SnowstormExportConfiguration;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemBuildStatus;
import org.snomed.simplex.client.domain.EditionStatus;
import org.snomed.simplex.client.srs.domain.OutputFile;
import org.snomed.simplex.client.srs.domain.ProductUpdateRequestInternal;
import org.snomed.simplex.client.srs.domain.SRSBuild;
import org.snomed.simplex.client.srs.domain.SRSProduct;
import org.snomed.simplex.client.srs.manifest.ReleaseManifestService;
import org.snomed.simplex.client.srs.manifest.domain.CreateBuildRequest;
import org.snomed.simplex.domain.PackageConfiguration;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.snomed.simplex.client.SnowstormClient.ExportType.DELTA;

@Service
public class ReleaseServiceClient {

	public static final String RELEASE_ASSERTION_GROUP_NAMES = "common-authoring,simplex-release";
	public static final String VALIDATION_ASSERTION_GROUP_NAMES = "common-authoring";
	public static final String DROOLS_RULES_GROUP_NAMES = "common-authoring";

	private final String releaseServiceURL;
	private final String releaseServiceUsername;
	private final String releaseServicePassword;

	private final String releaseCenter;
	private final String releaseCenterBranch;
	private final String releaseSource;
	private final String readmeHeaderTemplate;
	private final String licenceStatementTemplate;
	private final ReleaseManifestService releaseManifestService;
	private final SnowstormClientFactory snowstormClientFactory;
	private final AuthenticationClient authenticationClient;

	public static final boolean EDITION_PACKAGE = true;
	public static final String FIRST_SNOMEDCT_RELEASE_DATE = "2002-01-01";

	private static final Cache<String, RestTemplate> clientCache = CacheBuilder.newBuilder()
			.expireAfterWrite(5L, TimeUnit.MINUTES).build();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ReleaseServiceClient(
			@Value("${snomed-release-service.url}") String releaseServiceURL,
			@Value("${snomed-release-service.username}") String releaseServiceUsername,
			@Value("${snomed-release-service.password}") String releaseServicePassword,
			@Value("${snomed-release-service.simplex-release-center}") String releaseCenter,
			@Value("${snomed-release-service.simplex-release-center-branch}") String releaseCenterBranch,
			@Value("${snomed-release-service.source-name}") String releaseSource,
			@Value("${snomed-release-service.package.readme-header-template}") String readmeHeaderTemplate,
			@Value("${snomed-release-service.package.licence-statement-template}") String licenceStatementTemplate,
			@Autowired ReleaseManifestService releaseManifestService,
			@Autowired SnowstormClientFactory snowstormClientFactory,
			@Autowired AuthenticationClient authenticationClient) {

		this.releaseServiceURL = releaseServiceURL;
		this.releaseServiceUsername = releaseServiceUsername;
		this.releaseServicePassword = releaseServicePassword;
		this.releaseCenter = releaseCenter;
		this.releaseCenterBranch = releaseCenterBranch;
		this.releaseSource = releaseSource;
		this.readmeHeaderTemplate = readmeHeaderTemplate;
		this.licenceStatementTemplate = licenceStatementTemplate;
		this.releaseManifestService = releaseManifestService;
		this.snowstormClientFactory = snowstormClientFactory;
		this.authenticationClient = authenticationClient;
	}

	public SRSBuild getReleaseCompleteBuildOrThrow(CodeSystem codeSystem) throws ServiceException {
		if (codeSystem.getEditionStatus() != EditionStatus.RELEASE || codeSystem.getBuildStatus() != CodeSystemBuildStatus.COMPLETE) {
			throw new ServiceExceptionWithStatusCode("This function is only available when CodeSytem is in release mode " +
					"and the release candidate build is complete.", HttpStatus.CONFLICT);
		}

		String buildUrl = codeSystem.getLatestReleaseCandidateBuild();
		SRSBuild build = getBuild(buildUrl);
		String buildStatus = build.status();
		if (CodeSystemBuildStatus.fromSRSStatus(buildStatus) != CodeSystemBuildStatus.COMPLETE) {
			throw new ServiceExceptionWithStatusCode("The release candidate build is not yet complete.", HttpStatus.CONFLICT);
		}
		return build;
	}

	public SRSProduct getCreateProduct(CodeSystem codeSystem, PackageConfiguration packageConfiguration) throws ServiceException {
		SRSProduct product = getProduct(codeSystem);
		if (product == null) {
			callVoid("creating release product", client -> client.postForEntity(
					String.format("/centers/%s/products", releaseCenter),
					new CreateProductRequest(getProductName(codeSystem.getShortName())),
					Void.class));
		}
		return updateProductConfiguration(codeSystem, packageConfiguration);
	}

	private static String getThisYear() {
		return new GregorianCalendar().get(Calendar.YEAR) + "";
	}

	public SRSProduct updateProductConfiguration(
			CodeSystem codeSystem,
			PackageConfiguration packageConfiguration) throws ServiceException {

		String readmeHeader = createReadmeHeader(codeSystem.getName(), packageConfiguration);
		String licenceStatement = createLicenceStatement(codeSystem.getName(), packageConfiguration);

		ProductUpdateRequestInternal updateRequest = new ProductUpdateRequestInternal();

		boolean firstTimeRelease = codeSystem.getLatestVersion() == null;
		updateRequest.setFirstTimeRelease(firstTimeRelease);

		String dependencyPackage = codeSystem.getDependencyPackage();

		if (firstTimeRelease) {
			updateRequest.setPreviousEditionDependencyEffectiveDate(FIRST_SNOMEDCT_RELEASE_DATE);
			updateRequest.setPreviousPublishedPackage(null);
			updateRequest.setExtensionDependencyRelease(dependencyPackage);
		} else {
			updateRequest.setPreviousPublishedPackage(codeSystem.getPreviousPackage());
			Integer previousDependantVersionEffectiveTime = codeSystem.getLatestVersion().dependantVersionEffectiveTime();
			if (previousDependantVersionEffectiveTime == null) {
				throw new ServiceExceptionWithStatusCode("Previous %s release has missing metadata: dependantVersionEffectiveTime.".formatted(codeSystem.getShortName()),
					HttpStatus.CONFLICT);
			}
			updateRequest.setPreviousEditionDependencyEffectiveDate(toDateHyphenFormat(previousDependantVersionEffectiveTime.toString()));
			updateRequest.setExtensionDependencyRelease(dependencyPackage);
		}

		updateRequest.setReleaseAsAnEdition(EDITION_PACKAGE);
		updateRequest.setClassifyOutputFiles(false);// Edition package is too big. Classification has already happened anyway.

		updateRequest.setReadmeHeader(readmeHeader);
		updateRequest.setReadmeEndDate(getThisYear());
		updateRequest.setLicenseStatement(licenceStatement);
		updateRequest.setAdditionalReleaseInformationFields("{\"placeholder\": null}");// This JSON is the minimum requirement
		updateRequest.setAssertionGroupNames(RELEASE_ASSERTION_GROUP_NAMES);
		updateRequest.setEnableDrools(true);
		updateRequest.setDroolsRulesGroupNames(DROOLS_RULES_GROUP_NAMES);
		updateRequest.setEnableMRCMValidation(true);
		updateRequest.setNamespaceId(codeSystem.getNamespace());
		updateRequest.setDefaultModuleId(codeSystem.getDefaultModule());
		updateRequest.setModuleIds(codeSystem.getDefaultModule());

		callVoid("updating release product configuration", client -> client.put(
				String.format("/centers/%s/products/%s/configuration", releaseCenter, getProductName(codeSystem.getShortName())),
				updateRequest));
		return getProduct(codeSystem);
	}

	private String toDateHyphenFormat(String dateStamp) {
		return dateStamp.substring(0, 4) + "-" + dateStamp.substring(4, 6) + "-" + dateStamp.substring(6, 8);
	}

	public SRSProduct getProduct(CodeSystem codeSystem) throws ServiceException {
		try {
			return call("loading release product", client -> client.getForEntity(
					String.format("/centers/%s/products/%s", releaseCenter, getProductName(codeSystem.getShortName())),
					SRSProduct.class).getBody());
		} catch (HttpClientErrorException.NotFound e) {
			return null;
		}
	}

	public SRSBuild buildProduct(CodeSystem codeSystem, String effectiveTime) throws ServiceException {
		logger.info("Preparing build for {}", codeSystem.getShortName());
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		SRSBuild build = prepareReleaseBuild(codeSystem, effectiveTime, snowstormClient);
		logger.info("Build {} preparation complete", build.id());

		scheduleBuild(build, codeSystem);
		logger.info("Build {} scheduled to run. Url: {}", build.id(), build.url());
		return build;
	}

	private SRSBuild prepareReleaseBuild(CodeSystem codeSystem, String effectiveTime, SnowstormClient snowstormClient) throws ServiceException {
		logger.info("Generating manifest");

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		if (!effectiveTime.matches("\\d{8}")) {
			throw new ServiceExceptionWithStatusCode(
					format("Invalid package effective time '%s'. Expected format yyyyyMMdd, for example: %s", effectiveTime, dateFormat.format(new Date())), HttpStatus.BAD_REQUEST);
		}

		String productName = codeSystem.getName().replace("Extension", "").replace("Edition", "");
		String manifestXml = releaseManifestService.generateManifestXml(codeSystem, productName, effectiveTime, snowstormClient);
		uploadManifest(codeSystem, manifestXml);
		logger.info("Uploaded manifest");

		SRSBuild build = createBuild(codeSystem, effectiveTime);
		logger.info("Created build {}", build.id());
		try {
			File tempFile = Files.createTempFile(codeSystem.getShortName() + UUID.randomUUID(), ".zip").toFile();
			try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
				snowstormClient.exportRF2(outputStream, new SnowstormExportConfiguration(DELTA, codeSystem).setTransientEffectiveTime(effectiveTime));
			}
			logger.info("Exported RF2 delta");

			File deltaReleaseDirectory;
			ReleaseImporter releaseImporter = new ReleaseImporter();
			try (FileInputStream releaseZipStream = new FileInputStream(tempFile)) {
				deltaReleaseDirectory = releaseImporter.unzipRelease(releaseZipStream, ReleaseImporter.ImportType.DELTA);
				logger.info("Unzipped export");
			} catch (ReleaseImportException e) {
				throw new ServiceException(format("Failed to extract RF2 archive for code system %s during build preparation.", codeSystem.getShortName()), e);
			}

			try (Stream<Path> deltaPathStream = Files.find(deltaReleaseDirectory.toPath(), 10,
					(filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.toFile().getName().endsWith(".txt"))) {

				for (Path file : deltaPathStream.toList()) {
					uploadDeltaFile(file, build, codeSystem);
					logger.info("Uploaded {}", file.getFileName());
				}
			}
			logger.info("All input files uploaded");
			prepareInputFiles(build, codeSystem);
			logger.info("Input files prepared");

			return build;
		} catch (IOException e) {
			throw new ServiceException(format("Failed to export code system %s during build preparation.", codeSystem.getShortName()), e);
		}
	}

	private String createReadmeHeader(String name, PackageConfiguration packageConfiguration) throws ServiceExceptionWithStatusCode {
		String orgName = packageConfiguration.orgName();
		if (orgName == null) {
			throw new ServiceExceptionWithStatusCode("Organisation name must be set before package readme can be generated.", HttpStatus.CONFLICT);
		}
		String orgContactDetails = packageConfiguration.orgContactDetails();
		if (orgContactDetails == null) {
			throw new ServiceExceptionWithStatusCode("Organisation contact details must be set before package readme can be generated.", HttpStatus.CONFLICT);
		}
		return readmeHeaderTemplate
				.replace("{simplexProduct}", name)
				.replace("{simplexProductOrganisationName}", orgName)
				.replace("{simplexProductContactDetails}", orgContactDetails)
				.replace("{readmeEndDate}", getThisYear());
	}

	private String createLicenceStatement(String name, PackageConfiguration packageConfiguration) {
		return licenceStatementTemplate
				.replace("{simplexProduct}", name)
				.replace("{simplexProductOrganisationName}", packageConfiguration.orgName())
				.replace("{simplexProductContactDetails}", packageConfiguration.orgContactDetails())
				.replace("{readmeEndDate}", getThisYear());
	}

	public void uploadManifest(CodeSystem codeSystem, String manifestXml) throws ServiceException {
		File tempDirectory = null;
		File manifestFile = null;
		try {
			manifestXml = manifestXml.replace(" xmlns=\"\"", "");
			tempDirectory = Files.createTempDirectory(UUID.randomUUID().toString()).toFile();
			manifestFile = new File(tempDirectory, "manifest.xml");
			Files.writeString(manifestFile.toPath(), manifestXml, StandardCharsets.UTF_8);
			String url = format("/centers/%s/products/%s/manifest", releaseCenter, getProductName(codeSystem));
			HttpHeaders headers = getUploadHeaders();
			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			body.add("file", new FileSystemResource(manifestFile));
			callVoid("uploading release manifest", client -> client.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class));
		} catch (IOException e) {
			throw new ServiceException("Failed to serialise generated manifest for upload.", e);
		} finally {
			if (manifestFile != null) {
				try {
					Files.delete(manifestFile.toPath());
				} catch (IOException e) {
					logger.info("Failed to delete temp manifest file {}", manifestFile.getAbsolutePath());
				}
			}
			if (tempDirectory != null) {
				try {
					Files.delete(tempDirectory.toPath());
				} catch (IOException e) {
					logger.info("Failed to delete temp directory {}", tempDirectory.getAbsoluteFile());
				}
			}
		}
	}

	public void uploadDeltaFile(Path file, SRSBuild releaseBuild, CodeSystem codeSystem) throws ServiceException {
		String url = format("/centers/%s/products/%s/builds/%s/sourcefiles/%s",
				releaseCenter, getProductName(codeSystem), releaseBuild.id(), releaseSource);
		HttpHeaders headers = getUploadHeaders();
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new FileSystemResource(file));
		callVoid("uploading release delta file", client -> client.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class));
	}

	public void prepareInputFiles(SRSBuild releaseBuild, CodeSystem codeSystem) throws ServiceException {
		String url = format("/centers/%s/products/%s/builds/%s/inputfiles/prepare",
				releaseCenter, getProductName(codeSystem), releaseBuild.id());
		callVoid("preparing release build input files", client -> client.exchange(url, HttpMethod.POST, null, Void.class));
	}

	private static HttpHeaders getUploadHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		return headers;
	}

	private void scheduleBuild(SRSBuild build, CodeSystem codeSystem) throws ServiceException {
		String url = format("/centers/%s/products/%s/builds/%s/schedule",
				releaseCenter, getProductName(codeSystem), build.id());
		callVoid("scheduling release build", client -> client.postForEntity(url, null, Void.class));
	}

	public SRSBuild getBuild(String buildUrl) throws ServiceException {
		return call("loading release build status", client -> client.getForEntity(buildUrl, SRSBuild.class).getBody());
	}

	public SRSBuild createBuild(CodeSystem codeSystem, String effectiveTime) throws ServiceException {
		String productName = getProductName(codeSystem);
		String url = format("/centers/%s/products/%s/builds", releaseCenter, productName);
		CreateBuildRequest createBuildRequest = new CreateBuildRequest(effectiveTime, releaseCenterBranch);
		return call("creating release build", client -> client.exchange(url, HttpMethod.POST, new HttpEntity<>(createBuildRequest), SRSBuild.class).getBody());
	}

	public void publishBuild(SRSBuild build) throws ServiceException {
		String url = "%s/publish".formatted(build.url());
		callVoid("publishing release build", client -> client.exchange(url, HttpMethod.POST, null, Void.class));
	}

	public String getReleasePackageFilename(String buildUrl) throws ServiceException {
		String url = getReleaseCandidatePackageUrl(buildUrl);
		return url.substring(url.lastIndexOf("/") + 1);
	}

	public Pair<String, File> downloadReleaseCandidatePackage(String buildUrl) throws ServiceException {
		String url = getReleaseCandidatePackageUrl(buildUrl);

		String filename = url.substring(url.lastIndexOf("/") + 1);
		return call("downloading release candidate package", client -> client.execute(url, HttpMethod.GET,
				httpRequest -> httpRequest.getHeaders().add("Accept", "application/zip"), httpResponse -> {
					File tempFile = File.createTempFile("release-candidate-download" + UUID.randomUUID(), "tmp");
					try (InputStream inputStream = httpResponse.getBody()) {
						Streams.copy(inputStream, new FileOutputStream(tempFile), true);
					}
					return Pair.of(filename, tempFile);
				}));
	}

	public String getReleaseCandidatePackageUrl(String buildUrl) throws ServiceException {
		String outputFilesUrl = "%s/outputfiles".formatted(buildUrl);
		ParameterizedTypeReference<List<OutputFile>> responseType = new ParameterizedTypeReference<>() {};
		ResponseEntity<List<OutputFile>> outputFiles = call("listing release build output files",
				client -> client.exchange(outputFilesUrl, HttpMethod.GET, null, responseType));
		List<OutputFile> body = outputFiles.getBody();
		if (body == null) {
			throw new ServiceException("Failed to list download files for release build.");
		}
		for (OutputFile outputFile : body) {
			if (outputFile.getId().endsWith(".zip")) {
				return outputFile.getUrl();
			}
		}
		throw new ServiceException("No release package found in build output files.");
	}

	private String getProductName(CodeSystem codeSystem) {
		return getProductName(codeSystem.getShortName());
	}

	private String getProductName(String shortName) {
		return shortName.toLowerCase().replace("-", "").replace("_", "");
	}

	@FunctionalInterface
	private interface SrsRequest<T> {
		T run(RestTemplate client);
	}

	@FunctionalInterface
	private interface SrsVoidRequest {
		void run(RestTemplate client);
	}

	private <T> T call(String action, SrsRequest<T> request) throws ServiceException {
		RestTemplate client = getClient();
		try {
			return request.run(client);
		} catch (HttpClientErrorException.NotFound e) {
			throw e;
		} catch (HttpStatusCodeException e) {
			throw toReleaseServiceException(e, action);
		} catch (RestClientException e) {
			throw unavailableWhile(action, e);
		}
	}

	private void callVoid(String action, SrsVoidRequest request) throws ServiceException {
		call(action, client -> {
			request.run(client);
			return null;
		});
	}

	private ServiceExceptionWithStatusCode unavailableWhile(String action, Throwable cause) {
		return new ServiceExceptionWithStatusCode(
				format("Release Service is unavailable while %s. Check Release Service URL and Simplex credentials (snomed-release-service.*).", action),
				HttpStatus.SERVICE_UNAVAILABLE,
				cause);
	}

	private ServiceExceptionWithStatusCode toReleaseServiceException(HttpStatusCodeException e, String action) {
		int status = e.getStatusCode().value();
		String responseBody = e.getResponseBodyAsString(StandardCharsets.UTF_8);
		if (logger.isWarnEnabled()) {
			logger.warn("Release Service HTTP {} while {} | response: {}",
					status, action, sanitizeResponseBodyForLog(responseBody));
		}

		String accessHint = (status == 401 || status == 403)
				? " Access may be denied by Release Service or nginx; verify snomed-release-service.url, username, and password."
				: "";
		String message = format(
				"Release Service is unavailable while %s.%s Check Release Service URL and Simplex credentials (snomed-release-service.*).",
				action, accessHint);
		return new ServiceExceptionWithStatusCode(message, HttpStatus.SERVICE_UNAVAILABLE, e);
	}

	static String sanitizeResponseBodyForLog(String body) {
		if (body == null || body.isBlank()) {
			return "(empty)";
		}
		String trimmed = body.strip();
		if (trimmed.startsWith("<") && trimmed.toLowerCase(Locale.ROOT).contains("<html")) {
			return "(HTML error page)";
		}
		if (trimmed.length() > 500) {
			return trimmed.substring(0, 500) + "...";
		}
		return trimmed;
	}

	private RestTemplate getClient() throws ServiceException {
		try {
			// Cache SRS service account RestTemplate using username
			return clientCache.get(releaseServiceUsername, () -> {
				logger.info("Logging in with SRS service account \"{}\"", releaseServiceUsername);
				String authenticationToken = authenticationClient.fetchAuthenticationToken(releaseServiceUsername, releaseServicePassword);
				if (authenticationToken == null || authenticationToken.isEmpty()) {
					throw new ServiceExceptionWithStatusCode("Failed to authenticate with Release Service system user. " +
							"Unable to process request.", HttpStatus.FORBIDDEN);
				}

				MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
				converter.setObjectMapper(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
				return new RestTemplateBuilder()
						.rootUri(releaseServiceURL)
						.defaultHeader("Cookie", authenticationToken)
						.defaultHeader("Content-Type", "application/json")
						.messageConverters(List.of(converter, new AllEncompassingFormHttpMessageConverter()))
						.build();
			});
		} catch (ExecutionException e) {
			throw new ServiceException("Failed to create SRS client", e);
		}
	}

	private record CreateProductRequest(String name) {}

}
