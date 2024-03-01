package org.snomed.simplex.service.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ValidationFix {

    private final String id;
    private final String type;
    private final String subtype;
    private final List<FixComponent> components;

    public ValidationFix(String id) {
        this.id = id;
        String[] split = id.split("\\.", 2);
        type = split[0];
        subtype = split[1];
        components = new ArrayList<>();
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

    public List<FixComponent> getComponents() {
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
