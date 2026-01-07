package org.snomed.simplex.translation.domain;

/**
 * What the user intends to do with this term. Add, Remove or Nothing
 */
public record TermIntent(String term, Intent intent) {}
