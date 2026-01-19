package org.snomed.simplex.weblate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import org.snomed.simplex.client.domain.*;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.util.FileUtils;
import org.snomed.simplex.weblate.domain.WeblateUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.snomed.simplex.weblate.WeblateClient.COMMON_PROJECT;
import static org.snomed.simplex.weblate.WeblateClient.SNOMEDCT_COMPONENT;

@Service
public class WeblateSnomedUpgradeService {

	public static final String TRANSLATION_TOOL_DEPENDENCY = "translation-tool-dependency-date";
	public static final String MAIN_BRANCH = "MAIN";
	public static final Pattern NUMBER_PATTERN = Pattern.compile(".*\",\"?([\\d]{6,18})\"?,\".*");
	public final Logger logger = LoggerFactory.getLogger(WeblateSnomedUpgradeService.class);

	private final WeblateClientFactory weblateClientFactory;
	private final SnowstormClientFactory snowstormClientFactory;
	private final WeblateDiagramService weblateDiagramService;
	private final CodeSystemService codeSystemService;

	public WeblateSnomedUpgradeService(WeblateClientFactory weblateClientFactory, WeblateDiagramService weblateDiagramService, SnowstormClientFactory snowstormClientFactory,
			CodeSystemService codeSystemService) {

		this.weblateClientFactory = weblateClientFactory;
		this.snowstormClientFactory = snowstormClientFactory;
		this.weblateDiagramService = weblateDiagramService;
		this.codeSystemService = codeSystemService;
	}

	public TranslationToolUpdatePlan getUpdatePlan(boolean initial, Integer upgradeToEffectiveTime) throws ServiceExceptionWithStatusCode {
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		Integer previousVersion = null;
		if (!initial) {
			// Fetch previous version
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
			throw new ServiceExceptionWithStatusCode(("No new version available. " +
				"Previous version is %s, latest version is %s").formatted(previousVersion, newVersionDate), HttpStatus.CONFLICT);
		}
		return new TranslationToolUpdatePlan(previousVersion, newVersionDate, newVersion, codeSystem);
	}

	private void insertNewConceptStubsIntoWeblate(TranslationToolUpdatePlan updatePlan, WeblateClient weblateClient) throws ServiceExceptionWithStatusCode {
		File existingSourceFile = null;
		File releaseFile = null;
		try {
			logger.info("Downloading existing SNOMED CT concept list from Translation Tool.");
			existingSourceFile = weblateClient.downloadSnomedSourceList();
			List<Long> workingList = getCurrentConceptList(existingSourceFile);
			Map<Long, String> newRows = new Long2ObjectOpenHashMap<>();

			logger.info("Downloading target release.");
			Pair<String, File> releaseFileNameAndFile = codeSystemService.downloadVersionPackage(updatePlan.codeSystem(), updatePlan.newVersion());
			releaseFile = releaseFileNameAndFile.getRight();
			logger.info("Loading hierarchy-sorted list from release file.");
			gatherNewRows(releaseFile, workingList, newRows);

			// - Recreate the source file, including the new rows and the inactive rows that are no longer part of the hierarchy.
			// We keep inactive rows because they may be part of current translation efforts for extensions that have not upgraded yet.
			logger.info("Uploading new list of SNOMED CT concepts to Translation Tool.");
			createNewSourceFileAndUpload(existingSourceFile, workingList, newRows, weblateClient);
		} finally {
			FileUtils.deleteOrLogWarning(existingSourceFile);
			FileUtils.deleteOrLogWarning(releaseFile);
		}
	}

	private void gatherNewRows(File releaseFile, List<Long> workingList, Map<Long, String> newRows) throws ServiceExceptionWithStatusCode {
		ComponentStore componentStore = new ComponentStore();
		LoadingProfile loadingProfile = LoadingProfile.light;

		// Component factory that records PTs
		ComponentStoreComponentFactoryWithPT componentFactory = new ComponentStoreComponentFactoryWithPT(componentStore, Concepts.US_LANG_REFSET);
		// Component factory that records inferred children
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
					// Add this concept
					int i = workingList.indexOf(previousConceptId);
					workingList.add(i + 1, currentConcept.getId());
					newRows.put(currentConcept.getId(), getSourceRow(currentConcept, componentFactory.getPt(currentConcept)));
				}

