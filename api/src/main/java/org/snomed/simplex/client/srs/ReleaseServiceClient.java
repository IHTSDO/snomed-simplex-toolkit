package org.snomed.simplex.client.srs;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.AuthenticationClient;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.srs.manifest.ReleaseManifestService;
import org.snomed.simplex.client.srs.manifest.domain.CreateBuildRequest;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseBuild;
import org.snomed.simplex.domain.PackageConfiguration;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.ihtsdo.sso.integration.SecurityUtil.getAuthenticationToken;

@Service
public class ReleaseServiceClient {

    public static final String ASSERTION_GROUP_NAMES = "common-authoring,simplex-release";
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
            .expireAfterAccess(5L, TimeUnit.MINUTES).build();

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

    public Product getCreateProduct(CodeSystem codeSystem, PackageConfiguration packageConfiguration) throws ServiceException {
        Product product = getProduct(codeSystem);
        if (product == null) {
            getClient().postForEntity(
                    String.format("/centers/%s/products", releaseCenter),
                    new CreateProductRequest(getProductName(codeSystem.getShortName())),
                    Void.class);
        }
		return updateProductConfiguration(codeSystem, packageConfiguration);
    }

    private static String getThisYear() {
		return new GregorianCalendar().get(Calendar.YEAR) + "";
    }

    public Product updateProductConfiguration(
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
            String previousDependencyPackage = codeSystem.getPreviousDependencyPackage();
            updateRequest.setPreviousEditionDependencyEffectiveDate(extractFilenameEffectiveDate(previousDependencyPackage));
            updateRequest.setExtensionDependencyRelease(dependencyPackage);
        }

        updateRequest.setReleaseAsAnEdition(EDITION_PACKAGE);
        updateRequest.setClassifyOutputFiles(false);// Edition package is too big. Classification has already happened anyway.

        updateRequest.setReadmeHeader(readmeHeader);
        updateRequest.setReadmeEndDate(getThisYear());
        updateRequest.setLicenseStatement(licenceStatement);
        updateRequest.setAssertionGroupNames(ASSERTION_GROUP_NAMES);
        updateRequest.setEnableDrools(true);
        updateRequest.setDroolsRulesGroupNames(DROOLS_RULES_GROUP_NAMES);
        updateRequest.setEnableMRCMValidation(true);
        updateRequest.setNamespaceId(codeSystem.getNamespace());
        updateRequest.setDefaultModuleId(codeSystem.getDefaultModule());
        updateRequest.setModuleIds(codeSystem.getModules().stream().map(ConceptMini::getConceptId).collect(Collectors.joining(",")));

