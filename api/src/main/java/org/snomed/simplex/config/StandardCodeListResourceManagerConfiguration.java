package org.snomed.simplex.config;

import org.ihtsdo.otf.resourcemanager.ResourceConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("standard-code-lists.storage")
public class StandardCodeListResourceManagerConfiguration extends ResourceConfiguration {
}
