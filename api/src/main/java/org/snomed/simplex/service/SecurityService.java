package org.snomed.simplex.service;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SecurityService {

	@Value("${ims-security.roles.enabled}")
	private boolean rolesEnabled;

	@Value("${ims-security.required-role}")
	private String userGroup;

	@Value("${permission.admin.group}")
	private String adminGroup;

	private final SnowstormClientFactory snowstormClientFactory;

	private final Map<String, Map<String, Set<String>>> userCodesystemRoleCache;

	public SecurityService(SnowstormClientFactory snowstormClientFactory) {
		userCodesystemRoleCache = new HashMap<>();
		this.snowstormClientFactory = snowstormClientFactory;
	}

	public synchronized void updateUserRolePermissionCache(List<CodeSystem> codeSystems) {
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication authentication = context.getAuthentication();
		Map<String, Set<String>> codesystemRoleCache = userCodesystemRoleCache.computeIfAbsent((String) authentication.getPrincipal(), i -> new HashMap<>());
		for (CodeSystem codeSystem : codeSystems) {
			codesystemRoleCache.put(codeSystem.getShortName(), codeSystem.getUserRoles());
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
		if (!userCodesystemRoleCache.containsKey(principal)) {
			try {
				SnowstormClient client = snowstormClientFactory.getClient();
				CodeSystem codeSystem = client.getCodeSystemOrThrow(codesystem);
				updateUserRolePermissionCache(Collections.singletonList(codeSystem));
			} catch (ServiceException e) {
				// Framework prevents throwing this up. Just return false.
				return false;
			}
		}
		return userCodesystemRoleCache.getOrDefault(principal, Collections.emptyMap())
				.getOrDefault(codesystem, Collections.emptySet()).contains(role);
	}
}
