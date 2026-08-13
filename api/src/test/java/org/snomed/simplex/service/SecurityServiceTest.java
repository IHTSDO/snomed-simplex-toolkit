package org.snomed.simplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

	@Mock
	private SnowstormClientFactory snowstormClientFactory;

	@Mock
	private SnowstormClient snowstormClient;

	@Mock
	private CodeSystem codeSystem;

	private SecurityService securityService;

	@BeforeEach
	void setUp() {
		securityService = new SecurityService(snowstormClientFactory);
		ReflectionTestUtils.setField(securityService, "rolesEnabled", true);
		ReflectionTestUtils.setField(securityService, "roleCacheTtl", Duration.ofHours(1));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("author-user", "token"));
	}

	@Test
	void hasPermissionUsesCachedRolesWithinTtl() throws ServiceException {
		when(codeSystem.getShortName()).thenReturn("SNOMEDCT-TEST");
		when(codeSystem.getUserRoles()).thenReturn(Set.of("AUTHOR"));
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken("author-user", "token");

		assertTrue(securityService.hasPermission(authentication, "AUTHOR", "SNOMEDCT-TEST"));
		assertTrue(securityService.hasPermission(authentication, "AUTHOR", "SNOMEDCT-TEST"));
		verify(snowstormClient, times(1)).getCodeSystemOrThrow("SNOMEDCT-TEST");
	}

	@Test
	void hasPermissionRefreshesFromSnowstormWhenCacheEntryExpired() throws ServiceException {
		ReflectionTestUtils.setField(securityService, "roleCacheTtl", Duration.ZERO);
		when(codeSystem.getShortName()).thenReturn("SNOMEDCT-TEST");
		when(codeSystem.getUserRoles()).thenReturn(Set.of("AUTHOR"));
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken("author-user", "token");

		assertTrue(securityService.hasPermission(authentication, "AUTHOR", "SNOMEDCT-TEST"));
		assertTrue(securityService.hasPermission(authentication, "AUTHOR", "SNOMEDCT-TEST"));
		verify(snowstormClient, times(2)).getCodeSystemOrThrow("SNOMEDCT-TEST");
	}

	@Test
	void hasPermissionReturnsFalseWhenSnowstormReportsNoRoles() throws ServiceException {
		when(codeSystem.getShortName()).thenReturn("SNOMEDCT-TEST");
		when(codeSystem.getUserRoles()).thenReturn(Set.of());
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken("author-user", "token");

		assertFalse(securityService.hasPermission(authentication, "AUTHOR", "SNOMEDCT-TEST"));
	}
}
