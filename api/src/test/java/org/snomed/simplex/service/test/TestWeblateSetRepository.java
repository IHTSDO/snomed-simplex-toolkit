package org.snomed.simplex.service.test;

import org.snomed.simplex.weblate.WeblateSetRepository;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TestWeblateSetRepository extends NoopRepository<WeblateTranslationSet> implements WeblateSetRepository {

	private final List<WeblateTranslationSet> sets;

	public TestWeblateSetRepository() {
		sets = new ArrayList<>();
	}

	@Override
	public <S extends WeblateTranslationSet> S save(S entity) {
		sets.add(entity);
		return entity;
	}

	public List<WeblateTranslationSet> getSets() {
		return sets;
	}

	@Override
	public List<WeblateTranslationSet> findByCodesystemOrderByName(String codesystem) {
		return List.of();
	}

	@Override
	public List<WeblateTranslationSet> findByCodesystemAndRefsetOrderByName(String codesystem, String refsetId) {
		return List.of();
	}

	@Override
	public Optional<WeblateTranslationSet> findByCodesystemAndLabelAndRefsetOrderByName(String codesystem, String label, String refset) {
		return Optional.empty();
	}
}
