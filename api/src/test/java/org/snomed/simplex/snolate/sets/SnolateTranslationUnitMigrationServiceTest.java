package org.snomed.simplex.snolate.sets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.snomed.simplex.rest.pojos.RepairTranslationUnitIdsResponse;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.translation.tool.TranslationSubsetType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnolateTranslationUnitMigrationServiceTest {

	private static final String COMPOSITE = "nl-31000172101";

	@Mock
	private SnolateSetRepository snolateSetRepository;
	@Mock
	private SnolateTranslationSearchService translationSearchService;
	@Mock
	private SnolateTranslationUnitRepository translationUnitRepository;

	private SnolateTranslationUnitMigrationService service;

	@BeforeEach
	void setUp() {
		service = new SnolateTranslationUnitMigrationService(snolateSetRepository, translationSearchService,
				translationUnitRepository);
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

		RepairTranslationUnitIdsResponse response = service.repairTranslationUnitIds("SNOMEDCT-BE");

		assertThat(response.compositeLanguageBucketsProcessed()).isEqualTo(1);
		assertThat(response.mergedGroups()).isEqualTo(1);
		assertThat(response.orphansDeleted()).isEqualTo(2);

		ArgumentCaptor<Iterable<TranslationUnit>> saveCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).saveAll(saveCaptor.capture());
		TranslationUnit saved = saveCaptor.getValue().iterator().next();
		assertThat(saved.getId()).isEqualTo("nl-31000172101_63161005");
		assertThat(saved.getMemberOf()).contains(set.getCompositeSetCode());

		ArgumentCaptor<Iterable<String>> deleteCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(translationUnitRepository).deleteAllById(deleteCaptor.capture());
		assertThat(deleteCaptor.getValue()).containsExactlyInAnyOrder("orphan-a", "orphan-b");
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
