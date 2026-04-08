package org.snomed.simplex.snolate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.util.TimerUtil;
import org.snomed.simplex.translation.tool.TranslationSetStatus;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.snomed.simplex.snolate.sets.SnolateSetService.JOB_TYPE_CREATE;
import static org.snomed.simplex.snolate.sets.SnolateSetService.JOB_TYPE_DELETE;
import static org.snomed.simplex.snolate.sets.SnolateSetService.JOB_TYPE_REFRESH;
import static org.snomed.simplex.snolate.sets.SnolateSetService.PERCENTAGE_PROCESSED_START;

public class SnolateSetCreationService extends AbstractSnolateSetProcessingService {

	/** Chunk size for Elasticsearch batch reads/writes in {@link #bulkAddSetMembership}. */
	private static final int ELASTIC_IO_CHUNK_SIZE = 1_000;

	private final SnolateSetRepository snolateSetRepository;
	private final SnolateTranslationSourceRepository translationSourceRepository;
	private final SnolateTranslationUnitRepository translationUnitRepository;
	private final SnolateTranslationSearchService translationSearchService;
	private final SnowstormClientFactory snowstormClientFactory;
	private final int batchSize;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnolateSetCreationService(SnolateProcessingContext processingContext, int batchSize) {
		super(processingContext);
		this.snolateSetRepository = processingContext.snolateSetRepository();
		this.translationSourceRepository = processingContext.translationSourceRepository();
		this.translationUnitRepository = processingContext.translationUnitRepository();
		this.translationSearchService = processingContext.translationSearchService();
		this.snowstormClientFactory = processingContext.snowstormClientFactory();
		this.batchSize = batchSize;
	}

	public void createSet(SnolateTranslationSet translationSet) throws ServiceException {
		String codesystemShortName = translationSet.getCodesystem();
		ServiceHelper.requiredParameter("codesystem", codesystemShortName);
		ServiceHelper.requiredParameter("name", translationSet.getName());
		String refsetId = translationSet.getRefset();
		ServiceHelper.requiredParameter("refset", refsetId);
		ServiceHelper.requiredParameter("label", translationSet.getLabel());
		ServiceHelper.requiredParameter("ecl", translationSet.getEcl());
		ServiceHelper.requiredParameter("selectionCodesystem", translationSet.getSelectionCodesystem());

		Optional<SnolateTranslationSet> optional = snolateSetRepository.findByCodesystemAndLabelAndRefsetOrderByName(
				codesystemShortName, translationSet.getLabel(), refsetId);
		if (optional.isPresent()) {
			throw new ServiceExceptionWithStatusCode("A translation set with this label already exists.", HttpStatus.CONFLICT);
		}

		CodeSystem codeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(codesystemShortName);
		String languageCode = codeSystem.getTranslationLanguages().get(refsetId);
		if (languageCode == null) {
			throw new ServiceExceptionWithStatusCode("Language code not found for refset: " + refsetId, HttpStatus.NOT_FOUND);
		}
		translationSet.setLanguageCode(languageCode);
		translationSet.setInternationalEffectiveTime(codeSystem.getDependantVersionEffectiveTime());

		translationSet.setStatus(TranslationSetStatus.INITIALISING);
		translationSet.setPercentageProcessed(PERCENTAGE_PROCESSED_START);

		logger.info("Queueing Snolate translation set for creation {}/{}/{}", codesystemShortName, refsetId, translationSet.getLabel());
		snolateSetRepository.save(translationSet);

		queueJob(translationSet, JOB_TYPE_CREATE);
	}

	public void refreshSet(SnolateTranslationSet translationSet) throws ServiceException {
		if (translationSet.getStatus() == TranslationSetStatus.DELETING) {
			throw new ServiceExceptionWithStatusCode("Cannot refresh a translation set that is being deleted.", HttpStatus.CONFLICT);
		}

		CodeSystem codeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(translationSet.getCodesystem());
		translationSet.setInternationalEffectiveTime(codeSystem.getDependantVersionEffectiveTime());

		translationSet.setStatus(TranslationSetStatus.INITIALISING);
		translationSet.setPercentageProcessed(PERCENTAGE_PROCESSED_START);

		logger.info("Queueing Snolate translation set for refresh {}/{}/{}",
				translationSet.getCodesystem(), translationSet.getRefset(), translationSet.getLabel());
		snolateSetRepository.save(translationSet);

		queueJob(translationSet, JOB_TYPE_REFRESH);
	}

	public void queueDelete(SnolateTranslationSet translationSet) throws ServiceException {
		queueJob(translationSet, JOB_TYPE_DELETE);
	}

