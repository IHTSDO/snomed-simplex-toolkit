package org.snomed.simplex.snolate.sets;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.rest.pojos.RepairTranslationUnitIdsResponse;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

@Service
public class SnolateTranslationUnitMigrationService {

	private static final int ELASTIC_IO_CHUNK_SIZE = 1_000;
	private static final int MAX_WARNINGS_IN_RESPONSE = 100;

	private final SnolateSetRepository snolateSetRepository;
	private final SnolateTranslationSearchService translationSearchService;
	private final SnolateTranslationUnitRepository translationUnitRepository;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public SnolateTranslationUnitMigrationService(SnolateSetRepository snolateSetRepository,
			SnolateTranslationSearchService translationSearchService,
			SnolateTranslationUnitRepository translationUnitRepository) {
		this.snolateSetRepository = snolateSetRepository;
		this.translationSearchService = translationSearchService;
		this.translationUnitRepository = translationUnitRepository;
	}

	public RepairTranslationUnitIdsResponse repairTranslationUnitIds(@Nullable String codeSystem) {
		List<SnolateTranslationSet> sets = codeSystem != null && !codeSystem.isBlank()
				? snolateSetRepository.findByCodesystemOrderByName(codeSystem)
				: StreamSupport.stream(snolateSetRepository.findAll().spliterator(), false).toList();

		Set<String> compositeLanguageBuckets = new LinkedHashSet<>();
		for (SnolateTranslationSet set : sets) {
			compositeLanguageBuckets.add(set.getLanguageCodeWithRefsetId());
		}

		int mergedGroups = 0;
		int orphansDeleted = 0;
		int unchanged = 0;
		List<String> warnings = new ArrayList<>();

		for (String compositeLanguageCode : compositeLanguageBuckets) {
			logger.info("Repairing translation unit ids for composite language bucket {}", compositeLanguageCode);
			Map<String, List<TranslationUnit>> byCode = new LinkedHashMap<>();
			translationSearchService.forEachUnitByCompositeLanguageCode(compositeLanguageCode,
					unit -> byCode.computeIfAbsent(unit.getCode(), k -> new ArrayList<>()).add(unit));

			List<TranslationUnit> toSave = new ArrayList<>();
			List<String> orphanIds = new ArrayList<>();

			for (Map.Entry<String, List<TranslationUnit>> entry : byCode.entrySet()) {
				String code = entry.getKey();
				List<TranslationUnit> group = entry.getValue();
				if (!TranslationUnitMerger.needsRepair(compositeLanguageCode, code, group)) {
					unchanged++;
					continue;
				}
				List<String> groupWarnings = new ArrayList<>();
				TranslationUnitMerger.MergeResult result = TranslationUnitMerger.merge(
						compositeLanguageCode, code, group, groupWarnings);
				appendWarnings(warnings, groupWarnings);
				toSave.add(result.canonical());
				orphanIds.addAll(result.orphanDocumentIds());
				mergedGroups++;
			}

			saveInChunks(toSave);
			deleteInChunks(orphanIds);
			orphansDeleted += orphanIds.size();
			logger.info("Bucket {} complete: {} groups merged, {} orphan documents queued for deletion",
					compositeLanguageCode, toSave.size(), orphanIds.size());
		}

		logger.info("Translation unit id repair complete: {} buckets, {} merged, {} unchanged, {} orphans deleted",
				compositeLanguageBuckets.size(), mergedGroups, unchanged, orphansDeleted);

		return new RepairTranslationUnitIdsResponse(
				compositeLanguageBuckets.size(),
				mergedGroups,
				orphansDeleted,
				unchanged,
				warnings.size() > MAX_WARNINGS_IN_RESPONSE ? warnings.subList(0, MAX_WARNINGS_IN_RESPONSE) : warnings);
	}

	private void appendWarnings(List<String> target, List<String> groupWarnings) {
		for (String warning : groupWarnings) {
			if (target.size() >= MAX_WARNINGS_IN_RESPONSE) {
				return;
			}
			target.add(warning);
			logger.warn(warning);
		}
	}

	private void saveInChunks(List<TranslationUnit> units) {
		units.forEach(TranslationUnit::prepareForPersistence);
		for (int i = 0; i < units.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, units.size());
			translationUnitRepository.saveAll(units.subList(i, end));
		}
	}

	private void deleteInChunks(List<String> orphanIds) {
		for (int i = 0; i < orphanIds.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, orphanIds.size());
			translationUnitRepository.deleteAllById(orphanIds.subList(i, end));
		}
	}
}
