package org.snomed.simplex.rest.pojos;

public record CodeSystemUpgradeRequest(int newDependantVersion, boolean contentAutomations) {
}
