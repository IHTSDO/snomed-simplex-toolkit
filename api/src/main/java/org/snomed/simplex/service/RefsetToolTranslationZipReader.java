package org.snomed.simplex.service;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.exceptions.ServiceException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.Long.parseLong;
import static java.lang.String.format;

public class RefsetToolTranslationZipReader implements TranslationUploadProvider {

	private final InputStream rf2ZipFileInputStream;

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private final String langRefset;

	public RefsetToolTranslationZipReader(InputStream rf2ZipFileInputStream, String langRefset) {
		this.rf2ZipFileInputStream = rf2ZipFileInputStream;
		this.langRefset = langRefset;
	}

	@Override
	public Map<Long, List<Description>> readUpload() throws ServiceException {
		Map<Long, List<Description>> conceptMap = new Long2ObjectLinkedOpenHashMap<>();
		Map<Long, Description.Acceptability> descriptionAcceptabilityMap = new Long2ObjectLinkedOpenHashMap<>();

		try (ZipInputStream zipInputStream = new ZipInputStream(rf2ZipFileInputStream)) {
			ZipEntry zipEntry;
			// Loop through zip entries until we have read the description and lang refset files. We don't know what order they will be in within the zip.
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				if (zipEntry.getName().contains("sct2_Description_Snapshot")) {
					// Read description file
					BufferedReader descriptionReader = new BufferedReader(new InputStreamReader(zipInputStream));
					String header = descriptionReader.readLine();
					if (!header.equals("id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId")) {
						throw new ServiceException(format("Unrecognised header in Description file '%s' within zip file.", zipEntry.getName()));
					}
					String line;
					int lineNum = 1;
					while ((line = descriptionReader.readLine()) != null) {
						lineNum++;
						String[] split = line.split("\t");
						// id	effectiveTime	active	moduleId	conceptId	languageCode	typeId	term	caseSignificanceId
						// 0	1				2		3			4			5				6		7		8
						if (!split[4].matches("\\d+")) {
							logger.info("Bad conceptId format on line {}.", lineNum);
							continue;
						}
						long conceptId = parseLong(split[4]);
						String languageCode = split[5];
						// Strip any dialect, just keep language code
						languageCode = languageCode.substring(0, 2);

						Description description = new Description(Description.Type.fromConceptId(split[6]), languageCode, split[7],
								Description.CaseSignificance.fromConceptId(split[8]));

						description.setDescriptionId(split[0]);
						conceptMap.computeIfAbsent(conceptId, key -> new ArrayList<>()).add(description);
					}
				} else if (zipEntry.getName().contains("der2_cRefset_LanguageSnapshot")) {
					// Read lang refset file
					BufferedReader langRefsetReader = new BufferedReader(new InputStreamReader(zipInputStream));
					String header = langRefsetReader.readLine();
					if (!header.equals("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId")) {
						throw new ServiceException(format("Unrecognised header in Language Refset file '%s' within zip file.", zipEntry.getName()));
					}
					String line;
					int lineNum = 1;
					while ((line = langRefsetReader.readLine()) != null) {
						lineNum++;
						String[] split = line.split("\t");
						// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	acceptabilityId
						// 0	1				2		3			4			5						6

						// Only read active rows in the refset we are interested in
						if ("1".equals(split[2])) {
							if (!split[5].matches("\\d+")) {
								logger.info("Bad referencedComponentId format on line {}.", lineNum);
								continue;
							}
							long descriptionId = parseLong(split[5]);
							descriptionAcceptabilityMap.put(descriptionId, Description.Acceptability.fromConceptId(split[6]));
						}
					}

				}
			}

			// Merge acceptability into descriptions. Drop descriptions and concepts that has no active acceptability in the lang refset we are focused on.

			Set<Long> conceptsWithNoInterestingDescriptions = new LongOpenHashSet();
			for (Map.Entry<Long, List<Description>> conceptAndDescriptions : conceptMap.entrySet()) {
				for (Description description : conceptAndDescriptions.getValue()) {
					Description.Acceptability acceptability = descriptionAcceptabilityMap.get(parseLong(description.getDescriptionId()));
					if (acceptability != null) {
						description.setAcceptabilityMap(Map.of(langRefset, acceptability));
					}
					// Forget the description id from the refset tool
					description.setDescriptionId(null);
				}
				// Forget about descriptions with no active acceptability in this lang refset
				conceptAndDescriptions.setValue(conceptAndDescriptions.getValue().stream()
						.filter(description -> description.getAcceptabilityMap() != null).collect(Collectors.toList()));
				if (conceptAndDescriptions.getValue().isEmpty()) {
					conceptsWithNoInterestingDescriptions.add(conceptAndDescriptions.getKey());
				}
			}
			for (Long conceptsWithNoInterestingDescription : conceptsWithNoInterestingDescriptions) {
				conceptMap.remove(conceptsWithNoInterestingDescription);
			}

			return conceptMap;
		} catch (IOException e) {
			throw new ServiceException("Failed to read zip file.", e);
		}
	}

}
