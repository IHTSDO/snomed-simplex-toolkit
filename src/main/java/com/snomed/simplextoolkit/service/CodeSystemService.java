package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.Concept;
import com.snomed.simplextoolkit.domain.CodeSystem;
import com.snomed.simplextoolkit.domain.Concepts;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CodeSystemService {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	public CodeSystem createCodeSystem(String name, String shortName, String namespace, boolean createModule, String moduleName, String existingModuleId) throws ServiceException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		// Create code system
		CodeSystem newCodeSystem = snowstormClient.createCodeSystem(name, shortName, namespace);

		if (createModule) {
			// Create module
			String tag = "core metadata concept";
			Concept tempModuleConcept = snowstormClient.createSimpleMetadataConcept(Concepts.MODULE, moduleName, tag, newCodeSystem);
			String moduleId = tempModuleConcept.getConceptId();
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


		// TODO Update code system with module as uriModuleId - Only SnowstormX so far.
		return newCodeSystem;
	}

	private static void setDefaultModule(String moduleId, CodeSystem newCodeSystem, SnowstormClient snowstormClient) {
		Map<String, String> newMetadata = new HashMap<>();
		newMetadata.put("defaultModuleId", moduleId);
		snowstormClient.addBranchMetadata(newCodeSystem.getBranchPath(), newMetadata);
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
