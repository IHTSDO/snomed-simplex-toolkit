package org.snomed.simplex.weblate;

import org.apache.commons.lang3.tuple.Pair;
import org.snomed.simplex.client.domain.*;

import java.util.ArrayList;
import java.util.List;

import static org.snomed.simplex.client.domain.Description.Type.FSN;
import static org.snomed.simplex.client.domain.Description.Type.SYNONYM;

public class WeblateExplanationCreator {

	private WeblateExplanationCreator() {
	}

	public static String getMarkdown(Concept concept) {
		List<Pair<String, String>> relationships = new ArrayList<>();
		for (Relationship relationship : concept.getRelationships().stream().filter(Relationship::isActive).toList()) {
			String typeTerm;
			if (relationship.getTypeId().equals(Concepts.IS_A)) {
				typeTerm = "Parent";
			} else {
				typeTerm = relationship.getType().getPt().getTerm();
			}
			String valueString;
			ConcreteValue concreteValue = relationship.getConcreteValue();
			if (concreteValue != null) {
				valueString = concreteValue.getValue();
			} else {
				ConceptMini target = relationship.getTarget();
				valueString = "%s [open](http://snomed.info/id/%s)".formatted(target.getFsn().getTerm(), target.getConceptId());
			}
			relationships.add(Pair.of(typeTerm, valueString));
		}

		List<Description> activeDescriptions = concept.getDescriptions().stream().filter(Component::isActive).toList();
		Description fsn = activeDescriptions.stream()
				.filter(d -> d.getType() == FSN)
				.filter(d -> d.getAcceptabilityMap().get(Concepts.US_LANG_REFSET) == Description.Acceptability.PREFERRED)
				.findFirst().orElse(new Description());
		Description pt = activeDescriptions.stream()
				.filter(d1 -> d1.getType() == SYNONYM)
				.filter(d1 -> d1.getAcceptabilityMap().get(Concepts.US_LANG_REFSET) == Description.Acceptability.PREFERRED)
				.findFirst().orElse(new Description());
		List<Description> synonyms = activeDescriptions.stream()
				.filter(d -> d.getType() == SYNONYM)
				.filter(d -> d.getAcceptabilityMap().get(Concepts.US_LANG_REFSET) == Description.Acceptability.ACCEPTABLE)
				.toList();

		StringBuilder builder = new StringBuilder(
				"""
						#### Concept Details
						
						| Descriptions |  | Parents and Attributes |  |
						| :------------- | --- | ------------: | :--- |
						""");
		int row = 0;
		appendRow(builder, "_FSN_: ", fsn.getTerm(), relationships, row++);
		appendRow(builder, "_PT_: ", pt.getTerm(), relationships, row++);
		// Add any synonyms
		for (Description synonym : synonyms) {
			appendRow(builder, "", synonym.getTerm(), relationships, row++);
		}
		// Add any remaining relationships
		while (row < relationships.size()) {
			appendRow(builder, "", "", relationships, row++);
		}
		builder.append("[View concept](http://snomed.info/id/").append(concept.getConceptId()).append(")");
		return builder.toString();
	}

	private static void appendRow(StringBuilder builder, String type, String term, List<Pair<String, String>> relationships, int row) {
		builder.append("| ");
		if (!term.isEmpty()) {
			builder.append(type).append(term);
		}
		builder.append(" |  | ");
		if (relationships.size() > row) {
			Pair<String, String> attribute = relationships.get(row);
			builder.append("_").append(attribute.getLeft()).append("_ →");
			builder.append(" |  ");
			builder.append(attribute.getRight());
		}
		builder.append(" |\n");
	}

}
