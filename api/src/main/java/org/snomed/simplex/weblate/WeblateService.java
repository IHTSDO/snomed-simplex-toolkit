package org.snomed.simplex.weblate;

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
import org.snomed.simplex.util.SupplierUtil;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateComponent;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateService(SnowstormClientFactory snowstormClientFactory, WeblateClientFactory weblateClientFactory, WeblateGitClient weblateGitClient) {
		this.snowstormClientFactory = snowstormClientFactory;
		this.weblateClientFactory = weblateClientFactory;
		this.weblateGitClient = weblateGitClient;
	}

	public Page<WeblateComponent> getSharedSets() throws ServiceException {
		List<WeblateComponent> components = weblateClientFactory.getClient().listComponents(commonProject);
		components = components.stream().filter(c -> !c.slug().equals("glossary")).toList();
		return new Page<>(components);
	}

	public void createSharedSet(WeblateComponent weblateComponent) throws ServiceException {
		String project = commonProject;
		String componentSlug = weblateComponent.slug();
		String ecl = weblateComponent.ecl();
		ServiceHelper.requiredParameter("slug", componentSlug);
		ServiceHelper.requiredParameter("project", project);
		ServiceHelper.requiredParameter("ecl", ecl);

		WeblateClient weblateClient = weblateClientFactory.getClient();
		WeblateComponent existingSet = weblateClient.getComponent(project, componentSlug);
		if (existingSet != null) {
			throw new ServiceExceptionWithStatusCode("This set already exists.", HttpStatus.CONFLICT);
		}

		logger.info("Creating new component {}/{}", project, componentSlug);

		// Create directory and blank en.csv file in Git
		weblateGitClient.createBlankComponent(componentSlug);

		// Create Component in Weblate
		weblateClient.createComponent(project, weblateComponent, weblateGitClient.getRemoteRepo(), weblateGitClient.getRepoBranch());

		// Create units from ECL
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);
		logger.info("Expanding ECL {}", ecl);
		Supplier<ConceptMini> conceptStream = snowstormClient.getConceptStream(codeSystem.getBranchPath(), ecl);

		List<ConceptMini> batch;
		long added = 0;
		while (!(batch = SupplierUtil.getBatch(1_000, conceptStream)).isEmpty()) {
			logger.info("Creating batch of {} units", batch.size());
			List<Long> conceptIds = batch.stream()
					.map(ConceptMini::getConceptId)
					.map(Long::parseLong)
					.toList();
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
			Map<String, Concept> conceptMap = concepts.stream().collect(Collectors.toMap(Concept::getConceptId, Function.identity()));
			for (ConceptMini mini : batch) {
				String conceptId = mini.getConceptId();
				Concept concept = conceptMap.get(conceptId);
				String display = mini.getPtOrFsnOrConceptId();
				String explanation = WeblateExplanationCreator.getMarkdown(concept);
				List<String> displayList = List.of(display);
				WeblateUnit weblateUnit = new WeblateUnit(displayList, displayList, conceptId, explanation);
				WeblateUnit newUnit = weblateClient.createUnit(weblateUnit, project, componentSlug, EN);
				weblateClient.patchUnitExplanation(newUnit.getId(), weblateUnit.getExplanation());
				added++;
			}
		}

		if (added == 0) {
			logger.warn("No concepts matched {}/{}", project, componentSlug);
		}

		logger.info("Component {}/{} created with {} units", project, componentSlug, added);
	}

	public void updateSet(CodeSystem codeSystem) throws ServiceException {

		// This method is work in progress

		UnitSupplier unitSupplier = weblateClientFactory.getClient().getUnitStream("test", "test");

		List<WeblateUnit> batch;
		while (!(batch = unitSupplier.getBatch(1_000)).isEmpty()) {
			List<Long> conceptIds = batch.stream()
					.map(WeblateUnit::getContext)
					.map(Long::parseLong)
					.toList();
			SnowstormClient snowstormClient = snowstormClientFactory.getClient();
			List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIds, codeSystem);
			Map<String, Concept> conceptMap = concepts.stream().collect(Collectors.toMap(Concept::getConceptId, Function.identity()));
			for (WeblateUnit weblateUnit : batch) {
				String conceptId = weblateUnit.getContext();
				Concept concept = conceptMap.get(conceptId);

				String explanation = WeblateExplanationCreator.getMarkdown(concept);
				weblateUnit.setExplanation(explanation);
			}
		}
	}

	public void createConceptSet(String branch, String valueSetEcl, OutputStream outputStream) throws ServiceException, IOException {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.getBranchOrThrow(branch);
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
			// source,target,context,developer_comments
			// "Adenosine deaminase 2 deficiency","Adenosine deaminase 2 deficiency",987840791000119102,"http://snomed.info/id/987840791000119102 - Adenosine deaminase 2 deficiency (disorder)"
			writer.write("source,target,context,developer_comments");
			writer.newLine();

			Supplier<ConceptMini> conceptStream = snowstormClient.getConceptStream(branch, valueSetEcl);
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
		}
	}

	public Page<WeblateUnit> getSharedSetRecords(String slug) throws ServiceException {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		WeblatePage<WeblateUnit> unitPage = weblateClient.getUnitPage(commonProject, slug);
		return new Page<>(unitPage.results(), (long) unitPage.count());
	}
}
