package org.snomed.simplex.service.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class ValidationFix {

    private final String id;
    private final String type;
    private final String subtype;
    private final Set<FixComponent> components;

    public ValidationFix(String id) {
        this.id = id;
        String[] split = id.split("\\.", 2);
        type = split[0];
        subtype = split[1];
        components = new LinkedHashSet<>();
    }

    @JsonIgnore
    public boolean isAutomatic() {
        return "automatic-fix".equals(type);
    }

    public void addComponent(FixComponent fixComponent) {
        components.add(fixComponent);
    }

    public String getType() {
        return type;
    }

    public String getSubtype() {
        return subtype;
    }

    public Set<FixComponent> getComponents() {
        return components;
    }

    public int getComponentCount() {
        return components.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationFix that = (ValidationFix) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
