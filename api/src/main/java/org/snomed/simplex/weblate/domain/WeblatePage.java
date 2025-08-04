package org.snomed.simplex.weblate.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record WeblatePage<T>(int count, @JsonIgnore String next, String previous, List<T> results) {

}
