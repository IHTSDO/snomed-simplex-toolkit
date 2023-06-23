package com.snomed.simplextoolkit.service;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Lists;
import com.snomed.simplextoolkit.client.SnowstormClient;
import com.snomed.simplextoolkit.client.SnowstormClientFactory;
import com.snomed.simplextoolkit.client.domain.*;
import com.snomed.simplextoolkit.domain.Page;
import com.snomed.simplextoolkit.exceptions.ServiceException;
import com.snomed.simplextoolkit.rest.pojos.LanguageCode;
import com.snomed.simplextoolkit.util.TimerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static java.lang.Long.parseLong;
import static java.lang.String.format;

@Service
public class TranslationService {

	@Autowired
	private SnowstormClientFactory snowstormClientFactory;

	private List<LanguageCode> languageCodes = new ArrayList<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/language_codes_iso-639-1.txt")))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isEmpty()) {
					String[] split = line.split("\t");
					if (split.length == 2) {
						languageCodes.add(new LanguageCode(split[0], split[1]));
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public List<LanguageCode> getLanguageCodes() {
		return languageCodes;
	}

	public List<ConceptMini> listTranslations(CodeSystem codeSystem, SnowstormClient snowstormClient) throws ServiceException {
		TimerUtil timer = new TimerUtil("Load translations", Level.INFO, 2);

		List<ConceptMini> translationRefsets = snowstormClient.getRefsets("<" + Concepts.LANG_REFSET, codeSystem);
		timer.checkpoint("ECL for lang refsets");

		for (ConceptMini translationRefset : translationRefsets) {
			Page<RefsetMember> firstMember = snowstormClient.getRefsetMembers(translationRefset.getConceptId(), codeSystem, true, 0, 1);
			timer.checkpoint(format("Load one lang refset member for %s.", translationRefset.getIdAndFsnTerm()));

			if (!firstMember.getItems().isEmpty()) {
				RefsetMember member = firstMember.getItems().iterator().next();
				ReferencedComponent referencedComponent = member.getReferencedComponent();
				String lang = referencedComponent.getLang();
				if (lang != null) {
					translationRefset.addExtraField("lang", lang);
				}
			}
		}
		return translationRefsets;
	}

	@Async
	public ChangeSummary uploadTranslationAsCSV(String languageRefsetId, String languageCode, CodeSystem codeSystem, InputStream inputStream,
			boolean overwriteExistingCaseSignificance, boolean translationTermsUseTitleCase, SnowstormClient snowstormClient) throws ServiceException {

		// source,target,context,developer_comments
		logger.info("Reading translation file..");
		int added = 0;
		int updated = 0;
		int removed = 0;
		int newTotal = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String header = reader.readLine();
			if (header == null) {
				header = "";
			}
			header = header.replace("\"", "");
			if (header.equals("source,target,context,developer_comments")) {
				logger.info("Detected Weblate CSV format");
				String line;
				int lineNumber = 1;
				Map<Long, List<String>> conceptDescriptions = new Long2ObjectOpenHashMap<>();
				while ((line = reader.readLine()) != null) {
					lineNumber++;
					String[] columns = line.replace("\"", "").split(",");
					// source	target	context	developer_comments
					// 0		1		2		3
					if (columns.length < 4) {
						logger.warn("Line {} has less than 4 columns, skipping: {}", lineNumber, columns);
						continue;
					}
					String translatedTerm = columns[1];
					String conceptString = columns[2];
					if (!translatedTerm.isEmpty() && conceptString.matches("\\d+")) {
						Long conceptId = parseLong(conceptString);
						conceptDescriptions.computeIfAbsent(conceptId, id -> new ArrayList<>()).add(translatedTerm);
					}
				}
				logger.info("Read translation terms for {} concepts", conceptDescriptions.size());

				if (conceptDescriptions.isEmpty()) {
					// No change
					int activeRefsetMembers = snowstormClient.countAllActiveRefsetMembers(languageRefsetId, codeSystem);
					return new ChangeSummary(0, 0, 0, activeRefsetMembers);
				}

				int processed = 0;
				int batchSize = 500;
				for (List<Long> conceptIdBatch : Lists.partition(new ArrayList<>(conceptDescriptions.keySet()), batchSize)) {
					if (processed > 0 && processed % 1_000 == 0) {
						logger.info("Processed {} / {}", processed, conceptDescriptions.size());
					}
					processed += batchSize;
					List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptIdBatch, codeSystem);
					List<Concept> conceptsToUpdate = new ArrayList<>();
					for (Concept concept : concepts) {
						boolean anyChange = false;
						List<String> csvDescriptionTerms = conceptDescriptions.get(parseLong(concept.getConceptId()));
						List<Description> snowstormDescriptions = concept.getDescriptions();
						snowstormDescriptions.sort(Comparator.comparing(Description::isActive).reversed());

						// Remove any descriptions in snowstorm with a matching language and lang refset if they are not in the latest CSV
						List<Description> toRemove = new ArrayList<>();
						for (Description snowstormDescription : snowstormDescriptions) {
							if (snowstormDescription.getLang().equals(languageCode)
									&& snowstormDescription.isActive()
									&& snowstormDescription.getAcceptabilityMap().containsKey(languageRefsetId)) {
								if (!csvDescriptionTerms.contains(snowstormDescription.getTerm())) {
									snowstormDescription.getAcceptabilityMap().remove(languageRefsetId);
									anyChange = true;
									removed++;// Removed from the language refset

									// Also suggest that Snowstorm deletes / inactivates this description if not used by any other lang refset
									if (snowstormDescription.getAcceptabilityMap().isEmpty()) {
										toRemove.add(snowstormDescription);// Snowstorm will delete or inactivate component depending on release status
									}
								}
							}
						}
						snowstormDescriptions.removeAll(toRemove);

						// Add any missing descriptions in the snowstorm concept
						boolean firstTerm = true;
						for (String csvDescriptionTerm : csvDescriptionTerms) {
							// Match by language and term only
							Optional<Description> existingDescriptionOptional = snowstormDescriptions.stream()
									.filter(d -> d.getLang().equals(languageCode) && d.getTerm().equals(csvDescriptionTerm)).findFirst();
							// First term in the spreadsheet is "preferred"
							String newAcceptability = firstTerm ? "PREFERRED" : "ACCEPTABLE";
							String caseSignificance = guessCaseSignificance(csvDescriptionTerm, translationTermsUseTitleCase);

							if (existingDescriptionOptional.isPresent()) {
								Description existingDescription = existingDescriptionOptional.get();

								if (!existingDescription.isActive()) {// Reactivation
									existingDescription.setActive(true);
									anyChange = true;
								}

								if (overwriteExistingCaseSignificance && !existingDescription.getCaseSignificance().equals(caseSignificance)) {
									existingDescription.setCaseSignificance(caseSignificance);
									anyChange = true;
								}

								String existingAcceptability = existingDescription.getAcceptabilityMap().put(languageRefsetId, newAcceptability);
								if (!newAcceptability.equals(existingAcceptability)) {
									logger.debug("Correcting acceptability of {} '{}' from {} to {}",
											existingDescription.getDescriptionId(), existingDescription.getTerm(), existingAcceptability, newAcceptability);
									anyChange = true;
								}

								if (anyChange) {
									updated++;
								}
							} else {
								// no existing match, create new
								snowstormDescriptions.add(new Description(Concepts.SYNONYM_KEYWORD, languageCode, csvDescriptionTerm, caseSignificance,
										languageRefsetId, newAcceptability));
								anyChange = true;
								added++;
							}
							firstTerm = false;
						}

						if (anyChange) {
							conceptsToUpdate.add(concept);
						}
					}
					if (!conceptsToUpdate.isEmpty()) {
						logger.info("Updating {} concepts on {}", conceptsToUpdate.size(), codeSystem.getBranchPath());
						snowstormClient.updateBrowserFormatConcepts(conceptsToUpdate, codeSystem);
					}
				}

			} else {
				throw new ServiceException(format("Unrecognised CSV header '%s'", header));
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to read CSV.", e);
		}
		ChangeSummary changeSummary = new ChangeSummary(added, updated, removed, newTotal);
		logger.info("translation upload complete on {}: {}", codeSystem.getBranchPath(), changeSummary);
		return changeSummary;
	}

	protected String guessCaseSignificance(String term, boolean titleCaseUsed) {
		if (term.isEmpty()) {
			return "CASE_INSENSITIVE";
		}
		if (titleCaseUsed) {
			// Ignore first character by removing it
			term = term.substring(1);
		}
		return term.equals(term.toLowerCase()) ? "CASE_INSENSITIVE" : "ENTIRE_TERM_CASE_SENSITIVE";
	}

}
