package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
public class SnolateTranslationUnitStore {

	private static final int ELASTIC_IO_CHUNK_SIZE = 1_000;

	private final SnolateTranslationUnitRepository translationUnitRepository;

	public SnolateTranslationUnitStore(SnolateTranslationUnitRepository translationUnitRepository) {
		this.translationUnitRepository = translationUnitRepository;
	}

	public Map<String, TranslationUnit> loadByCodes(String compositeLanguageCode, Collection<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return Map.of();
		}
		List<String> codeList = codes instanceof List<String> list ? list : new ArrayList<>(codes);
		Map<String, TranslationUnit> byCode = new HashMap<>();
		for (int i = 0; i < codeList.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, codeList.size());
			List<String> chunk = codeList.subList(i, end);
			List<String> ids = chunk.stream()
					.map(code -> TranslationUnit.canonicalDocumentId(compositeLanguageCode, code))
					.toList();
			StreamSupport.stream(translationUnitRepository.findAllById(ids).spliterator(), false)
					.forEach(unit -> {
						if (unit.getCode() != null) {
							byCode.put(unit.getCode(), unit);
						}
					});
		}
		return byCode;
	}

	public Optional<TranslationUnit> loadByCode(String compositeLanguageCode, String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(loadByCodes(compositeLanguageCode, List.of(code)).get(code));
	}

	public void save(TranslationUnit unit) {
		saveAll(List.of(unit));
	}

	public void saveAll(Collection<TranslationUnit> units) {
		if (units == null || units.isEmpty()) {
			return;
		}
		List<TranslationUnit> batch = units instanceof List<TranslationUnit> list ? list : new ArrayList<>(units);
		batch.forEach(TranslationUnit::prepareForPersistence);
		for (int i = 0; i < batch.size(); i += ELASTIC_IO_CHUNK_SIZE) {
			int end = Math.min(i + ELASTIC_IO_CHUNK_SIZE, batch.size());
			translationUnitRepository.saveAll(batch.subList(i, end));
		}
	}
}