        getClient().put(
                String.format("/centers/%s/products/%s/configuration", releaseCenter, getProductName(codeSystem.getShortName())),
                updateRequest);
        return getProduct(codeSystem);
    }

    private static String extractFilenameEffectiveDate(String dependencyPackage) throws ServiceExceptionWithStatusCode {
        // Example SnomedCT_InternationalRF2_PRODUCTION_20240101T120000Z.zip
        Pattern datePattern = Pattern.compile(".*_(\\d{8})[^_]*\\.zip");
        Matcher dateMatcher = datePattern.matcher(dependencyPackage);
        if (!dateMatcher.matches()) {
            throw new ServiceExceptionWithStatusCode("Failed to extract effective time from the filename of the dependency package.", HttpStatus.CONFLICT);
        }
        String group = dateMatcher.group(1);
		return group.substring(0, 4) + "-" + group.substring(4, 6) + "-" + group.substring(6, 8);
    }

    public Product getProduct(CodeSystem codeSystem) throws ServiceException {
        try {
            ResponseEntity<Product> forEntity = getClient().getForEntity(
                    String.format("/centers/%s/products/%s", releaseCenter, getProductName(codeSystem.getShortName())),
                    Product.class);
            return forEntity.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public ReleaseBuild buildProduct(CodeSystem codeSystem, String effectiveTime) throws ServiceException {
        logger.info("Preparing build for {}", codeSystem.getShortName());
        SnowstormClient snowstormClient = snowstormClientFactory.getClient();
        ReleaseBuild build = prepareReleaseBuild(codeSystem, effectiveTime, snowstormClient);
        logger.info("Build {} preparation complete", build.getId());

		scheduleBuild(build, codeSystem);
        logger.info("Build {} scheduled to run. Url: {}", build.getId(), build.getUrl());
        return build;
    }

    private ReleaseBuild prepareReleaseBuild(CodeSystem codeSystem, String effectiveTime, SnowstormClient snowstormClient) throws ServiceException {
        logger.info("Generating manifest");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        if (!effectiveTime.matches("[0-9]{8}")) {
            throw new ServiceExceptionWithStatusCode(
                    format("Invalid package effective time '%s'. Expected format yyyyyMMdd, for example: %s", effectiveTime, dateFormat.format(new Date())), HttpStatus.BAD_REQUEST);
        }

        String productName = codeSystem.getName().replace("Extension", "").replace("Edition", "");
        String manifestXml = releaseManifestService.generateManifestXml(codeSystem, productName, effectiveTime, snowstormClient);
        uploadManifest(codeSystem, manifestXml);
        logger.info("Uploaded manifest");

        ReleaseBuild build = createBuild(codeSystem, effectiveTime);
        logger.info("Created build {}", build.getId());
        try {
            File tempFile = Files.createTempFile(codeSystem.getShortName() + UUID.randomUUID(), ".zip").toFile();
            snowstormClient.exportRF2(new FileOutputStream(tempFile), "DELTA", codeSystem, effectiveTime);
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

    private String createReadmeHeader(String name, PackageConfiguration packageConfiguration) {
        return readmeHeaderTemplate
                .replace("{simplexProduct}", name)
                .replace("{simplexProductOrganisationName}", packageConfiguration.orgName())
                .replace("{simplexProductContactDetails}", packageConfiguration.orgContactDetails())
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
            getClient().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
        } catch (IOException e) {
            throw new ServiceException("Failed to serialise generated manifest for upload.", e);
        } finally {
            if (manifestFile != null) {
                manifestFile.delete();
            }
            if (tempDirectory != null) {
                tempDirectory.delete();
            }
        }
    }

    public void uploadDeltaFile(Path file, ReleaseBuild releaseBuild, CodeSystem codeSystem) throws ServiceException {
        String url = format("/centers/%s/products/%s/builds/%s/sourcefiles/%s",
                releaseCenter, getProductName(codeSystem), releaseBuild.getId(), releaseSource);
        HttpHeaders headers = getUploadHeaders();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        getClient().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
    }

    public void prepareInputFiles(ReleaseBuild releaseBuild, CodeSystem codeSystem) throws ServiceException {
        String url = format("/centers/%s/products/%s/builds/%s/inputfiles/prepare",
                releaseCenter, getProductName(codeSystem), releaseBuild.getId());
        getClient().exchange(url, HttpMethod.POST, null, Void.class);
    }

    private static HttpHeaders getUploadHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    public void scheduleBuild(ReleaseBuild build, CodeSystem codeSystem) throws ServiceException {
        String url = format("/centers/%s/products/%s/builds/%s/schedule",
                releaseCenter, getProductName(codeSystem), build.getId());
        getClient().postForEntity(url, null, Void.class);
    }

    public ReleaseBuild createBuild(CodeSystem codeSystem, String effectiveTime) throws ServiceException {
        String productName = getProductName(codeSystem);
        String url = format("/centers/%s/products/%s/builds", releaseCenter, productName);
        CreateBuildRequest createBuildRequest = new CreateBuildRequest(effectiveTime, releaseCenterBranch);
        ResponseEntity<ReleaseBuild> response = getClient().exchange(url, HttpMethod.POST, new HttpEntity<>(createBuildRequest), ReleaseBuild.class);
        return response.getBody();
    }

    private String getProductName(CodeSystem codeSystem) {
        return getProductName(codeSystem.getShortName());
    }

    private String getProductName(String shortName) {
        return shortName.toLowerCase().replace("-", "").replace("_", "");
    }

    private RestTemplate getClient() throws ServiceException {
        try {
            // Cache SRS service account RestTemplate using username
            return clientCache.get(releaseServiceUsername, () -> {
                String authenticationToken = authenticationClient.fetchAuthenticationToken(releaseServiceUsername, releaseServicePassword);
                if (authenticationToken == null || authenticationToken.isEmpty()) {
                    throw new ServiceExceptionWithStatusCode("Failed to authenticate with Release Service system user. " +
                            "Unable to process request.", 403);
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

    public static class Product {

        private String id;
        private String latestBuildStatus;
        private ProductBuildConfiguration buildConfiguration;
        private ProductBuildTestConfig qaTestConfig;

        public String getId() {
            return id;
        }

        public String getLatestBuildStatus() {
            return latestBuildStatus;
        }

        public ProductBuildConfiguration getBuildConfiguration() {
            return buildConfiguration;
        }

        public void setBuildConfiguration(ProductBuildConfiguration buildConfiguration) {
            this.buildConfiguration = buildConfiguration;
        }

        public ProductBuildTestConfig getQaTestConfig() {
            return qaTestConfig;
        }

        public void setQaTestConfig(ProductBuildTestConfig qaTestConfig) {
            this.qaTestConfig = qaTestConfig;
        }
    }

    public record ProductBuildConfiguration(
            String readmeHeader,
            String readmeEndDate,
            ProductExtensionConfiguration extensionConfig) {
    }

    public record ProductExtensionConfiguration(
            String namespaceId,
            String defaultModuleId,
            String moduleIds,
            String dependencyRelease,
            boolean releaseAsAnEdition) {
    }

    public static class ProductUpdateRequestInternal {

        // buildConfiguration
        private boolean firstTimeRelease;
        private String readmeHeader;
        private String readmeEndDate;
        private String licenseStatement;

        // buildConfiguration.extensionConfig
        private String namespaceId;
        private String defaultModuleId;
        private String moduleIds;
        private String previousPublishedPackage;
        private String extensionDependencyRelease;
        private String previousEditionDependencyEffectiveDate;
        private boolean releaseAsAnEdition;
        private boolean classifyOutputFiles;

        // qaTestConfig
        private String assertionGroupNames;
        private boolean enableDrools;
        private String droolsRulesGroupNames;
        private boolean enableMRCMValidation;

        public String getReadmeHeader() {
            return readmeHeader;
        }

        public void setReadmeHeader(String readmeHeader) {
            this.readmeHeader = readmeHeader;
        }

        public String getReadmeEndDate() {
            return readmeEndDate;
        }

        public void setReadmeEndDate(String readmeEndDate) {
            this.readmeEndDate = readmeEndDate;
        }

        public String getLicenseStatement() {
            return licenseStatement;
        }

        public void setLicenseStatement(String licenseStatement) {
            this.licenseStatement = licenseStatement;
        }

        public String getAssertionGroupNames() {
            return assertionGroupNames;
        }

        public void setAssertionGroupNames(String assertionGroupNames) {
            this.assertionGroupNames = assertionGroupNames;
        }

        public void setEnableDrools(boolean enableDrools) {
            this.enableDrools = enableDrools;
        }

        public boolean isEnableDrools() {
            return enableDrools;
        }

        public void setDroolsRulesGroupNames(String droolsRulesGroupNames) {
            this.droolsRulesGroupNames = droolsRulesGroupNames;
        }

        public String getDroolsRulesGroupNames() {
            return droolsRulesGroupNames;
        }

        public void setEnableMRCMValidation(boolean enableMRCMValidation) {
            this.enableMRCMValidation = enableMRCMValidation;
        }

        public boolean isEnableMRCMValidation() {
            return enableMRCMValidation;
        }

        public void setNamespaceId(String namespaceId) {
            this.namespaceId = namespaceId;
        }

        public String getNamespaceId() {
            return namespaceId;
        }

        public void setDefaultModuleId(String defaultModuleId) {
            this.defaultModuleId = defaultModuleId;
        }

        public String getDefaultModuleId() {
            return defaultModuleId;
        }

        public void setModuleIds(String moduleIds) {
            this.moduleIds = moduleIds;
        }

        public String getModuleIds() {
            return moduleIds;
        }

        public void setExtensionDependencyRelease(String extensionDependencyRelease) {
            this.extensionDependencyRelease = extensionDependencyRelease;
        }

        public String getExtensionDependencyRelease() {
            return extensionDependencyRelease;
        }

        public String getPreviousEditionDependencyEffectiveDate() {
            return previousEditionDependencyEffectiveDate;
        }

        public void setPreviousEditionDependencyEffectiveDate(String previousEditionDependencyEffectiveDate) {
            this.previousEditionDependencyEffectiveDate = previousEditionDependencyEffectiveDate;
        }

        public void setReleaseAsAnEdition(boolean releaseAsAnEdition) {
            this.releaseAsAnEdition = releaseAsAnEdition;
        }

        @JsonGetter("releaseExtensionAsAnEdition")
        public boolean isReleaseAsAnEdition() {
            return releaseAsAnEdition;
        }

        public void setFirstTimeRelease(boolean firstTimeRelease) {
            this.firstTimeRelease = firstTimeRelease;
        }

        public boolean isFirstTimeRelease() {
            return firstTimeRelease;
        }

        public boolean isClassifyOutputFiles() {
            return classifyOutputFiles;
        }

        public void setClassifyOutputFiles(boolean classifyOutputFiles) {
            this.classifyOutputFiles = classifyOutputFiles;
        }

        public void setPreviousPublishedPackage(String previousPublishedPackage) {
            this.previousPublishedPackage = previousPublishedPackage;
        }

        public String getPreviousPublishedPackage() {
            return previousPublishedPackage;
        }
    }
    public record ProductBuildTestConfig(
            String assertionGroupNames,
            boolean enableDrools,
            String droolsRulesGroupNames,
            boolean enableMRCMValidation) {}

    private record CreateProductRequest(String name) {}

}
