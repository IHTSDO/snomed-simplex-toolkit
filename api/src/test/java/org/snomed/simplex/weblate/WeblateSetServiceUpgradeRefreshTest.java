package org.snomed.simplex.weblate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.rest.pojos.RefreshTranslationSetsAfterUpgradeResponse;
import org.snomed.simplex.service.SupportRegister;
import org.snomed.simplex.translation.service.TranslationService;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.snomed.simplex.weblate.domain.TranslationSubsetType;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jms.core.JmsTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeblateSetServiceUpgradeRefreshTest {

	private WeblateSetRepository repository;
	private SnowstormClientFactory snowstormClientFactory;
	private SnowstormClient snowstormClient;
	private CodeSystem codeSystem;
	private WeblateSetService service;

	@BeforeEach
	void setUp() {
		repository = mock(WeblateSetRepository.class);
		snowstormClientFactory = mock(SnowstormClientFactory.class);
		snowstormClient = mock(SnowstormClient.class);
		codeSystem = mock(CodeSystem.class);
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("TEST")).thenReturn(codeSystem);
		when(codeSystem.getDependantVersionEffectiveTime()).thenReturn(20250701);

		service = new WeblateSetService(repository, mock(WeblateClientFactory.class), snowstormClientFactory,
				mock(TranslationService.class), mock(SupportRegister.class), mock(TranslationLLMService.class),
				mock(JmsTemplate.class), "default", 100, new ObjectMapper());
	}

	@Test
	void refreshAllSetsAfterUpgrade_queuesOutdatedSets() throws ServiceException {
		WeblateTranslationSet outdated = setWithStatus(TranslationSetStatus.READY, 20250101);
		WeblateTranslationSet current = setWithStatus(TranslationSetStatus.READY, 20250701);
		WeblateTranslationSet busy = setWithStatus(TranslationSetStatus.UPGRADING, 20250101);
		when(repository.findByCodesystemOrderByName("TEST")).thenReturn(List.of(outdated, current, busy));

		RefreshTranslationSetsAfterUpgradeResponse response = service.refreshAllSetsAfterUpgrade("TEST");

		assertThat(response.queued()).isEqualTo(1);
		assertThat(response.skipped()).isEqualTo(2);
		assertThat(response.sets()).hasSize(1);
		assertThat(outdated.getStatus()).isEqualTo(TranslationSetStatus.QUEUED_FOR_UPGRADE);
		assertThat(outdated.getInternationalEffectiveTime()).isEqualTo(20250701);
		verify(repository).save(outdated);
	}

	@Test
	void refreshAllSetsAfterUpgrade_queuesFailedSetsWithOutdatedVersion() throws ServiceException {
		WeblateTranslationSet failed = setWithStatus(TranslationSetStatus.FAILED, 20250101);
		when(repository.findByCodesystemOrderByName("TEST")).thenReturn(List.of(failed));

		RefreshTranslationSetsAfterUpgradeResponse response = service.refreshAllSetsAfterUpgrade("TEST");

		assertThat(response.queued()).isEqualTo(1);
		assertThat(response.skipped()).isZero();
		assertThat(failed.getStatus()).isEqualTo(TranslationSetStatus.QUEUED_FOR_UPGRADE);
	}

	@Test
	void refreshAllSetsAfterUpgrade_skipsReadySetsAtCurrentVersion() throws ServiceException {
		WeblateTranslationSet current = setWithStatus(TranslationSetStatus.READY, 20250701);
		when(repository.findByCodesystemOrderByName("TEST")).thenReturn(List.of(current));

		RefreshTranslationSetsAfterUpgradeResponse response = service.refreshAllSetsAfterUpgrade("TEST");

		assertThat(response.queued()).isZero();
		assertThat(response.skipped()).isEqualTo(1);
		verify(repository, never()).save(any());
	}

	private WeblateTranslationSet setWithStatus(TranslationSetStatus status, Integer internationalEffectiveTime) {
		WeblateTranslationSet set = new WeblateTranslationSet("TEST", "123456789012345678", "Test Set", "test-set",
				"<< 138875005", TranslationSubsetType.SUB_TYPE, "TEST");
		set.setLanguageCode("fr");
		set.setStatus(status);
		set.setInternationalEffectiveTime(internationalEffectiveTime);
		return set;
	}
}
