package org.snomed.simplex.client.domain;

public record CodeSystemVersion(Integer effectiveDate, String version, String releasePackage, String branchPath) {
}
