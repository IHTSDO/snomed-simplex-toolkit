package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemVersion;
import org.snomed.simplex.rest.pojos.TranslationToolUpdatePlan;
import org.snomed.simplex.service.CodeSystemService;
import org.snomed.simplex.service.ContentProcessingJobService;
import org.snomed.simplex.service.job.ChangeSummary;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.snolate.sets.TranslationUnitOrderSync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateSnomedUpgradeServiceTest {

	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private SnolateTranslationUnitStore translationUnitStore;
	@Mock
	private SnowstormClientFactory snowstormClientFactory;
	@Mock
	private CodeSystemService codeSystemService;
	@Mock
	private ContentProcessingJobService jobService;

	private SnolateSnomedUpgradeService service;

	@BeforeEach
	void setUp() {
		service = spy(new SnolateSnomedUpgradeService(translationSourceRepository, translationSearchService,
				translationUnitStore, snowstormClientFactory, codeSystemService, jobService));
	}

	@Test
	void runSnomedUpgrade_syncsTranslationUnitOrderAfterSourceRenumber() throws Exception {
		SnowstormClient snowstormClient = mock(SnowstormClient.class);
		CodeSystem codeSystem = new CodeSystem("SNOMED CT", SnowstormClient.ROOT_CODESYSTEM, "MAIN");
		CodeSystemVersion version = new CodeSystemVersion(20250731, "20250731", null, null, null);
		TranslationToolUpdatePlan updatePlan = new TranslationToolUpdatePlan(20250101, 20250731, version, codeSystem);
		ContentJob contentJob = new ContentJob(codeSystem, "upgrade", null);

		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		doReturn(2).when(service).insertNewConceptStubsIntoSnolate(updatePlan);
		when(translationSourceRepository.count()).thenReturn(100L);

		try (MockedStatic<TranslationUnitOrderSync> orderSync = mockStatic(TranslationUnitOrderSync.class)) {
			orderSync.when(() -> TranslationUnitOrderSync.syncAllUnits(
					same(translationSearchService), same(translationSourceRepository), same(translationUnitStore)))
					.thenReturn(4);

			ChangeSummary summary = service.runSnomedUpgrade(updatePlan, contentJob);

			orderSync.verify(() -> TranslationUnitOrderSync.syncAllUnits(
					same(translationSearchService), same(translationSourceRepository), same(translationUnitStore)));
			verify(snowstormClient).upsertBranchMetadata(eq(SnolateSnomedUpgradeService.MAIN_BRANCH), any());
			assertThat(summary.getAdded()).isEqualTo(2);
		}
	}
}
