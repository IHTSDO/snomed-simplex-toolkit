package org.snomed.simplex.service;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Iterables;
import org.apache.logging.log4j.util.Strings;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.UpgradeReport;
import org.snomed.simplex.domain.RefsetUpgradeReport;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.util.IdentifierUtil;
import org.snomed.simplex.util.TimerUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UpgradeReportService {

	public UpgradeReport getUpgradeReport(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		TimerUtil timer = new TimerUtil("Upgrade Report", Level.INFO, 10);
		Branch branch = snowstormClient.getBranchOrThrow(codeSystem.getBranchPath());
		String previousDependencyEffectiveTime = branch.getMetadataValue(Branch.PREVIOUS_DEPENDENCY_EFFECTIVE_TIME_METADATA_KEY);

		UpgradeReport report = new UpgradeReport();
		report.setUpgradeTimestamp(new Date());
		report.setPreviousDependencyEffectiveTime(Strings.isBlank(previousDependencyEffectiveTime) ? 0 : Integer.parseInt(previousDependencyEffectiveTime));
		report.setNewDependencyEffectiveTime(codeSystem.getDependantVersionEffectiveTime());

		report.setConceptsWithMissingOrInactiveParent(getConceptsWithMissingOrInactiveParents(codeSystem, snowstormClient));
		timer.checkpoint("Concepts with inactive parents");

		report.setSubsets(getRefsetsWithMissingConceptsList(Concepts.SIMPLE_TYPE_REFSET, codeSystem, snowstormClient));
		timer.checkpoint("Subsets with inactive members");

		report.setMaps(getRefsetsWithMissingConceptsList(Concepts.SIMPLE_MAP_WITH_CORRELATION_TO_SNOMEDCT_REFSET, codeSystem, snowstormClient));
		timer.checkpoint("Maps with inactive members");

		report.setTranslations(getRefsetsWithMissingConceptsList(Concepts.LANG_REFSET, codeSystem, snowstormClient));
		timer.checkpoint("Translations with inactive concepts");

		return report;
	}

	private List<RefsetUpgradeReport> getRefsetsWithMissingConceptsList(String refsetType, CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		List<RefsetUpgradeReport> subsets = new ArrayList<>();
		List<ConceptMini> refsets = snowstormClient.getRefsets("<%s".formatted(refsetType), codeSystem);
		for (ConceptMini refset : refsets) {

			List<RefsetMember> refsetMembers = snowstormClient.loadAllRefsetMembers(refset.getConceptId(), codeSystem, true);
			Set<Long> referencedConceptIds = refsetMembers.stream().map(RefsetMember::getReferencedComponentId).filter(IdentifierUtil::isConceptId)
					.map(Long::parseLong).collect(Collectors.toSet());
			Set<Long> inactiveReferencedConcepts = new HashSet<>(referencedConceptIds);

			for (List<Long> referencedConceptIdBatch : Iterables.partition(referencedConceptIds, 10_000)) {
				List<Long> activeConceptIds = snowstormClient.getActiveConceptIds(referencedConceptIdBatch, codeSystem);
				activeConceptIds.forEach(inactiveReferencedConcepts::remove);
			}

			Set<ConceptMini> inactiveReferencedConceptMinis = new HashSet<>();
			if (!inactiveReferencedConcepts.isEmpty()) {
				List<Concept> inactiveConcepts = snowstormClient.loadBrowserFormatConcepts(inactiveReferencedConcepts, codeSystem);
				inactiveReferencedConceptMinis.addAll(inactiveConcepts.stream().map(Concept::toMini).toList());
			}
			RefsetUpgradeReport refsetUpgradeReport = new RefsetUpgradeReport(refset, inactiveReferencedConceptMinis);
			subsets.add(refsetUpgradeReport);
		}
		return subsets;
	}

	private Set<ConceptMini> getConceptsWithMissingOrInactiveParents(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {

		Map<Long, Set<Long>> conceptToParentsMap = new HashMap<>();

		List<Long> conceptIds = snowstormClient.findAllConceptsByModule(codeSystem, codeSystem.getDefaultModule()).stream()
				.map(ConceptMini::getConceptId).map(Long::parseLong).toList();
		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
		for (Concept concept : concepts) {
			for (Axiom classAxiom : concept.getClassAxioms()) {
				if (classAxiom.isActive()) {
					for (Relationship relationship : classAxiom.getRelationships()) {
						if (relationship.isActive() && relationship.getTypeId().equals(Concepts.IS_A)) {
							conceptToParentsMap.computeIfAbsent(concept.getConceptIdAsLong(), i -> new HashSet<>())
									.add(Long.parseLong(relationship.getDestinationId()));
						}
					}
				}
			}
		}

		Set<Long> allParents = conceptToParentsMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

		List<Long> parentsExist = snowstormClient.getActiveConceptIds(allParents, codeSystem);
		Set<Long> parentsThatDoNotExist = new HashSet<>(allParents);
		parentsExist.forEach(parentsThatDoNotExist::remove);

		Set<ConceptMini> conceptsWithMissingOrInactiveParents = new HashSet<>();
		if (!parentsThatDoNotExist.isEmpty()) {
			Map<Long, Concept> conceptMap = concepts.stream().collect(Collectors.toMap(Concept::getConceptIdAsLong, Function.identity()));
			for (Long parentThatDoesNotExist : parentsThatDoNotExist) {
				for (Map.Entry<Long, Set<Long>> conceptAndParents : conceptToParentsMap.entrySet()) {
					if (conceptAndParents.getValue().contains(parentThatDoesNotExist)) {
						Long conceptId = conceptAndParents.getKey();
						conceptsWithMissingOrInactiveParents.add(conceptMap.get(conceptId).toMini());
					}
				}
			}
		}
		return conceptsWithMissingOrInactiveParents;
	}

}
