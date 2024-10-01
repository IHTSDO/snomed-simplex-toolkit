package org.snomed.simplex.service.test;

import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.service.ActivityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TestActivityRepository extends NoopRepository<Activity> implements ActivityRepository {

	private final List<Activity> activities;

	public TestActivityRepository() {
		activities = new ArrayList<>();
	}

	@Override
	public <S extends Activity> S save(S entity) {
		activities.add(entity);
		return entity;
	}

	public List<Activity> getActivities() {
		return activities;
	}

	@Override
	public Page<Activity> findActivitiesByCodesystemIsOrderByStartDateDesc(String codesystem, PageRequest pageRequest) {
		return null;
	}

	@Override
	public Page<Activity> findActivitiesByCodesystemIsAndComponentIdOrderByStartDateDesc(String codesystem, String componentId, PageRequest pageRequest) {
		return null;
	}

	@Override
	public Optional<Activity> findActivityByCodesystemAndStartDate(String codeSystem, Long startDate) {
		return Optional.empty();
	}

	@Override
	public void deleteAllByActivityType(String activityType) {
	}
}
