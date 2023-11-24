package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.client.rvf.ValidationReport;
import com.snomed.simplextoolkit.client.rvf.ValidationServiceClient;
import com.snomed.simplextoolkit.domain.JobStatus;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.service.job.ExternalServiceJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

@Service
public class CodeSystemService {

	public static final String OWL_ONTOLOGY_REFSET = "762103008";
	public static final String OWL_EXPRESSION = "owlExpression";
	public static final String OWL_ONTOLOGY_HEADER = "734147008";

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@Autowired
	private JobService jobService;

	@Autowired
	private SupportRegister supportRegister;

	@Autowired
	private ValidationServiceClient validationServiceClient;

	private final Map<String, ExternalServiceJob> classificationJobsToMonitor = new HashMap<>();
	private final Map<String, ExternalServiceJob> validationJobsToMonitor = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

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
			snowstormClient.deleteConcept(tempModuleConcept.getConceptId(), newCodeSystem);

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
		snowstormClient.upsertBranchMetadata(newCodeSystem.getBranchPath(), newMetadata);
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

	public void validate(ExternalServiceJob asyncJob) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(asyncJob.getCodeSystem());
			URI validationUri = validationServiceClient.startValidation(codeSystem, snowstormClient);
			logger.info("Created validation. Branch:{}, RVF Job:{}", codeSystem.getWorkingBranchPath(), validationUri);
			asyncJob.setLink(validationUri.toString());
			snowstormClient.upsertBranchMetadata(codeSystem.getBranchPath(), Map.of(Branch.LATEST_VALIDATION_REPORT_METADATA_KEY, validationUri.toString()));
			validationJobsToMonitor.put(validationUri.toString(), asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemIssue(asyncJob, "Failed to create validation job.", e);
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

	public void classify(ExternalServiceJob asyncJob) {
		try {
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			String branch = asyncJob.getBranch();
			String classificationId = snowstormClient.createClassification(branch);
			logger.info("Started classification. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, asyncJob.getId());
			asyncJob.setLink(classificationId);
			classificationJobsToMonitor.put(classificationId, asyncJob);
		} catch (ServiceException e) {
			supportRegister.handleSystemIssue(asyncJob, "Failed to create classification.", e);
		}
	}

	@Scheduled(initialDelay = 15, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void monitorClassificationJobs() {
		Set<String> completedClassifications = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : classificationJobsToMonitor.entrySet()) {
			String classificationId = entry.getKey();
			ExternalServiceJob job = entry.getValue();
			String branch = job.getBranch();
			SecurityContextHolder.setContext(job.getSecurityContext());
			boolean classificationComplete = false;
			try {
				// Get client using the security context of the user who created the job
				SnowstormClient snowstormClient = snowstormClientFactory.getClient();
				SnowstormClassificationJob classificationJob = snowstormClient.getClassificationJob(branch, classificationId);
				SnowstormClassificationJob.Status status = classificationJob.getStatus();
				if (status == SnowstormClassificationJob.Status.COMPLETED) {
					if (!classificationJob.isEquivalentConceptsFound()) {
						// Start classification save (async process)
						snowstormClient.startClassificationSave(branch, classificationId);
						logger.info("Start classification save. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, job.getId());
					} else {
						supportRegister.handleContentIssue(job, "Logically equivalent concepts have been found.");
						classificationComplete = true;
					}
				} else if (status == SnowstormClassificationJob.Status.SAVED) {
					logger.info("Classification saved. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, job.getId());
					job.setStatus(JobStatus.COMPLETED);
					classificationComplete = true;
				} else if (status == SnowstormClassificationJob.Status.FAILED) {
					supportRegister.handleSystemIssue(job, "Classification failed to run in Terminology Server.");
					classificationComplete = true;
				} else if (status == SnowstormClassificationJob.Status.SAVE_FAILED) {
					supportRegister.handleSystemIssue(job, "Classification failed to save in Terminology Server.");
					classificationComplete = true;
				} else if (status == SnowstormClassificationJob.Status.STALE) {
					logger.info("Classification stale. Branch:{}, classificationId:{}, jobId:{}", branch, classificationId, job.getId());
					job.setStatus(JobStatus.ERROR);
					job.setErrorMessage("Classification became stale because of new content changes. Please try again.");
					classificationComplete = true;
				}
			} catch (ServiceException e) {
				supportRegister.handleSystemIssue(job, "Terminology Server API issue.", e);
				classificationComplete = true;
			}
			if (classificationComplete) {
				completedClassifications.add(classificationId);
			}
		}
		for (String completedClassification : completedClassifications) {
			classificationJobsToMonitor.remove(completedClassification);
		}
	}

	@Scheduled(initialDelay = 15, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
	public void monitorValidationJobs() {
		Set<String> completedValidations = new HashSet<>();
		for (Map.Entry<String, ExternalServiceJob> entry : validationJobsToMonitor.entrySet()) {
			String validationUrl = entry.getKey();
			ExternalServiceJob job = entry.getValue();
			SecurityContextHolder.setContext(job.getSecurityContext());
			boolean validationComplete = false;
			try {
				// Get client using the security context of the user who created the job
				ValidationReport validationReport = validationServiceClient.getValidation(validationUrl);
				ValidationReport.State status = validationReport.status();
				logger.debug("Validation status {} for {}", status, validationUrl);
				if (status != null) {
					if (status == ValidationReport.State.COMPLETE) {
						logger.info("Validation completed. Branch:{}, RVF Job:{}, Status:{}", job.getBranch(), validationUrl, status);
						ValidationReport.ValidationResult validationResult = validationReport.rvfValidationResult();
						ValidationReport.TestResult testResult = validationResult.TestResult();
						if (testResult.totalFailures() > 0) {
							job.setErrorMessage("Validation errors were found in the content.");
							job.setStatus(JobStatus.CONTENT_ISSUE);
						} else if (testResult.totalWarnings() > 0) {
							job.setErrorMessage("Validation warnings were found in the content.");
							job.setStatus(JobStatus.CONTENT_ISSUE);
						} else {
							job.setStatus(JobStatus.COMPLETED);
						}
						validationComplete = true;
					} else if (status == ValidationReport.State.FAILED) {
						supportRegister.handleSystemIssue(job, "RVF report failed.");
						validationComplete = true;
					}
				}
			} catch (ServiceException e) {
				supportRegister.handleSystemIssue(job, "Terminology Server or RVF API issue.", e);
				validationComplete = true;
			}
			if (validationComplete) {
				completedValidations.add(validationUrl);
			}
		}
		for (String completedValidation : completedValidations) {
			validationJobsToMonitor.remove(completedValidation);
		}
	}
}
