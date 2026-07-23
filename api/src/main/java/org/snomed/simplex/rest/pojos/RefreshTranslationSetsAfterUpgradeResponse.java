package org.snomed.simplex.rest.pojos;

import org.snomed.simplex.snolate.sets.SnolateTranslationSet;

import java.util.List;

public record RefreshTranslationSetsAfterUpgradeResponse(int queued, int skipped, List<SnolateTranslationSet> sets) {
}
