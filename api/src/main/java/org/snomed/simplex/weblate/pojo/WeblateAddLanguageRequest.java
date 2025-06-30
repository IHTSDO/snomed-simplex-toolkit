package org.snomed.simplex.weblate.pojo;

public record WeblateAddLanguageRequest(String code, String name, String direction, int population, WeblateAddLanguageRequestPlural plural) {
}
