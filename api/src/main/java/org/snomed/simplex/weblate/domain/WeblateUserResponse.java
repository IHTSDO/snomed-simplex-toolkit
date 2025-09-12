package org.snomed.simplex.weblate.domain;

import java.util.List;

public class WeblateUserResponse extends WeblateUser {
    private List<String> groups;
    private List<String> languages;

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

}
