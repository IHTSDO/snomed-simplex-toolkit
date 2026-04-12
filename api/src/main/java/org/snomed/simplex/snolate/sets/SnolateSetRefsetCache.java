package org.snomed.simplex.snolate.sets;

import org.snomed.simplex.config.SnolateTranslationSetCacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Caches {@link SnolateSetRepository#findByCodesystemAndRefsetOrderByName(String, String)} so repeated
 * {@link SnolateSetService#findSubsetOrThrow} / {@link SnolateSetService#findByCodeSystemAndRefset} calls
 * do not hit Elasticsearch every time. Evict when a set for that code system + refset is created, updated,
 * deleted, or after background processing completes.
 */
@Component
public class SnolateSetRefsetCache {

	private final SnolateSetRepository snolateSetRepository;

	public SnolateSetRefsetCache(SnolateSetRepository snolateSetRepository) {
		this.snolateSetRepository = snolateSetRepository;
	}

	@Cacheable(
			cacheNames = SnolateTranslationSetCacheConfig.SNOLATE_SETS_BY_CODE_SYSTEM_AND_REFSET,
			key = "#codeSystem + '|' + #refsetId",
			sync = true)
	public List<SnolateTranslationSet> listByCodeSystemAndRefset(String codeSystem, String refsetId) {
		return snolateSetRepository.findByCodesystemAndRefsetOrderByName(codeSystem, refsetId);
	}

	@CacheEvict(
			cacheNames = SnolateTranslationSetCacheConfig.SNOLATE_SETS_BY_CODE_SYSTEM_AND_REFSET,
			key = "#codeSystem + '|' + #refsetId")
	public void evictByCodeSystemAndRefset(String codeSystem, String refsetId) {
	}
}
