package org.snomed.simplex.rest;

import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.exceptions.ServiceException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.function.Supplier;

@RestController
@Tag(name = "Weblate Util", description = "Tools for maintaining weblate")
@RequestMapping("api/weblate-util")
public class WeblateComponentUtilityController {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	@GetMapping(value = "/component-csv", produces = "text/csv")
	public void createCollection(@RequestParam String branch, @RequestParam String valueSetEcl,
								 HttpServletResponse response) throws ServiceException, IOException {

		SnowstormClient snowstormClient = snowstormClientFactory.getClient();
		snowstormClient.getBranchOrThrow(branch);

		response.setContentType("text/csv");
		try (BufferedWriter writer = new BufferedWriter(response.getWriter())) {
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
