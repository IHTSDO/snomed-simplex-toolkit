package org.snomed.simplex.weblate;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Service
public class WeblateService {

	private final SnowstormClientFactory snowstormClientFactory;

	public WeblateService(SnowstormClientFactory snowstormClientFactory) {
		this.snowstormClientFactory = snowstormClientFactory;
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
