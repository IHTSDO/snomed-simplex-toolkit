package org.snomed.simplex.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SnowstormUserAgentSupportTest {

	@Test
	void resolveSubstitutesVersionFromBuildProperties() {
		BuildProperties buildProperties = buildProperties("2.13.0");

		assertEquals("Simplex v2.13.0", SnowstormUserAgentSupport.resolve("Simplex v{version}", buildProperties));
	}

	@Test
	void resolveUsesDevelopmentWhenBuildPropertiesAbsent() {
		assertEquals("Simplex vdevelopment", SnowstormUserAgentSupport.resolve("Simplex v{version}", null));
	}

	@Test
	void resolveReturnsTemplateUnchangedWhenNoPlaceholder() {
		BuildProperties buildProperties = buildProperties("2.13.0");

		assertEquals("Custom Agent", SnowstormUserAgentSupport.resolve("Custom Agent", buildProperties));
	}

	@Test
	void resolveReturnsNullForBlankTemplate() {
		BuildProperties buildProperties = buildProperties("2.13.0");

		assertNull(SnowstormUserAgentSupport.resolve("", buildProperties));
		assertNull(SnowstormUserAgentSupport.resolve("   ", buildProperties));
		assertNull(SnowstormUserAgentSupport.resolve(null, buildProperties));
	}

	private BuildProperties buildProperties(String version) {
		Properties properties = new Properties();
		properties.setProperty("version", version);
		return new BuildProperties(properties);
	}

}
