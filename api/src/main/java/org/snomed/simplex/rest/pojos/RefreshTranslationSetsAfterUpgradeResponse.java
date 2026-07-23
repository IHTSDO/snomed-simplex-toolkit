package org.snomed.simplex.rest.pojos;

import org.snomed.simplex.weblate.domain.WeblateTranslationSet;

import java.util.List;

public record RefreshTranslationSetsAfterUpgradeResponse(
		int queued,
		int skipped,
		List<WeblateTranslationSet> sets
) {
}
