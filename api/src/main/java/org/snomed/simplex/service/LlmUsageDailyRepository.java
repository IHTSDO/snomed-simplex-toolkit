package org.snomed.simplex.service;

import org.snomed.simplex.ai.LlmUsageDaily;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface LlmUsageDailyRepository extends ElasticsearchRepository<LlmUsageDaily, String> {

	List<LlmUsageDaily> findByDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(String startDate, String endDate);

	List<LlmUsageDaily> findByCodesystemAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
			String codesystem, String startDate, String endDate);

	List<LlmUsageDaily> findByModelAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
			String model, String startDate, String endDate);

	List<LlmUsageDaily> findByCodesystemAndModelAndDateGreaterThanEqualAndDateLessThanEqualOrderByDateDesc(
			String codesystem, String model, String startDate, String endDate);

	List<LlmUsageDaily> findAllByOrderByDateDesc();

	List<LlmUsageDaily> findByCodesystemOrderByDateDesc(String codesystem);

	List<LlmUsageDaily> findByModelOrderByDateDesc(String model);

	List<LlmUsageDaily> findByCodesystemAndModelOrderByDateDesc(String codesystem, String model);
}
