package org.snomed.simplex.rest.pojos;

import org.snomed.simplex.snolate.sets.SnolateTranslationSet;
import org.snomed.simplex.translation.tool.TranslationSetStatus;

import java.util.Date;

public record SnolateTranslationSetStatus(
		String id,
		String refset,
		String label,
		String name,
		TranslationSetStatus status,
		int percentageProcessed,
		int size,
		Date created,
		Date lastPulled
) {

	public static SnolateTranslationSetStatus from(SnolateTranslationSet set) {
		return new SnolateTranslationSetStatus(
				set.getId(),
				set.getRefset(),
				set.getLabel(),
				set.getName(),
				set.getStatus(),
				set.getPercentageProcessed(),
				set.getSize(),
				set.getCreated(),
				set.getLastPulled());
	}
}
