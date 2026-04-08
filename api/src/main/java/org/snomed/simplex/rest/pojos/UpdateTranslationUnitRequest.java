package org.snomed.simplex.rest.pojos;

import java.util.List;

public record UpdateTranslationUnitRequest(List<String> terms, String status) {
}
