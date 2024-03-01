package org.snomed.simplex.service.validation;

import java.util.Objects;

public class FixComponent {

    private String subsetId;
    private String mapId;
    private String translationId;

    private final String conceptId;
    private final String componentId;
    private final String assertionText;

    public FixComponent(String conceptId, String componentId, String assertionText) {
        this.conceptId = conceptId;
        this.componentId = componentId;
        this.assertionText = assertionText;
    }

    public String getSubsetId() {
        return subsetId;
    }

    public void setSubsetId(String subsetId) {
        this.subsetId = subsetId;
    }

    public String getMapId() {
        return mapId;
    }

    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

    public String getTranslationId() {
        return translationId;
    }

    public void setTranslationId(String translationId) {
        this.translationId = translationId;
    }

    public String getConceptId() {
        return conceptId;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getAssertionText() {
        return assertionText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixComponent that = (FixComponent) o;
        return Objects.equals(subsetId, that.subsetId) && Objects.equals(mapId, that.mapId) && Objects.equals(translationId, that.translationId) && Objects.equals(conceptId, that.conceptId) && Objects.equals(componentId, that.componentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subsetId, mapId, translationId, conceptId, componentId);
    }
}
