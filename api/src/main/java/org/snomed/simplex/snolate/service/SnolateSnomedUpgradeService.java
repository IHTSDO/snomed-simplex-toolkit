package org.snomed.simplex.snolate.service;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.commons.lang3.tuple.Pair;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.snomedboot.factory.implementation.HighLevelComponentFactoryAdapterImpl;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentStore;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ConceptImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.Branch;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemVersion;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.rf2.RF2LoadingComponentFactoryWithPT;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.util.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class SnolateSnomedUpgradeService {

	public static final String TRANSLATION_TOOL_DEPENDENCY = "translation-tool-dependency-date";
	public static final String MAIN_BRANCH = "MAIN";
	public final Logger logger = LoggerFactory.getLogger(SnolateSnomedUpgradeService.class);

	private final SnolateTranslationSourceRepository translationSourceRepository;
	private final SnowstormClientFactory snowstormClientFactory;
	private final CodeSystemService codeSystemService;
	private final ContentProcessingJobService jobService;

	@Value("${snolate.snomed.upgrade.enabled:false}")
	private boolean upgradeEnabled;

	public SnolateSnomedUpgradeService(SnolateTranslationSourceRepository translationSourceRepository, SnowstormClientFactory snowstormClientFactory,
			CodeSystemService codeSystemService, ContentProcessingJobService jobService) {

		this.translationSourceRepository = translationSourceRepository;
		this.snowstormClientFactory = snowstormClientFactory;
		this.codeSystemService = codeSystemService;
		this.jobService = jobService;
	}

	@Scheduled(cron = "${snolate.snomed.upgrade.check.cron}")
	public void scheduledSnomedUpgrade() {
		if (!upgradeEnabled) {
			logger.debug("Scheduled SNOMED CT upgrade is disabled (snolate.snomed.upgrade.enabled=false).");
			return;
		}
		logger.info("Running scheduled Snolate SNOMED CT upgrade.");
		try {
			runUpdate(false, "Scheduled SNOMED CT upgrade in Snolate", null);
		} catch (ServiceExceptionWithStatusCode e) {
			logger.warn("Scheduled Snolate SNOMED upgrade did not run: {}", e.getMessage());
		}
	}

	public TranslationToolUpdatePlan runUpdate(boolean initial, String message, Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		TranslationToolUpdatePlan updatePlan = getUpdatePlan(initial, upgradeToEffectiveTime);
		if (updatePlan == null) {
			return null;
		}

		CodeSystem rootCodeSystem = snowstormClientFactory.getClient().getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);
		Activity activity = new Activity(CodeSystem.SNOMEDCT, ComponentType.TRANSLATION,
				initial ? ActivityType.SNOLATE_SNOMED_INITIALISATION : ActivityType.SNOLATE_SNOMED_UPGRADE);
		ContentJob contentJob = new ContentJob(rootCodeSystem, message, null);
		jobService.queueContentJob(contentJob, null, activity, job -> runSnomedUpgrade(updatePlan, job));

		return updatePlan;
	}

	public TranslationToolUpdatePlan getUpdatePlan(boolean initial, Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		Integer previousVersion = null;
		if (!initial) {
			Branch branch = snowstormClient.getBranchOrThrow(MAIN_BRANCH);
			String translationToolDependency = branch.getMetadataValue(TRANSLATION_TOOL_DEPENDENCY);
			if (translationToolDependency == null || translationToolDependency.isEmpty()) {
				throw new ServiceExceptionWithStatusCode("%s is not set on branch %s.".formatted(TRANSLATION_TOOL_DEPENDENCY, MAIN_BRANCH), HttpStatus.CONFLICT);
			}
			previousVersion = Integer.parseInt(translationToolDependency);
		}
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);
		Integer newVersionDate;
		CodeSystemVersion newVersion;
		if (upgradeToEffectiveTime != null) {
			if (previousVersion != null && previousVersion > upgradeToEffectiveTime) {
				throw new ServiceExceptionWithStatusCode(("Can not downgrade. " +
					"Requested version %s is earlier than current version %s.").formatted(upgradeToEffectiveTime, previousVersion), HttpStatus.CONFLICT);
			}
			List<CodeSystemVersion> versions = snowstormClient.getVersions(codeSystem);
			Optional<CodeSystemVersion> first = versions.stream().filter(version -> version.effectiveDate().equals(upgradeToEffectiveTime)).findFirst();
			if (first.isPresent()) {
				newVersionDate = upgradeToEffectiveTime;
				newVersion = first.get();
			} else {
				throw new ServiceExceptionWithStatusCode(("Requested version %s not found.").formatted(upgradeToEffectiveTime), HttpStatus.CONFLICT);
			}
		} else {
			CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
			newVersionDate = latestVersion.effectiveDate();
			newVersion = latestVersion;
		}
		if (newVersionDate.equals(previousVersion)) {
			logger.info("No new SNOMED version available. Previous version is {}, latest version is {}.", previousVersion, newVersionDate);
			return null;
		}
		return new TranslationToolUpdatePlan(previousVersion, newVersionDate, newVersion, codeSystem);
	}

	private int insertNewConceptStubsIntoSnolate(TranslationToolUpdatePlan updatePlan) throws ServiceExceptionWithStatusCode {
		File releaseFile = null;
		try {
			logger.info("Loading existing SNOMED CT concept list from persistence.");
			List<Long> workingList = getCurrentConceptListFromPersistence();
			Map<Long, String> newRows = new Long2ObjectOpenHashMap<>();

			logger.info("Downloading target release.");
			Pair<String, File> releaseFileNameAndFile = codeSystemService.downloadVersionPackage(updatePlan.codeSystem(), updatePlan.newVersion());
			releaseFile = releaseFileNameAndFile.getRight();
			logger.info("Loading hierarchy-sorted list from release file.");
			gatherNewRows(releaseFile, workingList, newRows);

			logger.info("Saving new list of SNOMED CT concepts to persistence.");
			persistTranslationSources(workingList, newRows);
			return newRows.size();
		} finally {
			FileUtils.deleteOrLogWarning(releaseFile);
		}
	}

	private List<Long> getCurrentConceptListFromPersistence() {
		return new LongArrayList(translationSourceRepository.findAllByOrderByOrderAsc().stream()
				.map(TranslationSource::getCode)
				.map(Long::parseLong)
				.toList());
	}

	private void persistTranslationSources(List<Long> workingList, Map<Long, String> newRows) throws ServiceExceptionWithStatusCode {
		Map<String, TranslationSource> existingByCode = StreamSupport.stream(translationSourceRepository.findAll().spliterator(), false)
				.collect(Collectors.toMap(TranslationSource::getCode, Function.identity()));
		List<TranslationSource> toSave = new ArrayList<>();
		int order = 0;
		for (Long code : workingList) {
			String codeStr = code.toString();
			if (newRows.containsKey(code)) {
				toSave.add(new TranslationSource(codeStr, newRows.get(code), order));
			} else {
				TranslationSource row = existingByCode.get(codeStr);
				if (row == null) {
					throw new ServiceExceptionWithStatusCode("Expected existing TranslationSource for code %s".formatted(codeStr), HttpStatus.INTERNAL_SERVER_ERROR);
				}
				row.setOrder(order);
				toSave.add(row);
			}
			order++;
		}
		translationSourceRepository.saveAll(toSave);
	}

	private void gatherNewRows(File releaseFile, List<Long> workingList, Map<Long, String> newRows) throws ServiceExceptionWithStatusCode {
		ComponentStore componentStore = new ComponentStore();
		LoadingProfile loadingProfile = LoadingProfile.light;

		RF2LoadingComponentFactoryWithPT componentFactory = new RF2LoadingComponentFactoryWithPT(componentStore, Concepts.US_LANG_REFSET);
		HighLevelComponentFactoryAdapterImpl highLevelComponentFactoryAdapter = new HighLevelComponentFactoryAdapterImpl(loadingProfile, componentFactory, componentFactory);

		try {
			try (FileInputStream releaseZip = new FileInputStream(releaseFile)) {
				new ReleaseImporter().loadSnapshotReleaseFiles(releaseZip, loadingProfile, highLevelComponentFactoryAdapter, false);
			}
		} catch (IOException | ReleaseImportException e) {
			throw new ServiceExceptionWithStatusCode("Failed to load SNOMED from release file %s".formatted(releaseFile.getName()), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
		Map<Long, ConceptImpl> allConceptMap = componentStore.getConcepts();
		ConceptImpl rootConcept = allConceptMap.get(Long.parseLong(Concepts.ROOT_SNOMEDCT));
		Set<Long> coveredConcepts = new LongOpenHashSet();

		LinkedList<ConceptImpl> nextConcepts = new LinkedList<>();
		nextConcepts.add(rootConcept);
		Long previousConceptId = rootConcept.getId();
		while (!nextConcepts.isEmpty()) {
			ConceptImpl currentConcept = nextConcepts.remove();
			if (coveredConcepts.add(currentConcept.getId())) {
				if (!workingList.contains(currentConcept.getId())) {
					int i = workingList.indexOf(previousConceptId);
					workingList.add(i + 1, currentConcept.getId());
					newRows.put(currentConcept.getId(), componentFactory.getPt(currentConcept));
				}

				addSortedChildren(currentConcept, componentFactory, nextConcepts);
				previousConceptId = currentConcept.getId();
			}
			if (coveredConcepts.size() % 10_000 == 0 && logger.isInfoEnabled()) {
				logger.info("Processed {} concepts", NumberFormat.getInstance().format(coveredConcepts.size()));
			}
		}
	}

	private static void addSortedChildren(ConceptImpl currentConcept, RF2LoadingComponentFactoryWithPT componentFactory, LinkedList<ConceptImpl> nextConcepts) {
		Set<org.ihtsdo.otf.snomedboot.domain.Concept> inferredChildren = currentConcept.getInferredChildren();
		List<Pair<String, ConceptImpl>> sortedChildren = new ArrayList<>(inferredChildren.size());
		for (org.ihtsdo.otf.snomedboot.domain.Concept inferredChild : inferredChildren) {
			if (inferredChild instanceof ConceptImpl childImpl) {
				sortedChildren.add(Pair.of(componentFactory.getPt(childImpl).toLowerCase(), childImpl));
			}
		}
		Comparator<Pair<String, ConceptImpl>> comparing = Comparator.comparing(Pair::getLeft);
		sortedChildren.sort(comparing.reversed());
		for (Pair<String, ConceptImpl> sortedChild : sortedChildren) {
			nextConcepts.addFirst(sortedChild.getRight());
		}
	}

	public ChangeSummary runSnomedUpgrade(TranslationToolUpdatePlan updatePlan, ContentJob contentJob) throws ServiceExceptionWithStatusCode {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		contentJob.setRecordsTotal(100);
		contentJob.setRecordsProcessed(5);

		logger.info("Starting update of SNOMED CT concepts in Snolate. Current version:{}, New version:{}.",
				updatePlan.currentVersion(), updatePlan.newVersionDate());

		int added = insertNewConceptStubsIntoSnolate(updatePlan);
		int newTotal = (int) translationSourceRepository.count();
		contentJob.setRecordsProcessed(100);
		logger.info("Snolate SNOMED CT source updated. {} new concepts persisted.", added);

		snowstormClient.upsertBranchMetadata(MAIN_BRANCH, Map.of(TRANSLATION_TOOL_DEPENDENCY, updatePlan.newVersionDate().toString()));

		return new ChangeSummary(added, 0, 0, newTotal);
	}
}