				// Sort children by PT and add to the stack
				addSortedChildren(currentConcept, componentFactory, nextConcepts);
				previousConceptId = currentConcept.getId();
			}
			if (coveredConcepts.size() % 10_000 == 0 && logger.isInfoEnabled()) {
				logger.info("Processed {} concepts", NumberFormat.getInstance().format(coveredConcepts.size()));
			}
		}
	}

	private static void addSortedChildren(ConceptImpl currentConcept, ComponentStoreComponentFactoryWithPT componentFactory, LinkedList<ConceptImpl> nextConcepts) {
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

	private void createNewSourceFileAndUpload(File existingSourceFile, List<Long> workingList, Map<Long, String> newRows, WeblateClient weblateClient) throws ServiceExceptionWithStatusCode {
		File newSourceFile = null;
		try {
			newSourceFile = File.createTempFile("new_source_file" + UUID.randomUUID(), ".csv");

			try (BufferedReader reader = new BufferedReader(new FileReader(existingSourceFile));
				 BufferedWriter writer = new BufferedWriter(new FileWriter(newSourceFile))) {

				String header = reader.readLine();
				if (!header.contains("source")) {
					throw new ServiceExceptionWithStatusCode("Unexpected header in existing Translation Tool source file: '%s'".formatted(header), HttpStatus.CONFLICT);
				}
				writer.write("source,target,context,developer_comments");// Copy header
				writer.newLine();

				for (Long code : workingList) {
					if (newRows.containsKey(code)) {
						writer.write(newRows.get(code));
					} else {
						String line = reader.readLine();
						assertExpectedLine(code, line);
						writer.write(line);
					}
					writer.newLine();
				}
			}

			// Upload new source file, force synchronous behaviour
			weblateClient.uploadTranslationFileAndWaitForProcessing(newSourceFile, "en");

		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to write source file.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		} finally {
			FileUtils.deleteOrLogWarning(newSourceFile);
		}
	}

	private void assertExpectedLine(Long code, String line) throws ServiceExceptionWithStatusCode {
		Long codeLong = getCodeFromWeblateRow(line);
		if (!code.equals(codeLong)) {
			throw new ServiceExceptionWithStatusCode("Expected existing code %s but found %s".formatted(code, codeLong), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private String getSourceRow(ConceptImpl currentConcept, String pt) {
		String code = currentConcept.getId().toString();
		String fsn = currentConcept.getFsn();
		return "\"%s\",\"%s\",%s,\"http://snomed.info/id/%s - %s\""
			.formatted(pt, pt, code, code, fsn);
	}

	private List<Long> getCurrentConceptList(File sourceFile) throws ServiceExceptionWithStatusCode {
		List<Long> workingList = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
			String line = reader.readLine();
			if (!line.replace("\"", "").startsWith("source,target,context")) {
				throw new ServiceExceptionWithStatusCode("Source file has unexpected header line. '%s'".formatted(line), HttpStatus.CONFLICT);
			}
			while ((line = reader.readLine()) != null) {
				Long codeLong = getCodeFromWeblateRow(line);
				if (codeLong != null) {
					workingList.add(codeLong);
				}
			}
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to read source file.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return workingList;
	}

	private Long getCodeFromWeblateRow(String line) {
		String[] values = line.split(",");
		String code = null;
		if (values.length == 4) {
			code = values[2].replace("\"", "");
		} else {
			// Some of the terms contain commas which are not escaped. A standard CVS reader can't deal with this, so using regex if colum count is high
			Matcher matcher = NUMBER_PATTERN.matcher(line);
			if (matcher.matches()) {
				code = matcher.group(1);
			}
		}
		if (code != null) {
			return Long.parseLong(code);
		}
		return null;
	}

	public ChangeSummary runSnomedUpgrade(TranslationToolUpdatePlan updatePlan, ContentJob contentJob) throws ServiceExceptionWithStatusCode {
		WeblateClient weblateClient = weblateClientFactory.getClient();
		SnowstormClient snowstormClient = snowstormClientFactory.getClient();

		contentJob.setRecordsTotal(100);// Percent rather than records
		contentJob.setRecordsProcessed(5);

		logger.info("Starting update of SNOMED CT concepts in Translation Tool. Current version:{}, New version:{}.", updatePlan.currentVersion(), updatePlan.newVersionDate());

		// 1. Insert new concepts into the list on Weblate
		insertNewConceptStubsIntoWeblate(updatePlan, weblateClient);
		contentJob.setRecordsProcessed(20);

		// 2. Update concept details of those concepts that are new or changed
		ChangeSummary changeSummary = updateDetailsOfNewOrChangedConcepts(updatePlan, contentJob, snowstormClient, weblateClient);

		snowstormClient.upsertBranchMetadata(MAIN_BRANCH, Map.of(TRANSLATION_TOOL_DEPENDENCY, updatePlan.newVersionDate().toString()));

		return changeSummary;
	}

	private ChangeSummary updateDetailsOfNewOrChangedConcepts(TranslationToolUpdatePlan updatePlan, ContentJob contentJob,
			SnowstormClient snowstormClient, WeblateClient weblateClient) throws ServiceExceptionWithStatusCode {

		// Get set of concepts that need updating
		List<Long> conceptUpdateFilter = null;// null if first run, then update everything
		if (updatePlan.currentVersion() != null) {
			logger.info("Getting list of new or updated concepts from Snowstorm.");
			conceptUpdateFilter = snowstormClient.getConceptChangeReport(updatePlan.newVersion().branchPath(), updatePlan.currentVersion());
			contentJob.setRecordsTotal(conceptUpdateFilter.size());
			logger.info("{} concepts need their details and diagrams updating.", conceptUpdateFilter.size());
		} else {
			logger.info("All concepts need their details and diagrams updating.");
		}
		logger.info("All concepts need their details and diagrams updating.");

		// Process weblate units in order
		UnitSupplier unitStream = weblateClient.getUnitStream(WeblateClient.COMMON_PROJECT, WeblateClient.SNOMEDCT_COMPONENT, 1, null);
		CodeSystem codeSystem = snowstormClient.getCodeSystemOrThrow(SnowstormClient.ROOT_CODESYSTEM);

		// Set working branch as version branch to ensure reading the correct version components
		codeSystem = new CodeSystem(codeSystem.getName(), codeSystem.getShortName(), codeSystem.getBranchPath());
		codeSystem.setSimplexWorkingBranch(updatePlan.newVersion().branchPath());

		int processed = 0;
		List<WeblateUnit> batch;
		while (!(batch = unitStream.getBatch(1_000)).isEmpty()) {

			// Filter batch
			List<Long> conceptIdsToProcess = new ArrayList<>();
			List<WeblateUnit> unitsToProcess = new ArrayList<>();
			for (WeblateUnit unit : batch) {
				long conceptCode = Long.parseLong(unit.getKey());
				if (conceptUpdateFilter == null || conceptUpdateFilter.contains(conceptCode)) {
					unitsToProcess.add(unit);
					conceptIdsToProcess.add(conceptCode);
				}
			}

			if (!conceptIdsToProcess.isEmpty()) {
				processed = updateUnits(conceptIdsToProcess, unitsToProcess, codeSystem, processed, snowstormClient, weblateClient);
			}
		}

		logger.info("Upgraded SNOMED CT source. {} units updated.", processed);
		return new ChangeSummary(0, processed, 0, processed);
	}

	private int updateUnits(List<Long> conceptsToProcess, List<WeblateUnit> unitsToProcess, CodeSystem codeSystem, int processed,
			SnowstormClient snowstormClient, WeblateClient weblateClient) {

		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(conceptsToProcess, codeSystem);
		Map<String, Concept> conceptMap = concepts.stream().collect(Collectors.toMap(Concept::getConceptId, Function.identity()));

		for (WeblateUnit unit : unitsToProcess) {
			String conceptId = unit.getKey();
			Concept concept = conceptMap.get(conceptId);
			String explanation = WeblateExplanationCreator.getMarkdown(concept);
			unit.setExplanation(explanation);
			weblateClient.patchUnitExplanation(unit.getId(), unit.getExplanation());
			weblateDiagramService.createWeblateScreenshot(unit, concept, COMMON_PROJECT, SNOMEDCT_COMPONENT, weblateClient);
			processed++;
			if (processed % 1_000 == 0 && logger.isInfoEnabled()) {
				logger.info("Processed {} units", String.format("%,d", processed));
			}
		}
		return processed;
	}
}
