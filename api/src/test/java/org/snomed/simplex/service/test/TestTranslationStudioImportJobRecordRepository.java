package org.snomed.simplex.service.test;

import org.snomed.simplex.snolate.domain.TranslationStudioImportJobRecord;
import org.snomed.simplex.snolate.domain.TranslationStudioImportJobRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class TestTranslationStudioImportJobRecordRepository extends NoopRepository<TranslationStudioImportJobRecord>
		implements TranslationStudioImportJobRecordRepository {

	private final List<TranslationStudioImportJobRecord> records = new ArrayList<>();

	@Override
	public <S extends TranslationStudioImportJobRecord> S save(S entity) {
		records.removeIf(record -> record.getId().equals(entity.getId()));
		records.add(entity);
		return entity;
	}

	public List<TranslationStudioImportJobRecord> getRecords() {
		return records;
	}

	@Override
	public Page<TranslationStudioImportJobRecord> findByCodesystemOrderByCreatedDesc(String codesystem, Pageable pageable) {
		return page(filterByCodesystem(codesystem), pageable);
	}

	@Override
	public Page<TranslationStudioImportJobRecord> findByCodesystemAndRefsetIdOrderByCreatedDesc(String codesystem, String refsetId, Pageable pageable) {
		return page(filterByCodesystem(codesystem).stream()
				.filter(record -> refsetId.equals(record.getRefsetId()))
				.toList(), pageable);
	}

	@Override
	public Optional<TranslationStudioImportJobRecord> findByCodesystemAndId(String codesystem, String id) {
		return records.stream()
				.filter(record -> codesystem.equals(record.getCodesystem()) && id.equals(record.getId()))
				.findFirst();
	}

	private List<TranslationStudioImportJobRecord> filterByCodesystem(String codesystem) {
		return records.stream()
				.filter(record -> codesystem.equals(record.getCodesystem()))
				.sorted(Comparator.comparing(TranslationStudioImportJobRecord::getCreated).reversed())
				.toList();
	}

	private static Page<TranslationStudioImportJobRecord> page(List<TranslationStudioImportJobRecord> filtered, Pageable pageable) {
		int start = Math.toIntExact(pageable.getOffset());
		int end = Math.min(start + pageable.getPageSize(), filtered.size());
		List<TranslationStudioImportJobRecord> content = start >= filtered.size() ? List.of() : filtered.subList(start, end);
		return new PageImpl<>(content, pageable, filtered.size());
	}
}
