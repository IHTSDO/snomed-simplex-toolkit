package org.snomed.simplex.service.validation;

import java.util.Objects;

public record FixComponent(String conceptId, String componentId, String assertionText) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixComponent that = (FixComponent) o;
        return Objects.equals(conceptId, that.conceptId) && Objects.equals(componentId, that.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conceptId, componentId);
    }
}
