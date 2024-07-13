package org.snomed.simplex.config.security;

import org.snomed.simplex.service.SecurityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {

	private final SecurityService securityService;

	public MethodSecurityConfig(SecurityService securityService) {
		this.securityService = securityService;
	}

	@Bean
	static MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator) {
		DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
		handler.setPermissionEvaluator(permissionEvaluator);
		return handler;
	}

	@Bean
	public PermissionEvaluator permissionEvaluator() {
		return new PermissionEvaluator() {
			@Override
			public boolean hasPermission(Authentication authentication, Object permission, Object targetDomainObject) {
				if ("".equals(targetDomainObject) && permission.equals("USER")) {
					return securityService.isApplicationUser(authentication);
				}
				if ("".equals(targetDomainObject) && permission.equals("ADMIN")) {
					return securityService.isApplicationAdmin(authentication);
				}
				return securityService.hasPermission(authentication, (String) permission, (String) targetDomainObject);
			}

			@Override
			public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
				return false;
			}
		};
	}

}
