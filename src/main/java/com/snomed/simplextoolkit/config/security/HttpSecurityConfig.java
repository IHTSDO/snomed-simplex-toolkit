package com.snomed.simplextoolkit.config.security;

import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class HttpSecurityConfig {

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Value("${ims-security.required-role}")
	private String requiredRole;

	private final String[] excludedUrlPatterns = {
			"/",
			"/*",
			"/assets/*",
			"/api/version",
			"/api/ui-configuration",
			"/api/",
			"/swagger-ui.html",
			"/swagger-ui/**",
			"/v3/api-docs/**"
	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		HttpSecurity httpSecurity = http
				.csrf(CsrfConfigurer::disable);// lgtm [java/spring-disabled-csrf-protection]

		if (rolesEnabled) {
			http.addFilterBefore(new RequestHeaderAuthenticationDecorator(), AuthorizationFilter.class);
			http.addFilterAt(new RequiredRoleFilter(requiredRole, excludedUrlPatterns), AuthorizationFilter.class);

			for (String pattern : excludedUrlPatterns) {
				http.authorizeHttpRequests(auth -> auth.requestMatchers(new AntPathRequestMatcher(pattern)).permitAll());
			}
			http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
					.exceptionHandling(ah -> ah.accessDeniedHandler(new AccessDeniedExceptionHandler()))
					.httpBasic(withDefaults());
		}

		http.httpBasic(HttpBasicConfigurer::disable)
				.formLogin(FormLoginConfigurer::disable);

		return httpSecurity.build();
	}

}
