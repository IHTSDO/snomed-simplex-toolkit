package org.snomed.simplex.client.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class ConceptSerialisationTest {

	@Test
	void testReSerialisation() throws IOException, JSONException {
		ObjectMapper mapper = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		// Read example concept JSON
		String originalConceptJson = StreamUtils.copyToString(getClass().getResourceAsStream("/example-concept.json"), StandardCharsets.UTF_8);

		// Map JSON to Object
		Concept concept = mapper.readValue(originalConceptJson, Concept.class);
		// Map Object back to JSON
		String reserialisedJson = mapper.writeValueAsString(concept);

		// Test that nothing was lost - otherwise we will delete things when passed back to Snowstorm!
		JSONAssert.assertEquals(originalConceptJson, reserialisedJson, false);
	}

}