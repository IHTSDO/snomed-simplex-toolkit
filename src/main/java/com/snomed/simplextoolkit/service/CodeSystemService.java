package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.CodeSystem;
import com.snomed.simplextoolkit.client.domain.Concept;
import com.snomed.simplextoolkit.client.domain.Concepts;
import com.snomed.simplextoolkit.client.domain.RefsetMember;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.exceptions.ClientException;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

@Service
public class CodeSystemService {

	public static final String OWL_ONTOLOGY_REFSET = "762103008";
	public static final String OWL_EXPRESSION = "owlExpression";
	public static final String OWL_ONTOLOGY_HEADER = "734147008";

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	public CodeSystem createCodeSystem(String name, String shortName, String namespace, boolean createModule, String moduleName, String existingModuleId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		// Create code system
		CodeSystem newCodeSystem = snowstormClient.createCodeSystem(name, shortName, namespace);

		String moduleId = existingModuleId;
		if (createModule) {
			// Create module
			String tag = "core metadata concept";
			Concept tempModuleConcept = snowstormClient.createSimpleMetadataConcept(Concepts.MODULE, moduleName, tag, newCodeSystem);
			moduleId = tempModuleConcept.getConceptId();
			// Delete concept
			snowstormClient.deleteConcept(tempModuleConcept, newCodeSystem);

			// Set default module on branch
			setDefaultModule(moduleId, newCodeSystem, snowstormClient);

			// Recreate
			Concept moduleConcept = snowstormClient.newSimpleMetadataConceptWithoutSave(Concepts.MODULE, moduleName, tag);
			moduleConcept.setConceptId(moduleId);
			snowstormClient.createConcept(moduleConcept, newCodeSystem);
		} else if (existingModuleId != null && !existingModuleId.isEmpty()) {
			setDefaultModule(existingModuleId, newCodeSystem, snowstormClient);
		}

		createModuleOntologyExpression(moduleId, newCodeSystem, snowstormClient);

		// TODO Update code system with module as uriModuleId - Only SnowstormX so far.
		return newCodeSystem;
	}

	private static void setDefaultModule(String moduleId, CodeSystem newCodeSystem, SnowstormClient snowstormClient) {
		Map<String, String> newMetadata = new HashMap<>();
		newMetadata.put("defaultModuleId", moduleId);
		snowstormClient.addBranchMetadata(newCodeSystem.getBranchPath(), newMetadata);
	}

	private void createModuleOntologyExpression(String moduleId, CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		List<RefsetMember> ontologyMembers = snowstormClient.getRefsetMembers(OWL_ONTOLOGY_REFSET, codeSystem, false, 0, 100).getItems();
		RefsetMember existingOntologyExpressionMember = null;
		for (RefsetMember ontologyMember : ontologyMembers) {
			if (ontologyMember.isActive() && ontologyMember.getAdditionalFields().get(OWL_EXPRESSION).startsWith("Ontology(<http://snomed.info/sct/")) {
				existingOntologyExpressionMember = ontologyMember;
			}
		}
		if (existingOntologyExpressionMember == null) {
			throw new ServiceException(format("Ontology expression is not found for code system %s", codeSystem.getShortName()));
		}
		String moduleOntologyExpression = format("Ontology(<http://snomed.info/sct/%s>)", moduleId);
		if (!existingOntologyExpressionMember.getAdditionalFields().get(OWL_EXPRESSION).equals(moduleOntologyExpression)) {
			existingOntologyExpressionMember.setActive(false);
			RefsetMember newOntologyExpressionMember = new RefsetMember(OWL_ONTOLOGY_REFSET, moduleId, OWL_ONTOLOGY_HEADER).setAdditionalField(OWL_EXPRESSION, moduleOntologyExpression);
			snowstormClient.createUpdateRefsetMembers(List.of(existingOntologyExpressionMember, newOntologyExpressionMember), codeSystem);
		}
	}

	public void deleteCodeSystem(String shortName) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(shortName);
		// Delete code system including versions
		// Requires ADMIN permissions on codesystem branch
		snowstormClient.deleteCodeSystem(shortName);

		// Delete all branches
		// Requires ADMIN permissions on branches
		snowstormClient.deleteBranchAndChildren(codeSystem.getBranchPath());
	}
}
