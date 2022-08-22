package com.snomed.simplextoolkit.service;

import com.google.common.collect.Lists;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.Concept;
import com.snomed.simplextoolkit.client.domain.Description;
import com.snomed.simplextoolkit.domain.CodeSystem;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Long.parseLong;
import static java.lang.String.format;

@Service
public class TranslationService {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public void downloadTranslationAsSpreadsheet(String refsetId, CodeSystem codeSystem, OutputStream outputStream) {
		// conceptId	term

	}

	public ChangeSummary uploadTranslationAsCSV(String languageRefsetId, String languageCode, CodeSystem codeSystem, InputStream inputStream) throws ServiceException {
		// source,target,context,developer_comments
		logger.info("Reading translation file..");
		int added = 0;
		int updated = 0;
		int removed = 0;
		int newTotal = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String header = reader.readLine();
			header = header.replace("\"", "");
			if (header.equals("source,target,context,developer_comments")) {
				logger.info("Detected Weblate CSV format");
				String line;
				Map<Long, List<String>> conceptDescriptions = new Long2ObjectOpenHashMap<>();
				while ((line = reader.readLine()) != null) {
					String[] columns = line.replace("\"", "").split(",");
					// source	target	context	developer_comments
					// 0		1		2		3
					String translatedTerm = columns[1];
					String conceptString = columns[2];
					if (!translatedTerm.isEmpty() && conceptString.matches("\\d+")) {
						Long conceptId = parseLong(conceptString);
						conceptDescriptions.computeIfAbsent(conceptId, id -> new ArrayList<>()).add(translatedTerm);
					}
				}
				logger.info("Read translation terms for {} concepts", conceptDescriptions.size());

				SnowstormClient snowstormClient = snowstormClientFactory.getClient();

				if (conceptDescriptions.isEmpty()) {
					int activeRefsetMembers = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
					return new ChangeSummary(0, 0, 0, activeRefsetMembers);
				}

				for (List<Long> conceptIdBatch : Lists.partition(new ArrayList<>(conceptDescriptions.keySet()), 500)) {
					List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
					List<Concept> conceptsToUpdate = new ArrayList<>();
					for (Concept concept : concepts) {
						boolean anyChange = false;
						List<String> csvDescriptionTerms = conceptDescriptions.get(parseLong(concept.getConceptId()));
						List<Description> snowstormDescriptions = concept.getDescriptions();

						// Remove any descriptions in snowstorm with a matching language and lang refset if they are not in the latest CSV
						List<Description> toRemove = new ArrayList<>();
						for (Description snowstormDescription : snowstormDescriptions) {
							if (snowstormDescription.getLang().equals(languageCode)) {
								if (!csvDescriptionTerms.contains(snowstormDescription.getTerm())) {
									snowstormDescription.getAcceptabilityMap().remove(languageRefsetId);
									if (snowstormDescription.getAcceptabilityMap().isEmpty()) {
										toRemove.add(snowstormDescription);// Snowstorm will delete or inactivate component depending on release status
										removed++;
										anyChange = true;
									}
								}
							}
						}
						snowstormDescriptions.removeAll(toRemove);

						// Add any missing descriptions in the snowstorm concept
						boolean firstTerm = true;
						for (String csvDescriptionTerm : csvDescriptionTerms) {
							// Match by language and term only
							Optional<Description> existingDescription = snowstormDescriptions.stream()
									.filter(d -> d.getLang().equals(languageCode) && d.getTerm().equals(csvDescriptionTerm)).findFirst();
							// First term in the spreadsheet is "preferred"
							String newAcceptability = firstTerm ? "PREFERRED" : "ACCEPTABLE";
							if (existingDescription.isPresent()) {
								String existingAcceptability = existingDescription.get().getAcceptabilityMap().put(languageRefsetId, newAcceptability);
								if (!newAcceptability.equals(existingAcceptability)) {
									updated++;
									anyChange = true;
								}
							} else {
								// no existing match
								String caseSensitive = "ENTIRE_TERM_CASE_SENSITIVE";// TODO
								snowstormDescriptions.add(new Description("900000000000013009", languageCode, csvDescriptionTerm, caseSensitive,
										languageRefsetId, newAcceptability));
								added++;
							}
							firstTerm = false;
						}

						if (anyChange) {
							conceptsToUpdate.add(concept);
						}
					}
					if (!conceptsToUpdate.isEmpty()) {
						snowstormClient.updateBrowserFormatConcepts(conceptsToUpdate, codeSystem);
					}
				}

			} else {
				throw new ServiceException(format("Unrecognised CSV header '%s'", header));
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to read CSV.", e);
		}
		return new ChangeSummary(added, updated, removed, newTotal);
	}
}
