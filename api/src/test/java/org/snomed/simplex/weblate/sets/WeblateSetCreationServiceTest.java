package org.snomed.simplex.weblate.sets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.weblate.WeblateAdminClient;
import org.snomed.simplex.weblate.WeblateClient;
import org.snomed.simplex.weblate.WeblateClientFactory;
import org.snomed.simplex.weblate.WeblateSetRepository;
import org.snomed.simplex.weblate.TranslationLLMService;
import org.snomed.simplex.weblate.domain.TranslationSetStatus;
import org.snomed.simplex.weblate.domain.TranslationSubsetType;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.http.HttpStatus;
import org.springframework.jms.core.JmsTemplate;

import java.util.HashMap;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeblateSetCreationServiceTest {

	@Test
	void refreshSetForUpgrade_setsQueuedForUpgradeAndQueuesJob() throws ServiceException {
		WeblateSetRepository repository = mock();
		SnowstormClientFactory snowstormClientFactory = mock();
		SnowstormClient snowstormClient = mock();
		CodeSystem codeSystem = mock();
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("TEST")).thenReturn(codeSystem);
		when(codeSystem.getDependantVersionEffectiveTime()).thenReturn(20250701);

		WeblateSetCreationService service = createService(repository, snowstormClientFactory);
		WeblateTranslationSet set = readySet();

		service.refreshSetForUpgrade(set);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.QUEUED_FOR_UPGRADE);
		assertThat(set.getInternationalEffectiveTime()).isEqualTo(20250701);
		verify(repository).save(set);
	}

	@Test
	void refreshSetForUpgrade_rejectsBusySet() {
		WeblateSetRepository repository = mock();
		SnowstormClientFactory snowstormClientFactory = mock();
		WeblateSetCreationService service = createService(repository, snowstormClientFactory);
		WeblateTranslationSet set = readySet();
		set.setStatus(TranslationSetStatus.UPGRADING);

		assertThatThrownBy(() -> service.refreshSetForUpgrade(set))
				.isInstanceOf(ServiceExceptionWithStatusCode.class)
				.extracting("httpStatus")
				.isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void doRefreshSet_transitionsQueuedForUpgradeToUpgradingThenReady() throws ServiceExceptionWithStatusCode {
		WeblateSetRepository repository = mock();
		WeblateClientFactory weblateClientFactory = mock();
		WeblateClient weblateClient = mock();
		WeblateAdminClient weblateAdminClient = mock();
		when(weblateClientFactory.getClient()).thenReturn(weblateClient);
		when(weblateClient.getConceptIdsInWeblateSet(any())).thenReturn(new HashSet<>());

		SnowstormClientFactory snowstormClientFactory = mock();
		SnowstormClient snowstormClient = mock();
		CodeSystem selectionCodeSystem = mock();
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("TEST")).thenReturn(selectionCodeSystem);
		when(selectionCodeSystem.getBranchPath()).thenReturn("MAIN");

		SnowstormClient.ConceptIdStream conceptIdStream = mock();
		when(snowstormClient.getConceptIdStream(eq("MAIN"), any())).thenReturn(conceptIdStream);
		when(conceptIdStream.get()).thenReturn(null);
		when(conceptIdStream.getTotal()).thenReturn(0);

		when(weblateAdminClient.getCreateLabel(any(), any(), any())).thenReturn(mock());

		WeblateSetCreationService service = createService(repository, snowstormClientFactory, weblateClientFactory);
		WeblateTranslationSet set = readySet();
		set.setStatus(TranslationSetStatus.QUEUED_FOR_UPGRADE);

		service.doRefreshSet(set, weblateAdminClient, snowstormClientFactory);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.READY);
		assertThat(set.getPercentageProcessed()).isEqualTo(100);
	}

	@Test
	void doRefreshSet_transitionsInitialisingToProcessingThenReady() throws ServiceExceptionWithStatusCode {
		WeblateSetRepository repository = mock();
		WeblateClientFactory weblateClientFactory = mock();
		WeblateClient weblateClient = mock();
		WeblateAdminClient weblateAdminClient = mock();
		when(weblateClientFactory.getClient()).thenReturn(weblateClient);
		when(weblateClient.getConceptIdsInWeblateSet(any())).thenReturn(new HashSet<>());

		SnowstormClientFactory snowstormClientFactory = mock();
		SnowstormClient snowstormClient = mock();
		CodeSystem selectionCodeSystem = mock();
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		when(snowstormClient.getCodeSystemOrThrow("TEST")).thenReturn(selectionCodeSystem);
		when(selectionCodeSystem.getBranchPath()).thenReturn("MAIN");

		SnowstormClient.ConceptIdStream conceptIdStream = mock();
		when(snowstormClient.getConceptIdStream(eq("MAIN"), any())).thenReturn(conceptIdStream);
		when(conceptIdStream.get()).thenReturn(null);
		when(conceptIdStream.getTotal()).thenReturn(0);

		when(weblateAdminClient.getCreateLabel(any(), any(), any())).thenReturn(mock());

		WeblateSetCreationService service = createService(repository, snowstormClientFactory, weblateClientFactory);
		WeblateTranslationSet set = readySet();
		set.setStatus(TranslationSetStatus.INITIALISING);

		service.doRefreshSet(set, weblateAdminClient, snowstormClientFactory);

		assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.READY);
	}

	private WeblateSetCreationService createService(WeblateSetRepository repository, SnowstormClientFactory snowstormClientFactory) {
		return createService(repository, snowstormClientFactory, mock(WeblateClientFactory.class));
	}

	private WeblateSetCreationService createService(WeblateSetRepository repository, SnowstormClientFactory snowstormClientFactory,
			WeblateClientFactory weblateClientFactory) {
		ProcessingContext ctx = new ProcessingContext(snowstormClientFactory, weblateClientFactory, repository,
				mock(TranslationLLMService.class), new HashMap<>(), mock(JmsTemplate.class), "test-queue", new ObjectMapper());
		return new WeblateSetCreationService(ctx, 100);
	}

	private WeblateTranslationSet readySet() {
		WeblateTranslationSet set = new WeblateTranslationSet("TEST", "123456789012345678", "Test Set", "test-set",
				"<< 138875005", TranslationSubsetType.SUB_TYPE, "TEST");
		set.setLanguageCode("fr");
		set.setStatus(TranslationSetStatus.READY);
		set.setInternationalEffectiveTime(20250101);
		return set;
	}
}
