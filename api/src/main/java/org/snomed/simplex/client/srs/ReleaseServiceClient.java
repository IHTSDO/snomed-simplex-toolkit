package org.snomed.simplex.client.srs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.ihtsdo.sso.integration.SecurityUtil.getAuthenticationToken;

@Service
public class ReleaseServiceClient {

    private static final Cache<String, RestTemplate> clientCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5L, TimeUnit.MINUTES).build();

    private final String srsUrl;
    private final String simplexReleaseCenter;

    public ReleaseServiceClient(@Value("${srs.url}") String srsUrl,
                                @Value("${srs.simplex.release-center}") String simplexReleaseCenter) {

        this.srsUrl = srsUrl;
        this.simplexReleaseCenter = simplexReleaseCenter;
    }

    public Product getCreateProduct(CodeSystem codeSystem) throws ServiceException {
        Product product = getProduct(codeSystem);
        if (product == null) {
            createProduct(codeSystem);
            int year = new GregorianCalendar().get(Calendar.YEAR);
            product = updateProductConfiguration(codeSystem, new ProductUpdateRequest("", String.valueOf(year)));
        }
        return product;
    }

    public Product updateProductConfiguration(
            CodeSystem codeSystem,
            ProductUpdateRequest productUpdateRequest) throws ServiceException {

        ProductUpdateRequestInternal updateRequest = new ProductUpdateRequestInternal(productUpdateRequest);
        updateRequest.setAssertionGroupNames("common-authoring");
        updateRequest.setEnableDrools(true);
        updateRequest.setDroolsRulesGroupNames("common-authoring");
        updateRequest.setEnableMRCMValidation(true);
        updateRequest.setNamespaceId(codeSystem.getNamespace());
        updateRequest.setDefaultModuleId(codeSystem.getDefaultModule());
        updateRequest.setModuleIds(codeSystem.getDefaultModule());
        updateRequest.setExtensionDependencyRelease(codeSystem.getDependencyPackage());
        updateRequest.setReleaseAsAnEdition(false);
        updateRequest.setFirstTimeRelease(codeSystem.getLatestVersion() == null);

        getRestTemplate().put(
                String.format("/centers/%s/products/%s/configuration", simplexReleaseCenter, toSRSProductName(codeSystem.getShortName())),
                updateRequest);
        return getProduct(codeSystem);
    }

    private void createProduct(CodeSystem codeSystem) throws ServiceException {
        getRestTemplate().postForEntity(
                String.format("/centers/%s/products", simplexReleaseCenter),
                new CreateProductRequest(toSRSProductName(codeSystem.getShortName())),
                Void.class);
    }

    public Product getProduct(CodeSystem codeSystem) throws ServiceException {
        try {
            ResponseEntity<Product> forEntity = getRestTemplate().getForEntity(
                    String.format("/centers/%s/products/%s", simplexReleaseCenter, toSRSProductName(codeSystem.getShortName())),
                    Product.class);
            return forEntity.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private RestTemplate getRestTemplate() throws ServiceException {
        String authenticationToken = getAuthenticationToken();
        if (authenticationToken == null || authenticationToken.isEmpty()) {
            throw new ServiceExceptionWithStatusCode("Authentication token is missing. Unable to process request.", 403);
        }

        try {
            return clientCache.get(authenticationToken, () -> new RestTemplateBuilder()
                    .rootUri(srsUrl)
                    .defaultHeader("Cookie", authenticationToken)
                    .build());
        } catch (ExecutionException e) {
            throw new ServiceException("Failed to create SRS client", e);
        }
    }

    private String toSRSProductName(String shortName) {
        return shortName.toLowerCase().replace("-", "").replace("_", "");
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

    public record ProductUpdateRequest(
            String readmeHeader,
            String readmeEndDate) {
    }

    public static class ProductUpdateRequestInternal {

        // buildConfiguration
        private boolean firstTimeRelease;
        private final ProductUpdateRequest userRequest;

        // buildConfiguration.extensionConfig
        private String namespaceId;
        private String defaultModuleId;
        private String moduleIds;
        private String extensionDependencyRelease;
        private boolean releaseAsAnEdition;

        // qaTestConfig
        private String assertionGroupNames;
        private boolean enableDrools;
        private String droolsRulesGroupNames;
        private boolean enableMRCMValidation;

        public ProductUpdateRequestInternal(ProductUpdateRequest userRequest) {
            this.userRequest = userRequest;
        }

        @JsonUnwrapped
        public ProductUpdateRequest getUserRequest() {
            return userRequest;
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

        public void setReleaseAsAnEdition(boolean releaseAsAnEdition) {
            this.releaseAsAnEdition = releaseAsAnEdition;
        }

        public boolean isReleaseAsAnEdition() {
            return releaseAsAnEdition;
        }

        public void setFirstTimeRelease(boolean firstTimeRelease) {
            this.firstTimeRelease = firstTimeRelease;
        }

        public boolean isFirstTimeRelease() {
            return firstTimeRelease;
        }
    }
    public record ProductBuildTestConfig(
            String assertionGroupNames,
            boolean enableDrools,
            String droolsRulesGroupNames,
            boolean enableMRCMValidation) {}

    private record CreateProductRequest(String name) {}

}
