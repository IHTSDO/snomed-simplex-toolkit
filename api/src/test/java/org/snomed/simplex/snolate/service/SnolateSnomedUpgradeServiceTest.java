package org.snomed.simplex.snolate.service;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.sets.SnolateTranslationSearchService;
import org.snomed.simplex.snolate.sets.SnolateTranslationSourceRepository;
import org.snomed.simplex.snolate.sets.SnolateTranslationUnitStore;
import org.snomed.simplex.snolate.sets.TranslationUnitOrderSync;

import java.util.List;
import java.util.Map;

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
		doReturn(new SnolateSnomedUpgradeService.SourcePersistResult(2, 3)).when(service).insertNewConceptStubsIntoSnolate(updatePlan);
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
			assertThat(summary.getUpdated()).isEqualTo(3);
		}
	}

	@Test
	void applyReleaseTerm_updatesWhenDifferent() {
		TranslationSource row = new TranslationSource("100", "Old term", 0);

		assertThat(SnolateSnomedUpgradeService.applyReleaseTerm(row, "New term")).isTrue();
		assertThat(row.getTerm()).isEqualTo("New term");
	}

	@Test
	void applyReleaseTerm_noOpWhenUnchangedOrMissingReleasePt() {
		TranslationSource row = new TranslationSource("100", "Same term", 0);

		assertThat(SnolateSnomedUpgradeService.applyReleaseTerm(row, "Same term")).isFalse();
		assertThat(SnolateSnomedUpgradeService.applyReleaseTerm(row, null)).isFalse();
		assertThat(row.getTerm()).isEqualTo("Same term");
	}

	@Test
	void persistTranslationSources_updatesExistingEnglishTermFromRelease() throws Exception {
		TranslationSource existing = new TranslationSource("100", "Old PT", 5);
		when(translationSearchService.mapAllTranslationSourcesByCode()).thenReturn(Map.of("100", existing));

		Map<Long, String> newRows = new Long2ObjectOpenHashMap<>();
		Map<Long, String> releasePt = Map.of(100L, "New PT");

		int termsUpdated = service.persistTranslationSources(List.of(100L), newRows, releasePt);

		assertThat(termsUpdated).isOne();
		assertThat(existing.getTerm()).isEqualTo("New PT");
		assertThat(existing.getOrder()).isZero();

		ArgumentCaptor<List<TranslationSource>> captor = ArgumentCaptor.forClass(List.class);
		verify(translationSourceRepository).saveAll(captor.capture());
		assertThat(captor.getValue()).containsExactly(existing);
	}

	@Test
	void persistTranslationSources_newConceptUsesNewRowsNotReleaseLookup() throws Exception {
		when(translationSearchService.mapAllTranslationSourcesByCode()).thenReturn(Map.of());

		Map<Long, String> newRows = Map.of(200L, "Brand new PT");
		Map<Long, String> releasePt = Map.of(200L, "Other PT in map");

		int termsUpdated = service.persistTranslationSources(List.of(200L), newRows, releasePt);

		assertThat(termsUpdated).isZero();

		ArgumentCaptor<List<TranslationSource>> captor = ArgumentCaptor.forClass(List.class);
		verify(translationSourceRepository).saveAll(captor.capture());
		TranslationSource saved = captor.getValue().get(0);
		assertThat(saved.getCode()).isEqualTo("200");
		assertThat(saved.getTerm()).isEqualTo("Brand new PT");
	}
}
