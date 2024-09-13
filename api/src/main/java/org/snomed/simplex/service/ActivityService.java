package org.snomed.simplex.service;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

	private final ActivityRepository repository;

	public ActivityService(ActivityRepository repository) {
		this.repository = repository;
	}

	public <T> T recordActivity(String codeSystem, ComponentType componentType, ActivityType activityType,
			ServiceCallable<T> callable) throws ServiceException {

		Activity activity = new Activity(SecurityUtil.getUsername(), codeSystem, componentType, activityType);
		try {
			return callable.call();
		} catch (ServiceException e) {
			activity.exception(e);
			throw e;
		} finally {
			manualSaveActivity(activity);
		}
	}

	public void manualSaveActivity(Activity activity) {
		activity.end();
		repository.save(activity);
	}

	public org.snomed.simplex.domain.Page<Activity> findActivities(String codesystem, PageRequest pageRequest) {
		org.springframework.data.domain.Page<Activity> springPage = repository.findActivitiesByCodesystemIsOrderByStartDateDesc(codesystem, pageRequest);
		return new Page<>(springPage);
	}

}
