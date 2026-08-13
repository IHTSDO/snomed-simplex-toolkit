package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.SnowstormClientFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.rest.pojos.DeleteEmptyShellUnitsResponse;
import org.snomed.simplex.rest.pojos.RepairTranslationSetSizesResponse;
import org.snomed.simplex.rest.pojos.RepairTranslationUnitIdsResponse;
import org.snomed.simplex.snolate.domain.TranslationSource;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationUnitMigrationServiceTest {

	private static final String COMPOSITE = "nl-31000172101";
	private static final String OTHER_COMPOSITE = "fr-31000172102";
	private static final String CODE_SYSTEM = "SNOMEDCT-BE";

	@Mock
	private SnolateSetRepository snolateSetRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;
	@Mock
	private SnolateTranslationSourceRepository translationSourceRepository;
	@Mock
	private SnowstormClientFactory snowstormClientFactory;
	@Mock
	private SnowstormClient snowstormClient;
	@Mock
	private SnolateSetService snolateSetService;

	private SnolateTranslationUnitMigrationService service;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationUnitMigrationService(snolateSetRepository, translationSearchService,
				translationUnitRepository, translationSourceRepository, snowstormClientFactory, snolateSetService);
	}

	@Test
	void repairTranslationUnitIds_allCodeSystems_handlesPageFromFindAll() {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-BE", "31000172101", "Set", "label", "ecl",
				TranslationSubsetType.REFSET, "SNOMEDCT-BE");
		set.setLanguageCode("nl");
		when(snolateSetRepository.findAll()).thenReturn(new PageImpl<>(List.of(set), PageRequest.of(0, 100), 1));

		doAnswer(invocation -> null)
				.when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		RepairTranslationUnitIdsResponse response = service.repairTranslationUnitIds(null);

		assertThat(response.compositeLanguageBucketsProcessed()).isEqualTo(1);
		assertThat(response.mergedGroups()).isZero();
	}

	@Test
	void repairTranslationUnitIds_mergesDuplicatesInBucket() {
		SnolateTranslationSet set = new SnolateTranslationSet("SNOMEDCT-BE", "31000172101", "Set", "label", "ecl",
				org.snomed.simplex.translation.tool.TranslationSubsetType.REFSET, "SNOMEDCT-BE");
		set.setLanguageCode("nl");
		when(snolateSetRepository.findByCodesystemOrderByName("SNOMEDCT-BE")).thenReturn(List.of(set));

		TranslationUnit withoutMember = unit("63161005", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "orphan-a");
		TranslationUnit withMember = unit("63161005", List.of(), TranslationStatus.NOT_STARTED,
				Set.of(set.getCompositeSetCode()), "orphan-b");

		doAnswer(invocation -> {
			var consumer = invocation.getArgument(1, java.util.function.Consumer.class);
			consumer.accept(withoutMember);
			consumer.accept(withMember);
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(eq(COMPOSITE), any());

		when(translationSourceRepository.findAllById(List.of("63161005")))
				.thenReturn(List.of(new TranslationSource("63161005", "Principal", 55)));

		RepairTranslationUnitIdsResponse response = service.repairTranslationUnitIds("SNOMEDCT-BE");

		assertThat(response.compositeLanguageBucketsProcessed()).isEqualTo(1);
		assertThat(response.mergedGroups()).isEqualTo(1);
		assertThat(response.orphansDeleted()).isEqualTo(2);

		ArgumentCaptor<Iterable<TranslationUnit>> saveCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).saveAll(saveCaptor.capture());
		TranslationUnit saved = saveCaptor.getValue().iterator().next();
		assertThat(saved.getId()).isEqualTo("nl-31000172101_63161005");
		assertThat(saved.getMemberOf()).contains(set.getCompositeSetCode());
		assertThat(saved.getOrder()).isEqualTo(55);

		ArgumentCaptor<Iterable<String>> deleteCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).deleteAllById(deleteCaptor.capture());
		assertThat(deleteCaptor.getValue()).containsExactlyInAnyOrder("orphan-a", "orphan-b");
	}

	@Test
	void isEmptyShell_trueWhenNoTermsAndNotStarted() {
		TranslationUnit shell = unit("63161005", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "shell-id");
		assertThat(SnolateTranslationUnitMigrationService.isEmptyShell(shell)).isTrue();
	}

	@Test
	void isEmptyShell_falseWhenHasTerms() {
		TranslationUnit withTerms = unit("63161005", List.of("term"), TranslationStatus.NOT_STARTED, Set.of(), "with-terms");
		assertThat(SnolateTranslationUnitMigrationService.isEmptyShell(withTerms)).isFalse();
	}

	@Test
	void isEmptyShell_falseWhenCompleteWithoutTerms() {
		TranslationUnit complete = unit("63161005", List.of(), TranslationStatus.COMPLETE, Set.of(), "complete");
		assertThat(SnolateTranslationUnitMigrationService.isEmptyShell(complete)).isFalse();
	}

	@Test
	void deleteEmptyShellUnits_deletesOnlyEmptyNotStartedShellsAcrossRefsets() throws Exception {
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		CodeSystem edition = new CodeSystem("BE", CODE_SYSTEM, "MAIN/" + CODE_SYSTEM);
		Map<String, String> translationLanguages = new LinkedHashMap<>();
		translationLanguages.put("31000172101", "nl");
		translationLanguages.put("31000172102", "fr");
		edition.setTranslationLanguages(translationLanguages);
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM)).thenReturn(edition);

		TranslationUnit emptyShell = unit("111", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "nl-31000172101_111");
		TranslationUnit withTerms = unit("222", List.of("term"), TranslationStatus.NOT_STARTED, Set.of(), "nl-31000172101_222");
		TranslationUnit completeEmpty = unit("333", List.of(), TranslationStatus.COMPLETE, Set.of(), "nl-31000172101_333");
		TranslationUnit otherRefsetShell = unit("444", List.of(), TranslationStatus.NOT_STARTED, Set.of(), "fr-31000172102_444");

		doAnswer(invocation -> {
			var consumer = invocation.getArgument(1, java.util.function.Consumer.class);
			if (COMPOSITE.equals(invocation.getArgument(0))) {
				consumer.accept(emptyShell);
				consumer.accept(withTerms);
				consumer.accept(completeEmpty);
			} else if (OTHER_COMPOSITE.equals(invocation.getArgument(0))) {
				consumer.accept(otherRefsetShell);
			}
			return null;
		}).when(translationSearchService).forEachUnitByCompositeLanguageCode(any(), any());

		RepairTranslationSetSizesResponse repairResponse = new RepairTranslationSetSizesResponse(1, 0, 0, List.of());
		when(snolateSetService.repairSetSizes(CODE_SYSTEM)).thenReturn(repairResponse);

		DeleteEmptyShellUnitsResponse response = service.deleteEmptyShellUnits(CODE_SYSTEM);

		assertThat(response.languageRefsetsProcessed()).isEqualTo(2);
		assertThat(response.deleted()).isEqualTo(2);
		assertThat(response.byRefset()).extracting("refsetId", "languageCode", "deleted")
				.containsExactly(
						tuple("31000172101", "nl", 1),
						tuple("31000172102", "fr", 1));
		assertThat(response.setSizeRepair()).isSameAs(repairResponse);

		ArgumentCaptor<Iterable<String>> deleteCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository, times(2)).deleteAllById(deleteCaptor.capture());
		assertThat(deleteCaptor.getAllValues())
				.flatExtracting(iterable -> {
					List<String> ids = new ArrayList<>();
					iterable.forEach(ids::add);
					return ids;
				})
				.containsExactlyInAnyOrder("nl-31000172101_111", "fr-31000172102_444");
		verify(snolateSetService).repairSetSizes(CODE_SYSTEM);
	}

	@Test
	void deleteEmptyShellUnits_noTranslationLanguagesStillRepairsSetSizes() throws Exception {
		when(snowstormClientFactory.getClient()).thenReturn(snowstormClient);
		CodeSystem edition = new CodeSystem("BE", CODE_SYSTEM, "MAIN/" + CODE_SYSTEM);
		edition.setTranslationLanguages(Map.of());
		when(snowstormClient.getCodeSystemOrThrow(CODE_SYSTEM)).thenReturn(edition);

		RepairTranslationSetSizesResponse repairResponse = new RepairTranslationSetSizesResponse(0, 0, 0, List.of());
		when(snolateSetService.repairSetSizes(CODE_SYSTEM)).thenReturn(repairResponse);

		DeleteEmptyShellUnitsResponse response = service.deleteEmptyShellUnits(CODE_SYSTEM);

		assertThat(response.languageRefsetsProcessed()).isZero();
		assertThat(response.deleted()).isZero();
		assertThat(response.byRefset()).isEmpty();
		assertThat(response.setSizeRepair()).isSameAs(repairResponse);
		verify(snolateSetService).repairSetSizes(CODE_SYSTEM);
	}

	private static TranslationUnit unit(String code, List<String> terms, TranslationStatus status, Set<String> memberOf,
			String id) {
		TranslationUnit u = new TranslationUnit(
				new TranslationUnit.MembershipKey(code, "31000172101", "nl", COMPOSITE, 1),
				terms, status, new LinkedHashSet<>(memberOf));
		u.setId(id);
		return u;
	}
}
