package org.snomed.simplex.service;

import org.snomed.simplex.domain.activity.Activity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ActivityRepository extends ElasticsearchRepository<Activity, String> {
}