	public void doDeleteSet(SnolateTranslationSet translationSet) throws ServiceExceptionWithStatusCode {
		String compositeSetCode = translationSet.getCompositeSetCode();
		String compositeLang = translationSet.getLanguageCodeWithRefsetId();
		translationSearchService.forEachUnitInSet(compositeSetCode, compositeLang, u -> {
			LinkedHashSet<String> m = new LinkedHashSet<>(u.getMemberOf());
			if (m.remove(compositeSetCode)) {
				u.setMemberOf(m);
				translationUnitRepository.save(u);
			}
		});
		snolateSetRepository.delete(translationSet);
	}

	public void doRefreshSet(SnolateTranslationSet translationSet, SnowstormClientFactory snowstormClientFactory) throws ServiceExceptionWithStatusCode {
		String compositeSetCode = translationSet.getCompositeSetCode();
		TimerUtil timerUtil = new TimerUtil("Refreshing Snolate set %s".formatted(compositeSetCode));

		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		snolateSetRepository.save(translationSet);

		Set<String> currentConceptIds = new HashSet<>();
		translationSearchService.forEachUnitInSet(compositeSetCode, translationSet.getLanguageCodeWithRefsetId(),
				u -> currentConceptIds.add(u.getCode()));
		logger.info("Found {} translation units with set {}", currentConceptIds.size(), compositeSetCode);

		Set<String> newConceptIds = collectConceptIdsFromEcl(translationSet, snowstormClientFactory);
		logger.info("ECL returned {} concept IDs for Snolate set {}", newConceptIds.size(), compositeSetCode);

		List<String> toAdd = newConceptIds.stream().filter(id -> !currentConceptIds.contains(id)).toList();
		List<String> toRemove = currentConceptIds.stream().filter(id -> !newConceptIds.contains(id)).toList();
		logger.info("Refresh diff for {}: {} to add, {} to remove", compositeSetCode, toAdd.size(), toRemove.size());

		applyRemoveBatches(translationSet, toRemove, timerUtil);
		int added = applyAddBatches(translationSet, toAdd, timerUtil);
		int skippedAdds = toAdd.size() - added;
		if (skippedAdds > 0) {
			logger.info("Snolate refresh: {} concepts were not in translation_source and were skipped when adding set membership.", skippedAdds);
		}

		translationSet.setSize(newConceptIds.size());
		setProgressToComplete(translationSet);
		timerUtil.finish();
	}

	public void doCreateSet(SnolateTranslationSet translationSet, SnowstormClientFactory snowstormClientFactory) throws ServiceExceptionWithStatusCode {
		String compositeSetCode = translationSet.getCompositeSetCode();
		TimerUtil timerUtil = new TimerUtil("Adding Snolate set membership %s".formatted(compositeSetCode));

		translationSet.setStatus(TranslationSetStatus.PROCESSING);
		snolateSetRepository.save(translationSet);

		ConceptIdSource idSource = createConceptIdSource(translationSet, snowstormClientFactory);

		List<String> codes = new ArrayList<>();
		int done = 0;
		long skippedTotal = 0;
		String code;
		while ((code = idSource.next()) != null) {
			codes.add(code);
			if (codes.size() == batchSize) {
				skippedTotal += bulkAddSetMembership(translationSet, codes, timerUtil);
				done += batchSize;
				updateProcessingTotal(translationSet, done, idSource.getTotal());
			}
		}
		if (!codes.isEmpty()) {
			skippedTotal += bulkAddSetMembership(translationSet, codes, timerUtil);
			updateProcessingTotal(translationSet, idSource.getTotal(), idSource.getTotal());
		}
		if (skippedTotal > 0) {
			logger.warn("Snolate create set {}: skipped {} concept IDs not present in translation_source.",
					compositeSetCode, skippedTotal);
		}
		timerUtil.finish();

		translationSet.setSize(idSource.getTotal());
		setProgressToComplete(translationSet);
	}

	/** Collect all concept IDs from ECL into memory (same behavior as Weblate refresh). */
	private Set<String> collectConceptIdsFromEcl(SnolateTranslationSet translationSet, SnowstormClientFactory factory) throws ServiceExceptionWithStatusCode {
		ConceptIdSource source = createConceptIdSource(translationSet, factory);
		Set<String> ids = new HashSet<>();
		String id;
		while ((id = source.next()) != null) {
			ids.add(id);
		}
		return ids;
	}

	private void applyRemoveBatches(SnolateTranslationSet translationSet, List<String> toRemove, TimerUtil timerUtil) throws ServiceExceptionWithStatusCode {
		List<String> batch = new ArrayList<>();
		for (String id : toRemove) {
			batch.add(id);
			if (batch.size() == batchSize) {
				bulkRemoveSetMembership(translationSet, batch, timerUtil);
			}
		}
		if (!batch.isEmpty()) {
			bulkRemoveSetMembership(translationSet, batch, timerUtil);
		}
	}

