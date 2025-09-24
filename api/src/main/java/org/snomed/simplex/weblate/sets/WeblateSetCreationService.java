package org.snomed.simplex.weblate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.util.TimerUtil;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateClientFactory;
import org.snomed.simplex.weblate.WeblateSetRepository;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.snomed.simplex.weblate.domain.WeblateLabel;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.snomed.simplex.weblate.WeblateSetService.JOB_TYPE_CREATE;
import static org.snomed.simplex.weblate.WeblateSetService.PERCENTAGE_PROCESSED_START;

public class WeblateSetCreationService extends AbstractWeblateSetProcessingService {

	private final WeblateClientFactory weblateClientFactory;
	private final WeblateSetRepository weblateSetRepository;
	private final SnowstormClientFactory snowstormClientFactory;
	private final int labelBatchSize;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public WeblateSetCreationService(ProcessingContext processingContext, int labelBatchSize) {
		super(processingContext);
		weblateSetRepository = processingContext.weblateSetRepository();
		weblateClientFactory = processingContext.weblateClientFactory();
		snowstormClientFactory = processingContext.snowstormClientFactory();
		this.labelBatchSize = labelBatchSize;
	}

	public void createSet(WeblateTranslationSet translationSet) throws ServiceException {
		String codesystemShortName = translationSet.getCodesystem();
		ServiceHelper.requiredParameter("codesystem", codesystemShortName);
		ServiceHelper.requiredParameter("name", translationSet.getName());
		String refsetId = translationSet.getRefset();
		ServiceHelper.requiredParameter("refset", refsetId);
		ServiceHelper.requiredParameter("label", translationSet.getLabel());
		ServiceHelper.requiredParameter("ecl", translationSet.getEcl());
		ServiceHelper.requiredParameter("selectionCodesystem", translationSet.getSelectionCodesystem());

		Optional<WeblateTranslationSet> optional = weblateSetRepository.findByCodesystemAndLabelAndRefsetOrderByName(codesystemShortName, translationSet.getLabel(), refsetId);
		if (optional.isPresent()) {
			throw new ServiceExceptionWithStatusCode("A translation set with this label already exists.", HttpStatus.CONFLICT);
		}

		CodeSystem codeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(codesystemShortName);
		String languageCode = codeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset: " + refsetId, HttpStatus.NOT_FOUND);
		}
		translationSet.setLanguageCode(languageCode);

		WeblateClient weblateClient = weblateClientFactory.getClient();
		if (!weblateClient.isTranslationExistsSearchByLanguageRefset(translationSet.getLanguageCodeWithRefsetId())) {
			throw new ServiceExceptionWithStatusCode("Translation does not exist in Translation Tool, " +
				"please start language initialisation job or wait for it to finish.", HttpStatus.CONFLICT);
		}

		translationSet.setStatus(TranslationSetStatus.INITIALISING);
		translationSet.setPercentageProcessed(PERCENTAGE_PROCESSED_START);

		logger.info("Queueing Translation Tool Translation Set for creation {}/{}/{}", codesystemShortName, refsetId, translationSet.getLabel());
		weblateSetRepository.save(translationSet);

		queueJob(translationSet, JOB_TYPE_CREATE);
	}

	public void doCreateSet(WeblateTranslationSet translationSet, WeblateClient weblateClient, SnowstormClientFactory snowstormClientFactory) throws ServiceExceptionWithStatusCode {
		TimerUtil timerUtil = new TimerUtil("Adding label %s".formatted(translationSet.getLabel()));
		// Update status to processing
		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		weblateSetRepository.save(translationSet);

		SnowstormClient.ConceptIdStream conceptIdStream = getConceptIdStream(translationSet, snowstormClientFactory);

		String code;
		String compositeLabel = translationSet.getCompositeLabel();

		WeblateLabel weblateLabel = weblateClient.getCreateLabel(WeblateClient.COMMON_PROJECT, compositeLabel, translationSet.getName());

		List<String> codes = new ArrayList<>();
		int done = 0;
		while ((code = conceptIdStream.get()) != null) {
			codes.add(code);
			if (codes.size() == labelBatchSize) {
				bulkAddLabelsToBatch(compositeLabel, codes, weblateClient, weblateLabel);
				timerUtil.checkpoint("Completed batch");
				done += labelBatchSize;
				updateProcessingTotal(translationSet, done, conceptIdStream.getTotal());
			}
		}
		if (!codes.isEmpty()) {
			bulkAddLabelsToBatch(compositeLabel, codes, weblateClient, weblateLabel);
			updateProcessingTotal(translationSet, conceptIdStream.getTotal(), conceptIdStream.getTotal());
		}
		timerUtil.finish();

		translationSet.setStatus(TranslationSetStatus.READY);
		weblateSetRepository.save(translationSet);
	}

	private static SnowstormClient.ConceptIdStream getConceptIdStream(WeblateTranslationSet translationSet, SnowstormClientFactory snowstormClientFactory) throws ServiceExceptionWithStatusCode {
		String selectionCodesystemName = translationSet.getSelectionCodesystem();
		SnowstormClient snowstormClient;
		if (selectionCodesystemName.equals(SnowstormClientFactory.SNOMEDCT_DERIVATIVES)) {
			snowstormClient = snowstormClientFactory.getDerivativesClient();
		} else {
			snowstormClient = snowstormClientFactory.getClient();
		}
		CodeSystem selectionCodeSystem = snowstormClient.getCodeSystemOrThrow(selectionCodesystemName);
		return snowstormClient.getConceptIdStream(selectionCodeSystem.getBranchPath(), translationSet.getEcl());
	}

	private void updateProcessingTotal(WeblateTranslationSet translationSet, int done, int total) {
		if (total == 0) {
			total++;
		}
		translationSet.setPercentageProcessed((int) (((float) done / (float) total) * 100));
		translationSet.setSize(total);
		weblateSetRepository.save(translationSet);
	}

	private void bulkAddLabelsToBatch(String label, List<String> codes, WeblateClient weblateClient, WeblateLabel weblateLabel) throws ServiceExceptionWithStatusCode {
		logger.info("Adding batch of label:{} to {} units", label, codes.size());
		weblateClient.bulkAddLabels(WeblateClient.COMMON_PROJECT, weblateLabel.id(), codes);
		logger.info("Added label batch");
		codes.clear();
	}

}
