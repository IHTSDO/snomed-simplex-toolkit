package org.snomed.simplex.service.test;

import org.snomed.simplex.snolate.sets.SnolateSetRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TestSnolateSetRepository extends NoopRepository<SnolateTranslationSet> implements SnolateSetRepository {

	private final List<SnolateTranslationSet> sets = new ArrayList<>();

	@Override
	public <S extends SnolateTranslationSet> S save(S entity) {
		if (entity.getId() == null) {
			entity.setId(UUID.randomUUID().toString());
		}
		sets.removeIf(s -> entity.getId().equals(s.getId()));
		sets.add(entity);
		return entity;
	}

	@Override
	public Optional<SnolateTranslationSet> findById(String id) {
		return sets.stream().filter(s -> id.equals(s.getId())).findFirst();
	}

	public List<SnolateTranslationSet> getSets() {
		return sets;
	}

	@Override
	public List<SnolateTranslationSet> findByCodesystemOrderByName(String codesystem) {
		return List.of();
	}

	@Override
	public List<SnolateTranslationSet> findByCodesystemAndRefsetOrderByName(String codesystem, String refsetId) {
		return List.of();
	}

	@Override
	public Optional<SnolateTranslationSet> findByCodesystemAndLabelAndRefsetOrderByName(String codesystem, String label, String refset) {
		return Optional.empty();
	}
}
