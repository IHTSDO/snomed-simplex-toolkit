package org.snomed.simplex.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
		configurer.ignoreAcceptHeader(true);
		configurer.defaultContentType(MediaType.APPLICATION_JSON);
		configurer.defaultContentTypeStrategy(webRequest -> Collections.singletonList(MediaType.APPLICATION_JSON));
	}
}
