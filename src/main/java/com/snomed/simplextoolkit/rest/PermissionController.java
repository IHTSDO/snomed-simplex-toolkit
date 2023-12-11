package com.snomed.simplextoolkit.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static com.snomed.simplextoolkit.util.CollectionUtils.orEmpty;

@RestController
@Tag(name = "User Authorisation", description = "-")
@RequestMapping("api/auth")
public class PermissionController {

	@Value("${ims-security.required-role}")
	private String userGroup;

	@Value("${permission.admin.group}")
	private String adminGroup;

	@GetMapping
	public List<String> getUserRoles() {
		SecurityContext context = SecurityContextHolder.getContext();
		Authentication authentication = context.getAuthentication();
		List<String> roles = new ArrayList<>();
		if (authentication != null) {
			for (GrantedAuthority grantedAuthority : orEmpty(authentication.getAuthorities())) {
				String authority = grantedAuthority.getAuthority();
				if (authority.equals(userGroup)) {
					roles.add("USER");
				}
				if (authority.equals(adminGroup)) {
					roles.add("ADMIN");
				}
			}
		}
		return roles;
	}

}
