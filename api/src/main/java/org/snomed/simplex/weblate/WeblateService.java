package org.snomed.simplex.weblate;

import jakarta.annotation.PostConstruct;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.util.FileUtils;
import org.snomed.simplex.weblate.domain.WeblateProject;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Service
public class WeblateService {

	private final WeblateClient weblateClient;
	private final SnowstormClientFactory snowstormClientFactory;

	public WeblateService(WeblateClient weblateClient, SnowstormClientFactory snowstormClientFactory) {
		this.weblateClient = weblateClient;
		this.snowstormClientFactory = snowstormClientFactory;
	}

//	@PostConstruct
	public void setup() throws ServiceException {
		System.out.println("Setting up WeblateClient..");
		List<WeblateProject> weblateProjects = weblateClient.listProjects();
		System.out.println("Weblate Projects:");
		for (WeblateProject weblateProject : weblateProjects) {
			System.out.println("- " + weblateProject.name());
		}

		WeblateProject project = weblateClient.getProject("shared");
		createComponent(project, "Cardiovascular findings", "<! 106063007 |Cardiovascular finding (finding)|");
	}

	public void createComponent(WeblateProject project, String title, String ecl) throws ServiceException {
		SnowstormClient client = snowstormClientFactory.getClient();
		Supplier<ConceptMini> conceptStream = client.getConceptStream("MAIN", ecl);
		List<String> conceptIds = Stream.generate(conceptStream).map(ConceptMini::getConceptId).toList();
		File tempFile = null;
		try {
			tempFile = File.createTempFile(UUID.randomUUID().toString(), ".txt");
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				createConceptSet("MAIN", ecl, fos);
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to create temp file", e);
		} finally {
			FileUtils.deleteOrLogWarning(tempFile);
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
}
