package org.snomed.simplex.service;

import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.exceptions.ServiceException;
import org.junit.jupiter.api.Test;
import org.snomed.otf.snomedboot.testutil.ZipUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RefsetToolTranslationZipReaderTest {

	@Test
	void readUpload() throws IOException, ServiceException {
		File zipFile = ZipUtil.zipDirectoryRemovingCommentsAndBlankLines("src/test/resources/refset-translation-tool-example-export");
		try (InputStream inputStream = new FileInputStream(zipFile)) {
			String langRefset = "46011000052107";
			RefsetToolTranslationZipReader reader = new RefsetToolTranslationZipReader(inputStream, langRefset);
			Map<Long, List<Description>> readConceptDescriptionMap = reader.readUpload();
			assertEquals(3, readConceptDescriptionMap.size());
			System.out.println(readConceptDescriptionMap.keySet());
			List<Description> descriptions = readConceptDescriptionMap.get(233601004L);
			Description description = descriptions.get(0);
			assertEquals("sv", description.getLang());
			assertEquals("akut viral bronkit", description.getTerm());
			assertEquals(Description.Type.SYNONYM, description.getType());
			assertEquals(Description.Acceptability.PREFERRED, description.getAcceptabilityMap().get(langRefset));
		}

	}
}
