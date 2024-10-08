package org.snomed.simplex.service;

import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends ElasticsearchRepository<Activity, String> {

	Page<Activity> findActivitiesByCodesystemIsOrderByStartDateDesc(String codesystem, PageRequest pageRequest);

	Page<Activity> findActivitiesByCodesystemIsAndComponentIdOrderByStartDateDesc(String codesystem, String componentId, PageRequest pageRequest);

	Optional<Activity> findActivityByCodesystemAndStartDate(String codeSystem, Long startDate);

	void deleteAllByActivityType(String activityType);

	Page<Activity> findActivityByCodesystemAndActivityTypeOrderByStartDateDesc(String codeSystem, ActivityType activityType, PageRequest pageRequest);

	List<Activity> findActivitiesByEndDate(Long endDate);
}
