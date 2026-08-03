package org.snomed.simplex.snolate.sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * Copies {@link TranslationSource#getOrder()} onto {@link TranslationUnit#setOrder(int)}.
 */
public final class TranslationUnitOrderSync {

	private static final Logger logger = LoggerFactory.getLogger(TranslationUnitOrderSync.class);

	private static final int ELASTIC_IO_CHUNK_SIZE = 1_000;

	private TranslationUnitOrderSync() {
	}

	public static boolean applyIfChanged(TranslationUnit unit, TranslationSource source) {
		if (unit == null || source == null) {
			return false;
		}
		if (unit.getOrder() == source.getOrder()) {
			return false;
		}
		unit.setOrder(source.getOrder());
		return true;
	}

	public static List<TranslationUnit> syncBatch(Collection<TranslationUnit> units,
			SnolateTranslationSourceRepository translationSourceRepository) {
		if (units == null || units.isEmpty()) {
			return List.of();
		}
		List<TranslationUnit> unitList = units instanceof List<TranslationUnit> list ? list : new ArrayList<>(units);
		Map<String, TranslationSource> sourcesByCode = loadSourcesByCodes(unitList, translationSourceRepository);
		List<TranslationUnit> changed = new ArrayList<>();
		for (TranslationUnit unit : unitList) {
			if (applyIfChanged(unit, sourcesByCode.get(unit.getCode()))) {
				changed.add(unit);
			}
		}
		return changed;
	}

	public static int syncAllUnits(SnolateTranslationSearchService translationSearchService,
			SnolateTranslationSourceRepository translationSourceRepository,
			SnolateTranslationUnitStore translationUnitStore) {
		List<TranslationUnit> batch = new ArrayList<>(ELASTIC_IO_CHUNK_SIZE);
		int[] updatedTotal = {0};
		Consumer<TranslationUnit> consumer = unit -> {
			batch.add(unit);
			if (batch.size() >= ELASTIC_IO_CHUNK_SIZE) {
				updatedTotal[0] += flushBatch(batch, translationSourceRepository, translationUnitStore);
			}
		};
		translationSearchService.forEachTranslationUnit(consumer);
		updatedTotal[0] += flushBatch(batch, translationSourceRepository, translationUnitStore);
		logger.info("Synced TranslationUnit order from TranslationSource for {} unit(s).", updatedTotal[0]);
		return updatedTotal[0];
	}

	private static int flushBatch(List<TranslationUnit> batch,
			SnolateTranslationSourceRepository translationSourceRepository,
			SnolateTranslationUnitStore translationUnitStore) {
		if (batch.isEmpty()) {
			return 0;
		}
		List<TranslationUnit> changed = syncBatch(batch, translationSourceRepository);
		if (!changed.isEmpty()) {
			translationUnitStore.saveAll(changed);
		}
		batch.clear();
		return changed.size();
	}

	private static Map<String, TranslationSource> loadSourcesByCodes(List<TranslationUnit> units,
			SnolateTranslationSourceRepository translationSourceRepository) {
		List<String> codes = units.stream().map(TranslationUnit::getCode).distinct().toList();
		Map<String, TranslationSource> sourcesByCode = new HashMap<>();
		for (int i = 0; i < codes.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, codes.size());
			StreamSupport.stream(translationSourceRepository.findAllById(codes.subList(i, end)).spliterator(), false)
					.forEach(source -> sourcesByCode.put(source.getCode(), source));
		}
		return sourcesByCode;
	}
}
