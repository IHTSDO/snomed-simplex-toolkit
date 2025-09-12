package org.snomed.simplex.weblate.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WeblateGroup {
    private int id;
    private String name;
    @JsonProperty("defining_project")
    private String definingProject;
    @JsonProperty("project_selection")
    private int projectSelection;
    @JsonProperty("language_selection")
    private int languageSelection;
    private String url;
    private List<String> roles;
    private List<String> languages;
    private List<String> projects;
    private List<String> componentlists;
    private List<String> components;
    @JsonProperty("enforced_2fa")
    private boolean enforced2fa;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefiningProject() {
        return definingProject;
    }

    public void setDefiningProject(String definingProject) {
        this.definingProject = definingProject;
    }

    public int getProjectSelection() {
        return projectSelection;
    }

    public void setProjectSelection(int projectSelection) {
        this.projectSelection = projectSelection;
    }

    public int getLanguageSelection() {
        return languageSelection;
    }

    public void setLanguageSelection(int languageSelection) {
        this.languageSelection = languageSelection;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects;
    }

    public List<String> getComponentlists() {
        return componentlists;
    }

    public void setComponentlists(List<String> componentlists) {
        this.componentlists = componentlists;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public boolean isEnforced2fa() {
        return enforced2fa;
    }

    public void setEnforced2fa(boolean enforced2fa) {
        this.enforced2fa = enforced2fa;
    }
}
