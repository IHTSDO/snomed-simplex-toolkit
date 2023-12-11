package com.snomed.simplextoolkit.domain.security;

import java.util.Collections;
import java.util.Set;

public class UserCodeSystemRoles {

	private final Set<String> grantedGlobalRole;
	private final Set<String> grantedBranchRole;

	public UserCodeSystemRoles() {
		grantedGlobalRole = Collections.emptySet();
		grantedBranchRole = Collections.emptySet();
	}

	public UserCodeSystemRoles(Set<String> grantedGlobalRole, Set<String> grantedBranchRole) {
		this.grantedGlobalRole = Collections.unmodifiableSet(grantedGlobalRole);
		this.grantedBranchRole = Collections.unmodifiableSet(grantedBranchRole);
	}

	public Set<String> getGrantedGlobalRole() {
		return grantedGlobalRole;
	}

	public Set<String> getGrantedBranchRole() {
		return grantedBranchRole;
	}

}
