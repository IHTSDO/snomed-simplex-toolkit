package org.snomed.simplex.config;

/**
 * Controls where Translation Studio promotes content to.
 * <p>
 * Configured via {@code simplex.mode}. Spring fails application startup immediately
 * if the configured value does not match one of these constants.
 */
public enum SimplexMode {

	/** Promote directly to the code system's working branch. */
	SIMPLEX,

	/** Promote to a translation task branch managed by the SI Authoring Platform (Managed Service). */
	AUTHORING_PLATFORM

}
