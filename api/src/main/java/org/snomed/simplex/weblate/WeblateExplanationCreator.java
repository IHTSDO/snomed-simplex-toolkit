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
		List<Pair<String, ConceptMini>> attributes = new ArrayList<>();
		for (Relationship relationship : concept.getRelationships().stream().filter(Relationship::isActive).toList()) {
			if (relationship.getTypeId().equals(Concepts.IS_A)) {
				attributes.add(Pair.of("Parent", relationship.getTarget()));
			} else {
				attributes.add(Pair.of(relationship.getType().getPt().getTerm(), relationship.getTarget()));
			}
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
		appendRow(builder, "_FSN_: ", fsn.getTerm(), attributes, row++);
		appendRow(builder, "_PT_: ", pt.getTerm(), attributes, row++);
		// Add any synonyms
		for (Description synonym : synonyms) {
			appendRow(builder, "", synonym.getTerm(), attributes, row++);
		}
		// Add any remaining attributes
		while (row < attributes.size()) {
			appendRow(builder, "", "", attributes, row++);
		}
		builder.append("[View concept](http://snomed.info/id/").append(concept.getConceptId()).append(")");
		return builder.toString();
	}

	private static void appendRow(StringBuilder builder, String type, String term, List<Pair<String, ConceptMini>> attributes, int row) {
		builder.append("| ");
		if (!term.isEmpty()) {
			builder.append(type).append(term);
		}
		builder.append(" |  | ");
		if (attributes.size() > row) {
			Pair<String, ConceptMini> attribute = attributes.get(row);
			builder.append("_").append(attribute.getLeft()).append("_ →");
			builder.append(" |  ");
			ConceptMini target = attribute.getRight();
			builder.append(target.getFsn().getTerm()).append(" [open](http://snomed.info/id/").append(target.getConceptId()).append(")");
		}
		builder.append(" |\n");
	}

}
