package org.snomed.simplex.weblate;

import java.util.Date;

/**
 * Builder for constructing Weblate unit query parameters.
 */
public class UnitQueryBuilder {
    private final String projectSlug;
    private final String componentSlug;
    private String languageCode = "en";
    private String compositeLabel;
    private String state;
    private Date changedSince;
    private int pageSize = 100;
    private boolean fastestSort = true;

    public UnitQueryBuilder(String projectSlug, String componentSlug) {
        this.projectSlug = projectSlug;
        this.componentSlug = componentSlug;
    }

    public UnitQueryBuilder languageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    public UnitQueryBuilder compositeLabel(String compositeLabel) {
        this.compositeLabel = compositeLabel;
        return this;
    }

    public UnitQueryBuilder state(String state) {
        this.state = state;
        return this;
    }

    public UnitQueryBuilder pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public UnitQueryBuilder fastestSort(boolean fastestSort) {
        this.fastestSort = fastestSort;
        return this;
    }

    public UnitQueryBuilder changedSince(Date changedSince) {
        this.changedSince = changedSince;
        return this;
    }

    public String build() {
        StringBuilder query = new StringBuilder()
                .append("project").append(":").append(projectSlug)
                .append(" AND ")
                .append("component").append(":").append(componentSlug)
                .append(" AND ")
                .append("language").append(":").append(languageCode);

        if (compositeLabel != null) {
            query.append(" AND label:").append(compositeLabel);
        }

        if (state != null) {
            query.append(" AND state:").append(state);
        }

        if (changedSince != null) {
            // Format the timestamp for Weblate API - use ISO 8601 format
            String formattedTime = String.format("%tFT%<tT.%<tLZ", changedSince);
            query.append(" AND changed:>").append(formattedTime);
        }

        return "/units/?q=%s&page_size=%s%s&format=json".formatted(
                query,
                pageSize,
                fastestSort ? "&sort_by=id" : ""
        );
    }

    public static UnitQueryBuilder of(String projectSlug, String componentSlug) {
        return new UnitQueryBuilder(projectSlug, componentSlug);
    }
}
