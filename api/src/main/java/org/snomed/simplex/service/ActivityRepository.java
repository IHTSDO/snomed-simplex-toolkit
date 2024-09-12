package org.snomed.simplex.service;

import org.snomed.simplex.domain.activity.Activity;
import org.springframework.context.annotation.Profile;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

public interface ActivityRepository extends ElasticsearchRepository<Activity, String> {
}