	private int applyAddBatches(SnolateTranslationSet translationSet, List<String> toAdd, TimerUtil timerUtil) throws ServiceExceptionWithStatusCode {
		int added = 0;
		List<String> batch = new ArrayList<>();
		for (String id : toAdd) {
			batch.add(id);
			if (batch.size() == batchSize) {
				int n = batch.size();
				int skipped = bulkAddSetMembership(translationSet, batch, timerUtil);
				added += n - skipped;
			}
		}
		if (!batch.isEmpty()) {
			int n = batch.size();
			int skipped = bulkAddSetMembership(translationSet, batch, timerUtil);
			added += n - skipped;
		}
		return added;
	}

	protected ConceptIdSource createConceptIdSource(SnolateTranslationSet translationSet, SnowstormClientFactory factory) throws ServiceExceptionWithStatusCode {
		SnowstormClient.ConceptIdStream stream = getConceptIdStream(translationSet, factory);
		return new ConceptIdSource() {
			@Override
			public String next() {
				return stream.get();
			}

			@Override
			public int getTotal() {
				return stream.getTotal();
			}
		};
	}

	protected interface ConceptIdSource {
		String next();

		int getTotal();
	}

	private static SnowstormClient.ConceptIdStream getConceptIdStream(SnolateTranslationSet translationSet, SnowstormClientFactory snowstormClientFactory)
			throws ServiceExceptionWithStatusCode {
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

	private void updateProcessingTotal(SnolateTranslationSet translationSet, int done, int total) {
		if (total == 0) {
			total++;
		}
		translationSet.setPercentageProcessed((int) (((float) done / (float) total) * 100));
		translationSet.setSize(total);
		snolateSetRepository.save(translationSet);
	}

	/**
	 * @return number of requested codes not found in {@link TranslationSource} (skipped)
	 */
	private int bulkAddSetMembership(SnolateTranslationSet translationSet, List<String> codes, TimerUtil timerUtil) throws ServiceExceptionWithStatusCode {
		String compositeSetCode = translationSet.getCompositeSetCode();
		String compositeLang = translationSet.getLanguageCodeWithRefsetId();

		List<TranslationSource> rows = new ArrayList<>();
		for (int i = 0; i < codes.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, codes.size());
			rows.addAll(StreamSupport.stream(translationSourceRepository.findAllById(codes.subList(i, end)).spliterator(), false).toList());
		}
		Set<String> found = rows.stream().map(TranslationSource::getCode).collect(Collectors.toSet());
		int skipped = (int) codes.stream().filter(c -> !found.contains(c)).count();

		for (int i = 0; i < rows.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, rows.size());
			List<TranslationSource> srcChunk = rows.subList(i, end);
			List<String> chunkCodes = srcChunk.stream().map(TranslationSource::getCode).toList();
			List<TranslationUnit> existingBatch = translationUnitRepository.findAllByCompositeLanguageCodeAndCodeIn(compositeLang, chunkCodes);
			Map<String, TranslationUnit> byCode = existingBatch.stream()
					.collect(Collectors.toMap(TranslationUnit::getCode, Function.identity(), (a, b) -> a));

			List<TranslationUnit> toSave = new ArrayList<>(srcChunk.size());
			for (TranslationSource src : srcChunk) {
				TranslationUnit existing = byCode.get(src.getCode());
				TranslationUnit u;
				if (existing != null) {
					u = existing;
					LinkedHashSet<String> memberOf = new LinkedHashSet<>(u.getMemberOf());
					memberOf.add(compositeSetCode);
					u.setMemberOf(memberOf);
					u.setOrder(src.getOrder());
				} else {
					u = TranslationUnit.shellMember(src.getCode(), translationSet.getRefset(), translationSet.getLanguageCode(),
							compositeLang, src.getOrder(), compositeSetCode);
				}
				toSave.add(u);
			}
			for (int s = 0; s < toSave.size(); s += ELASTIC_IO_CHUNK_SIZE) {
				int sEnd = Math.min(s + ELASTIC_IO_CHUNK_SIZE, toSave.size());
				translationUnitRepository.saveAll(toSave.subList(s, sEnd));
			}
		}

		codes.clear();
		timerUtil.checkpoint("Added membership batch");
		return skipped;
	}

	private void bulkRemoveSetMembership(SnolateTranslationSet translationSet, List<String> codes, TimerUtil timerUtil) throws ServiceExceptionWithStatusCode {
		String compositeSetCode = translationSet.getCompositeSetCode();
		String compositeLang = translationSet.getLanguageCodeWithRefsetId();
		logger.info("Removing Snolate set membership:{} from {} concept IDs", compositeSetCode, codes.size());
		for (String code : codes) {
			translationUnitRepository.findByCodeAndCompositeLanguageCode(code, compositeLang).ifPresent(u -> {
				LinkedHashSet<String> m = new LinkedHashSet<>(u.getMemberOf());
				if (m.remove(compositeSetCode)) {
					u.setMemberOf(m);
					translationUnitRepository.save(u);
				}
			});
		}
		logger.info("Removed Snolate set membership batch");
		codes.clear();
		timerUtil.checkpoint("Removed membership batch");
	}
}
