package org.snomed.simplex.weblate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.domain.Concept;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeblateExplanationCreatorTest {

    private ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testGetMarkdown() throws IOException {
        String markdown = WeblateExplanationCreator.getMarkdown(getConcept("/dummy-concepts/429926006.json"));
        assertEquals("""
                #### Concept Details
                
                | Descriptions |  | Parents and Attributes |  |
                | :------------- | --- | ------------: | :--- |
                | _FSN_: Computed tomography of middle ear (procedure) |  | _Parent_ → |  Computed tomography of head (procedure) [open](http://snomed.info/id/303653007) |
                | _PT_: CT of middle ear |  | _Parent_ → |  Mastoid and middle ear procedure (procedure) [open](http://snomed.info/id/359655004) |
                | Computed tomography (CT) of middle ear |  | _Parent_ → |  Procedure on middle ear (procedure) [open](http://snomed.info/id/118893003) |
                | Computed tomography of middle ear |  | _Parent_ → |  X-ray of ear (procedure) [open](http://snomed.info/id/420068003) |
                |  |  | _Method_ → |  Computed tomography imaging - action (qualifier value) [open](http://snomed.info/id/312251004) |
                |  |  | _Procedure site - Direct_ → |  Middle ear structure (body structure) [open](http://snomed.info/id/25342003) |
                [View concept](http://snomed.info/id/429926006)""", markdown);
    }

    @Test
    void testGetMarkdownConcreteDomain() throws IOException {
        String markdown = WeblateExplanationCreator.getMarkdown(getConcept("/dummy-concepts/348315009.json"));
        assertEquals("""
               #### Concept Details
             
               | Descriptions |  | Parents and Attributes |  |
               | :------------- | --- | ------------: | :--- |
               | _FSN_: Product containing precisely caffeine 65 milligram and paracetamol 500 milligram/1 each conventional release oral tablet (clinical drug) |  | _Parent_ → |  Product containing only caffeine and paracetamol in oral dose form (medicinal product form) [open](http://snomed.info/id/778591001) |
               | _PT_: Acetaminophen 500 mg and caffeine 65 mg oral tablet |  | _Has manufactured dose form_ → |  Conventional release oral tablet (dose form) [open](http://snomed.info/id/421026006) |
               | Caffeine 65 mg and paracetamol 500 mg oral tablet |  | _Has unit of presentation_ → |  Tablet (unit of presentation) [open](http://snomed.info/id/732936001) |
               |  |  | _Count of base of active ingredient_ → |  2 |
               |  |  | _Has precise active ingredient_ → |  Caffeine (substance) [open](http://snomed.info/id/255641001) |
               |  |  | _Has BoSS_ → |  Caffeine (substance) [open](http://snomed.info/id/255641001) |
               |  |  | _Has presentation strength numerator value_ → |  65 |
               |  |  | _Has presentation strength numerator unit_ → |  milligram (qualifier value) [open](http://snomed.info/id/258684004) |
               |  |  | _Has presentation strength denominator value_ → |  1 |
               |  |  | _Has presentation strength denominator unit_ → |  Tablet (unit of presentation) [open](http://snomed.info/id/732936001) |
               |  |  | _Has precise active ingredient_ → |  Paracetamol (substance) [open](http://snomed.info/id/387517004) |
               |  |  | _Has BoSS_ → |  Paracetamol (substance) [open](http://snomed.info/id/387517004) |
               |  |  | _Has presentation strength numerator value_ → |  500 |
               |  |  | _Has presentation strength numerator unit_ → |  milligram (qualifier value) [open](http://snomed.info/id/258684004) |
               |  |  | _Has presentation strength denominator value_ → |  1 |
               |  |  | _Has presentation strength denominator unit_ → |  Tablet (unit of presentation) [open](http://snomed.info/id/732936001) |
               [View concept](http://snomed.info/id/348315009)""", markdown);
    }

    private Concept getConcept(String conceptTestInput) throws IOException {
        return objectMapper.readValue(getClass().getResourceAsStream(conceptTestInput), Concept.class);
    }

}
