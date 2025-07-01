package org.snomed.simplex.weblate;

import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.weblate.domain.WeblateComponent;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class WeblateService {

	public static final String EN = "en";

	@Value("${weblate.common.project}")
	public String commonProject;
	private final SnowstormClientFactory snowstormClientFactory;
	private final WeblateClientFactory weblateClientFactory;
	private final WeblateGitClient weblateGitClient;
	private final ExecutorService addLanguageExecutorService;
	private final SupportRegister supportRegister;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateService(SnowstormClientFactory snowstormClientFactory, WeblateClientFactory weblateClientFactory, WeblateGitClient weblateGitClient,
			SupportRegister supportRegister) {

		this.snowstormClientFactory = snowstormClientFactory;
		this.weblateClientFactory = weblateClientFactory;
		this.weblateGitClient = weblateGitClient;
		this.supportRegister = supportRegister;
		addLanguageExecutorService = Executors.newFixedThreadPool(1, new DefaultThreadFactory("Weblate-add-language-thread"));
	}

	public Page<WeblateComponent> getSharedSets() throws ServiceException {
		List<WeblateComponent> components = weblateClientFactory.getClient().listComponents(commonProject);
		components = components.stream().filter(c -> !c.slug().equals("glossary")).toList();
		return new Page<>(components);
	}

	public ChangeSummary updateSharedSet(String slug, int startPage) throws ServiceException {
		String project = commonProject;
		ServiceHelper.requiredParameter("slug", slug);
		ServiceHelper.requiredParameter("project", project);

		// nonsense code for sonar
		if (slug.equals("xxx")) {
			logger.info(weblateGitClient.getRemoteRepo());
		}

		WeblateClient weblateClient = weblateClientFactory.getClient();

		UnitSupplier unitStream = weblateClient.getUnitStream(project, slug, startPage);
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);

		int processed = 0;
		List<WeblateUnit> batch;
		while (!(batch = unitStream.getBatch(1_000)).isEmpty()) {
			List<Long> conceptIds = batch.stream()
					.map(WeblateUnit::getKey)
					.map(Long::parseLong)
					.toList();
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);

			Map<String, Concept> conceptMap = concepts.stream().collect(Collectors.toMap(Concept::getConceptId, Function.identity()));
			for (WeblateUnit unit : batch) {
				String conceptId = unit.getKey();
				Concept concept = conceptMap.get(conceptId);
				String explanation = WeblateExplanationCreator.getMarkdown(concept);
				if (unit.getExplanation() == null || unit.getExplanation().isEmpty()) {
					unit.setExplanation(explanation);
					weblateClient.patchUnitExplanation(unit.getId(), unit.getExplanation());
				}
				processed++;
				if (processed % 1_000 == 0) {
					logger.info("Processed {} units", String.format("%,d", processed));
				}
			}
		}

		if (processed == 0) {
			logger.warn("No concepts matched {}/{}", project, slug);
		}

		logger.info("Component {}/{} updated with {} units", project, slug, processed);
		return new ChangeSummary(processed, 0, 0, processed);
	}

	@Async
	public void createConceptSet(String branch, String focusConcept, File outputFile, SecurityContext securityContext) throws ServiceException, IOException {
		try (OutputStream outputStream = new FileOutputStream(outputFile)) {
			SecurityContextHolder.setContext(securityContext);
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			snowstormClient.getBranchOrThrow(branch);
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
				// source,target,context,developer_comments
				// "Adenosine deaminase 2 deficiency","Adenosine deaminase 2 deficiency",987840791000119102,"http://snomed.info/id/987840791000119102 - Adenosine deaminase 2 deficiency (disorder)"
				writer.write("source,target,context,developer_comments");
				writer.newLine();

				Supplier<ConceptMini> conceptStream = snowstormClient.getConceptSortedHierarchyStream(branch, focusConcept);
				ConceptMini concept;
				while ((concept = conceptStream.get()) != null) {
					writer.write("\"");
					writer.write(concept.getPtOrFsnOrConceptId());
					writer.write("\",\"");
					writer.write(concept.getPtOrFsnOrConceptId());
					writer.write("\",");
					writer.write(concept.getConceptId());
					writer.write(",\"");
					writer.write(String.format("http://snomed.info/id/%s - %s", concept.getConceptId(), concept.getFsnTermOrConceptId()));
					writer.write("\"");
					writer.newLine();
				}
				logger.info("Created concept set {}/{}", branch, focusConcept);
			}
		}
	}

	public Page<WeblateUnit> getSharedSetRecords(String slug) throws ServiceException {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		WeblatePage<WeblateUnit> unitPage = weblateClient.getUnitPage(commonProject, slug);
		return new Page<>(unitPage.results(), (long) unitPage.count());
	}

	public void deleteSharedSet(String slug) throws ServiceException {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		weblateClient.deleteComponent(commonProject, slug);
		// Will need to delete from git too.
	}

	public void initialiseLanguageAndTranslationAsync(ConceptMini langRefset, String languageCodeWithRefset, Consumer<ServiceException> errorCallback) {

		SecurityContext securityContext = SecurityContextHolder.getContext();

		addLanguageExecutorService.submit(() ->{
			SecurityContextHolder.setContext(securityContext);
			try {
				WeblateClient weblateClient = weblateClientFactory.getClient();

				// LanguageCode format = lang-refsetid, example fr-100000100
				if (!weblateClient.isLanguageExists(languageCodeWithRefset)) {
					logger.info("Language {} does not exist in Weblate, creating...", languageCodeWithRefset);
					String refsetTerm = langRefset.getPtOrFsnOrConceptId();
					String leftToRight = "ltr";
					// This request is quick because it's not creating any terms.
					weblateClient.createLanguage(languageCodeWithRefset, refsetTerm, leftToRight);
				}

				if (!weblateClient.isTranslationExistsSearchByLanguageRefset(languageCodeWithRefset)) {
					logger.info("Translation {} does not exist in Weblate, creating...", languageCodeWithRefset);
					// This request takes a long time because it's creating a new translation of the terms.
					weblateClient.createTranslation(languageCodeWithRefset);
				}
			} catch (ServiceExceptionWithStatusCode e) {
				supportRegister.handleSystemError(CodeSystem.SHARED, "Failed to add Weblate language.", e);
				errorCallback.accept(e);
			}
		});
	}
}
