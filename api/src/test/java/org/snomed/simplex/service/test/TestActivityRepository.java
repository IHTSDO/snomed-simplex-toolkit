package org.snomed.simplex.service.test;

import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.service.ActivityRepository;

import java.util.ArrayList;
import java.util.List;

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

}
