package org.snomed.simplex.client.srs.domain;

public class SRSProduct {

	private String id;
	private String latestBuildStatus;
	private ProductBuildConfiguration buildConfiguration;
	private org.snomed.simplex.domain.Product.ProductBuildTestConfig qaTestConfig;

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

	public org.snomed.simplex.domain.Product.ProductBuildTestConfig getQaTestConfig() {
		return qaTestConfig;
	}

	public void setQaTestConfig(org.snomed.simplex.domain.Product.ProductBuildTestConfig qaTestConfig) {
		this.qaTestConfig = qaTestConfig;
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
}
