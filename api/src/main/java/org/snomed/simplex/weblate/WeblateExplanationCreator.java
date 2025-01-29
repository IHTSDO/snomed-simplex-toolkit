package org.snomed.simplex.weblate;

import org.snomed.simplex.client.domain.Component;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.Description;

import java.util.Comparator;
import java.util.List;

public class WeblateExplanationCreator {

	public String getExplanation(Concept concept) {
		concept.getDescriptions().forEach(d -> d.setSortScore(getDescriptionSortScore(d)));
		List<Description> descriptions = concept.getDescriptions().stream()
				.filter(Component::isActive)
				.filter(d -> d.getType() != Description.Type.TEXT_DEFINITION)
				.filter(d -> d.getAcceptabilityMap().containsKey(Concepts.US_LANG_REFSET))
				.sorted(Comparator.comparing(Description::getSortScore))
				.toList();

		StringBuilder builder = new StringBuilder("""
						#### Concept Details
						| Descriptions |  | Parents and Attributes |  |
						| :------------- | --- | ------------: | :--- |
						""");

		for (Description description : descriptions) {
			builder.append("| %s%s |  |  |  |%n".formatted(getDescriptionType(description), description.getTerm()));
		}
		return builder.toString();
	}

	private String getDescriptionType(Description description) {
		if (description.getType() == Description.Type.FSN) {
			return "_FSN_: ";
		}
		if (description.isUsPreferredTerm()) {
			return "_PT_: ";
		}
		return "";
	}

	private int getDescriptionSortScore(Description description) {
		if (description.getType() == Description.Type.FSN) {
			return 1;
		}
		if (description.isUsPreferredTerm()) {
			return 2;
		}
		return 3;
	}

}
