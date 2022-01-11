package com.snomed.derivativemanagementtool.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;


@Configuration
public class SwaggerConfig {

	@Autowired(required = false)
	private BuildProperties buildProperties;

	@Bean
	public Docket swagger() {
		Docket docket = new Docket(DocumentationType.SWAGGER_2);
		final String version = buildProperties != null ? buildProperties.getVersion() : "DEV";
		docket.apiInfo(new ApiInfo("SNOMED CT Extension Maintenance Toolkit", "", version, null,
				new Contact("SNOMED International, Implementation Support Team", "http://snomed.org/support", "csrm@ihtsdo.org"),
				"Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0"));
		ApiSelectorBuilder apiSelectorBuilder = docket.select();

		apiSelectorBuilder
				.apis(RequestHandlerSelectors.any());

		// Don't show the error or root endpoints in swagger
		apiSelectorBuilder
				.paths(not(regex("/error")))
				.paths(not(regex("/")));

		return apiSelectorBuilder.build();
	}

}
