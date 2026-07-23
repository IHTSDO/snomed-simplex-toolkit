package org.snomed.simplex.snolate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.RefreshTranslationSetsAfterUpgradeResponse;
import org.snomed.simplex.rest.pojos.RepairTranslationSetSizesResponse;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.translation.TranslationLLMService;
import org.snomed.simplex.translation.tool.TranslationSetStatus;
import org.snomed.simplex.translation.tool.TranslationSubsetType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnolateSetServiceTest {

	private SnolateSetRepository snolateSetRepository;
	private SnolateSetRefsetCache snolateSetRefsetCache;
	private SnolateTranslationSearchService translationSearchService;
	private SnowstormClientFactory snowstormClientFactory;
	private SnowstormClient snowstormClient;
	private SnolateSetService snolateSetService;

	@BeforeEach
	void setUp() throws Exception {
		snolateSetRepository = mock();
		snolateSetRefsetCache = mock();
		translationSearchService = mock();
		snowstormClientFactory = mock();
		snowstormClient = mock();
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);

		snolateSetService = new SnolateSetService(snolateSetRepository, snolateSetRefsetCache, snowstormClientFactory,
				mock(), mock(), translationSearchService, mock(TranslationLLMService.class), mock(SupportRegister.class),
				mock(JmsTemplate.class), "test", 10, new ObjectMapper());
	}

	@Test
	void refreshAllSetsAfterUpgrade_queuesOutdatedSetsAndSkipsCurrentReadyAndBusy() throws ServiceException {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test-user", "n/a"));

		CodeSystem codeSystem = new CodeSystem("Test", "SNOMEDCT-TEST", "MAIN/SNOMEDCT-TEST");
		codeSystem.setDependantVersionEffectiveTime(20250701);
		when(snowstormClient.getCodeSystemOrThrow("SNOMEDCT-TEST")).thenReturn(codeSystem);

		SnolateTranslationSet outdatedReady = createSet("outdated", TranslationSetStatus.READY, 20240101);
		SnolateTranslationSet currentReady = createSet("current", TranslationSetStatus.READY, 20250701);
		SnolateTranslationSet busy = createSet("busy", TranslationSetStatus.UPGRADING, 20240101);
		SnolateTranslationSet failedOutdated = createSet("failed", TranslationSetStatus.FAILED, 20240101);

		when(snolateSetRepository.findByCodesystemOrderByName("SNOMEDCT-TEST"))
				.thenReturn(List.of(outdatedReady, currentReady, busy, failedOutdated));

		RefreshTranslationSetsAfterUpgradeResponse response = snolateSetService.refreshAllSetsAfterUpgrade("SNOMEDCT-TEST");

		assertThat(response.queued()).isEqualTo(2);
		assertThat(response.skipped()).isEqualTo(2);
		assertThat(response.sets()).containsExactly(outdatedReady, failedOutdated);
		assertThat(outdatedReady.getStatus()).isEqualTo(TranslationSetStatus.QUEUED_FOR_UPGRADE);
		assertThat(failedOutdated.getStatus()).isEqualTo(TranslationSetStatus.QUEUED_FOR_UPGRADE);
		assertThat(outdatedReady.getInternationalEffectiveTime()).isEqualTo(20250701);
		verify(snolateSetRefsetCache, times(2)).evictByCodeSystemAndRefset("SNOMEDCT-TEST", "100");

		SecurityContextHolder.clearContext();
	}

	@Test
	void repairSetSizes_updatesStoredSizeWhenElasticsearchCountDiffers() {
		SnolateTranslationSet wrongSize = createSet("wrong", TranslationSetStatus.READY, 20240101);
		wrongSize.setSize(100);
		when(snolateSetRepository.findByCodesystemOrderByName("SNOMEDCT-TEST")).thenReturn(List.of(wrongSize));
		when(translationSearchService.countUnitsInSet(eq("TEST_100_wrong"), eq("en-100"))).thenReturn(42L);

		RepairTranslationSetSizesResponse response = snolateSetService.repairSetSizes("SNOMEDCT-TEST");

		assertThat(response.repaired()).isEqualTo(1);
		assertThat(response.unchanged()).isZero();
		assertThat(response.skipped()).isZero();
		assertThat(response.changes()).hasSize(1);
		assertThat(response.changes().get(0).oldSize()).isEqualTo(100);
		assertThat(response.changes().get(0).newSize()).isEqualTo(42);
		assertThat(wrongSize.getSize()).isEqualTo(42);
		verify(snolateSetRepository).save(wrongSize);
		verify(snolateSetRefsetCache).evictByCodeSystemAndRefset("SNOMEDCT-TEST", "100");
	}

	@Test
	void repairSetSizes_skipsBusySetsAndLeavesCorrectSizesUnchanged() {
		SnolateTranslationSet busy = createSet("busy", TranslationSetStatus.UPGRADING, 20240101);
		busy.setSize(10);
		SnolateTranslationSet correct = createSet("correct", TranslationSetStatus.READY, 20240101);
		correct.setSize(5);
		when(snolateSetRepository.findByCodesystemOrderByName("SNOMEDCT-TEST")).thenReturn(List.of(busy, correct));
		when(translationSearchService.countUnitsInSet(eq("TEST_100_correct"), eq("en-100"))).thenReturn(5L);

		RepairTranslationSetSizesResponse response = snolateSetService.repairSetSizes("SNOMEDCT-TEST");

		assertThat(response.repaired()).isZero();
		assertThat(response.unchanged()).isEqualTo(1);
		assertThat(response.skipped()).isEqualTo(1);
		assertThat(response.changes()).isEmpty();
		verify(snolateSetRepository, never()).save(busy);
		verify(snolateSetRepository, never()).save(correct);
		verify(snolateSetRefsetCache, never()).evictByCodeSystemAndRefset(eq("SNOMEDCT-TEST"), eq("100"));
	}

	private static SnolateTranslationSet createSet(String label, TranslationSetStatus status, Integer internationalEffectiveTime) {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-TEST", "100", "Subset", label, "<<404684003",
				TranslationSubsetType.ECL, "SNOMEDCT-TEST");
		set.setId(label + "-id");
		set.setLanguageCode("en");
		set.setStatus(status);
		set.setInternationalEffectiveTime(internationalEffectiveTime);
		return set;
	}
}
