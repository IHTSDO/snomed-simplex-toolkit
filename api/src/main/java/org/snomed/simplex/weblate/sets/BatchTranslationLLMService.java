package org.snomed.simplex.weblate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.BatchTranslateRequest;
import org.snomed.simplex.weblate.TranslationLLMService;
import org.snomed.simplex.weblate.UnitQueryBuilder;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateClientFactory;
import org.snomed.simplex.weblate.domain.WeblatePage;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.snomed.simplex.weblate.pojo.WeblateUnitTranslation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.snomed.simplex.weblate.WeblateSetService.JOB_TYPE_BATCH_AI_TRANSLATE;
import static org.snomed.simplex.weblate.WeblateSetService.PERCENTAGE_PROCESSED_START;

public class BatchTranslationLLMService extends AbstractWeblateSetProcessingService {

	private final WeblateClientFactory weblateClientFactory;
	private final TranslationLLMService translationLLMService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public BatchTranslationLLMService(ProcessingContext processingContext) {
		super(processingContext);
		weblateClientFactory = processingContext.weblateClientFactory();
		translationLLMService = processingContext.translationLLMService();
	}


	public void runAiBatchTranslate(WeblateTranslationSet translationSet, BatchTranslateRequest request) throws ServiceException {
		queueJob(translationSet, JOB_TYPE_BATCH_AI_TRANSLATE, request);
	}

	public void doRunAiBatchTranslate(WeblateTranslationSet translationSet, BatchTranslateRequest request) throws ServiceException {
		setProgress(translationSet, PERCENTAGE_PROCESSED_START);

		// Get translation units from Weblate
		WeblateClient weblateClient = weblateClientFactory.getClient();
		UnitQueryBuilder queryBuilder = UnitQueryBuilder.of(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT);
		queryBuilder.languageCode(translationSet.getLanguageCodeWithRefsetId());
		queryBuilder.compositeLabel(translationSet.getCompositeLabel());
		queryBuilder.state("empty");
		queryBuilder.pageSize(10);
		WeblatePage<WeblateUnit> unitPage = weblateClient.getUnitPage(queryBuilder);

		// Translate with LLM service
		List<WeblateUnit> unitsToProcess = unitPage.results().stream().filter(unit -> unit.getSource().size() == 1).toList();
		List<String> unitSources = unitsToProcess.stream().map(unit -> unit.getSource().get(0)).toList();
		if (unitSources.isEmpty()) {
			logger.error("Nothing found to translate.");
			return;
		}
		Map<String, List<String>> sourceSuggestionMap = translationLLMService.suggestTranslations(translationSet, unitSources, false);

		// Push suggestions to Weblate
		Map<String, WeblateUnit> sourceStringToUnit = unitsToProcess.stream().collect(Collectors.toMap(unit -> unit.getSource().get(0), Function.identity()));
		List<WeblateUnitTranslation> translationsToUpload = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : sourceSuggestionMap.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				String sourceValue = entry.getKey();
				WeblateUnit weblateUnit = sourceStringToUnit.get(sourceValue);
				String suggestion = entry.getValue().get(0);
				translationsToUpload.add(new WeblateUnitTranslation(weblateUnit.getContext(), suggestion));
			}
		}

		weblateClient.uploadTranslations(translationSet, translationsToUpload);
	}

}
