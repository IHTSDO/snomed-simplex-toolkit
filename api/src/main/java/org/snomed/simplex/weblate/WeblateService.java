package org.snomed.simplex.weblate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.domain.WeblateSet;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class WeblateService {

	public static final String COMMON_PROJECT = "simplex-test-project";
	//	public static final String COMMON_PROJECT = "common";

	private final SnowstormClientFactory snowstormClientFactory;
	private final WeblateClient weblateClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateService(SnowstormClientFactory snowstormClientFactory, WeblateClient weblateClient) {
		this.snowstormClientFactory = snowstormClientFactory;
		this.weblateClient = weblateClient;
	}

	public Page<WeblateSet> getSharedSets() {
		return new Page<>(weblateClient.listComponents(COMMON_PROJECT));
	}

	public void createSharedSet(WeblateSet weblateSet) throws ServiceException {
		String componentSlug = weblateSet.slug();
		WeblateSet existingSet = weblateClient.getComponent(COMMON_PROJECT, componentSlug);
		if (existingSet != null) {
			throw new ServiceExceptionWithStatusCode("This set already exists.", HttpStatus.CONFLICT);
		}

		logger.info("Creating new component {}/{}", COMMON_PROJECT, componentSlug);

		// TODO: Generate en.csv and put into GitHub.
		//  Fields: source,target,context,developer_comments
		// 	"Apple","Apple",735215001,"http://snomed.info/id/735215001 - Apple (substance)"
		String gitBranch = "two";

		// Create Component
		weblateClient.createComponent(COMMON_PROJECT, weblateSet, "git@github.com:kaicode/translation-tool-testing.git", gitBranch);
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
