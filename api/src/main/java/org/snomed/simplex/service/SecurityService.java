package org.snomed.simplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SecurityService {

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Value("${ims-security.required-role}")
	private String userGroup;

	@Value("${permission.admin.group}")
	private String adminGroup;

	@Value("${ims-security.role-cache.ttl:1h}")
	private Duration roleCacheTtl;

	private final SnowstormClientFactory snowstormClientFactory;

	private final Map<String, Map<String, CachedCodeSystemRoles>> userCodesystemRoleCache;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SecurityService(SnowstormClientFactory snowstormClientFactory) {
		userCodesystemRoleCache = new HashMap<>();
		this.snowstormClientFactory = snowstormClientFactory;
	}

	public synchronized void updateUserRolePermissionCache(List<CodeSystem> codeSystems) {
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication authentication = context.getAuthentication();
		Map<String, CachedCodeSystemRoles> codesystemRoleCache = userCodesystemRoleCache.computeIfAbsent(
				(String) authentication.getPrincipal(), i -> new HashMap<>());
		long expiresAt = System.currentTimeMillis() + roleCacheTtl.toMillis();
		for (CodeSystem codeSystem : codeSystems) {
			codesystemRoleCache.put(codeSystem.getShortName(),
					new CachedCodeSystemRoles(codeSystem.getUserRoles(), expiresAt));
		}
	}

	public boolean isApplicationUser(Authentication authentication) {
		return !rolesEnabled ||
				authentication.getAuthorities().stream().anyMatch(group -> group.getAuthority().equals(userGroup));
	}

	public boolean isApplicationAdmin(Authentication authentication) {
		return !rolesEnabled ||
				authentication.getAuthorities().stream().anyMatch(group -> group.getAuthority().equals(adminGroup));
	}

	public boolean hasPermission(Authentication authentication, String role, String codesystem) {
		if (!rolesEnabled) {
			return true;
		}

		String principal = (String) authentication.getPrincipal();
		if (isCacheMissOrExpired(principal, codesystem)) {
			try {
				SnowstormClient client = snowstormClientFactory.getClient();
				CodeSystem codeSystem = client.getCodeSystemOrThrow(codesystem);
				updateUserRolePermissionCache(Collections.singletonList(codeSystem));
			} catch (ServiceException e) {
				// Framework prevents throwing this up.
				Throwable cause = e.getCause();
				if (cause instanceof HttpServerErrorException.BadGateway) {
					logger.error("Permission check failed because Simplex got a BadGatewayException connecting to Snowstorm: {}", cause.getMessage(), cause);
				} else {
					logger.debug("hasPermission = false because of exception: {}", e.getMessage(), e);
				}
				return false;
			}
		}
		Set<String> singleCodesystemRoles = getCachedRoles(principal, codesystem);
		boolean userHasRole = singleCodesystemRoles.contains(role);
		if (!userHasRole) {
			logger.info("User {} does not have required role {} on codesystem {}", principal, role, codesystem);
		}
		return userHasRole;
	}

	private boolean isCacheMissOrExpired(String principal, String codesystem) {
		Map<String, CachedCodeSystemRoles> codesystemRoleCache = userCodesystemRoleCache.get(principal);
		if (codesystemRoleCache == null) {
			return true;
		}
		CachedCodeSystemRoles cached = codesystemRoleCache.get(codesystem);
		return cached == null || cached.isExpired();
	}

	private Set<String> getCachedRoles(String principal, String codesystem) {
		Map<String, CachedCodeSystemRoles> codesystemRoleCache = userCodesystemRoleCache.getOrDefault(principal, Collections.emptyMap());
		CachedCodeSystemRoles cached = codesystemRoleCache.get(codesystem);
		return cached != null ? cached.roles() : Collections.emptySet();
	}

	private record CachedCodeSystemRoles(Set<String> roles, long expiresAtEpochMs) {
		boolean isExpired() {
			return System.currentTimeMillis() >= expiresAtEpochMs;
		}
	}
}
