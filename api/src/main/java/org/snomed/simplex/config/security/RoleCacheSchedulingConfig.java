package org.snomed.simplex.config.security;

import org.snomed.simplex.service.SecurityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

@Configuration
public class RoleCacheSchedulingConfig implements SchedulingConfigurer {

	private final SecurityService securityService;
	private final Duration roleCacheTtl;

	public RoleCacheSchedulingConfig(SecurityService securityService, @Value("${ims-security.role-cache.ttl:1h}") Duration roleCacheTtl) {
		this.securityService = securityService;
		this.roleCacheTtl = roleCacheTtl;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.addFixedDelayTask(securityService::expireRoleCaches, roleCacheTtl);
	}
}
