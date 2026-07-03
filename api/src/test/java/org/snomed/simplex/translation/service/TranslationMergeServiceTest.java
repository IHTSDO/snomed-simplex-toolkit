package org.snomed.simplex.translation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.translation.domain.Intent;
import org.snomed.simplex.translation.domain.TermIntent;
import org.snomed.simplex.translation.domain.TranslationIntent;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.repository.TranslationStateRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationMergeServiceTest {

	@Mock
	private TranslationStateRepository stateRepository;

	@Mock
	private TranslationSource source;

	@Mock
	private TranslationSource target;

	private TranslationMergeService mergeService;

	@BeforeEach
	void setUp() {
		mergeService = new TranslationMergeService(stateRepository);
	}

	@Test
	void buildSnowstormUploadState_synonymAdded_includesFullMergedTerms() {
		TranslationIntent sourceIntent = new TranslationIntent();
		sourceIntent.getTermIntents().put(100L, List.of(
				new TermIntent("preferred", Intent.NONE),
				new TermIntent("synonym", Intent.ADD)));

		TranslationState mergedTargetState = new TranslationState();
		mergedTargetState.getConceptTerms().put(100L, List.of("preferred", "synonym"));

		TranslationState uploadState = mergeService.buildSnowstormUploadState(sourceIntent, mergedTargetState);

		assertThat(uploadState.getConceptTerms()).containsEntry(100L, List.of("preferred", "synonym"));
	}

	@Test
	void buildSnowstormUploadState_termReplaced_includesNewTermOnly() {
		TranslationIntent sourceIntent = new TranslationIntent();
		sourceIntent.getTermIntents().put(100L, List.of(
				new TermIntent("new term", Intent.ADD),
				new TermIntent("old term", Intent.REMOVE)));

		TranslationState mergedTargetState = new TranslationState();
		mergedTargetState.getConceptTerms().put(100L, List.of("new term"));

		TranslationState uploadState = mergeService.buildSnowstormUploadState(sourceIntent, mergedTargetState);

		assertThat(uploadState.getConceptTerms()).containsEntry(100L, List.of("new term"));
	}

	@Test
	void buildSnowstormUploadState_noChanges_emptyUploadState() {
		TranslationIntent sourceIntent = new TranslationIntent();
		sourceIntent.getTermIntents().put(100L, List.of(new TermIntent("preferred", Intent.NONE)));

		TranslationState mergedTargetState = new TranslationState();
		mergedTargetState.getConceptTerms().put(100L, List.of("preferred"));

		TranslationState uploadState = mergeService.buildSnowstormUploadState(sourceIntent, mergedTargetState);

		assertThat(uploadState.getConceptTerms()).isEmpty();
	}

	@Test
	void applyMerge_returnsMergeResultWithSourceState() throws Exception {
		TranslationState sourceState = new TranslationState();
		sourceState.getConceptTerms().put(100L, List.of("term"));

		TranslationState targetState = new TranslationState();
		targetState.getConceptTerms().put(100L, List.of("existing"));

		when(source.getType()).thenReturn(TranslationSourceType.TERMINOLOGY_SERVER);
		when(target.getType()).thenReturn(TranslationSourceType.SNOLATE);
		when(source.readTranslation()).thenReturn(sourceState);
		when(target.readTranslation()).thenReturn(targetState);
		when(stateRepository.loadStateOrBlank("1000123", TranslationSourceType.TERMINOLOGY_SERVER)).thenReturn(new TranslationState());

		TranslationMergeService.MergeResult result = mergeService.applyMerge(source, target, "en", "1000123");

		assertThat(result.sourceState()).isSameAs(sourceState);
		verify(source, times(1)).readTranslation();
	}

	@Test
	void computeMerge_noChanges_hasChangesFalse() throws Exception {
		TranslationState sourceState = new TranslationState();
		sourceState.getConceptTerms().put(100L, List.of("term"));

		TranslationState targetState = new TranslationState();
		targetState.getConceptTerms().put(100L, List.of("existing"));

		when(source.getType()).thenReturn(TranslationSourceType.SNOLATE_SUBSET);
		when(target.getType()).thenReturn(TranslationSourceType.TERMINOLOGY_SERVER);
		when(source.readTranslation()).thenReturn(sourceState);
		when(target.readTranslation()).thenReturn(targetState);
		when(stateRepository.loadStateOrBlank("1000123", TranslationSourceType.SNOLATE_SUBSET)).thenReturn(sourceState);

		TranslationMergeService.MergeResult result = mergeService.computeMerge(source, target, "en", "1000123");

		assertThat(result.hasChanges()).isFalse();
		assertThat(result.snowstormUploadState().getConceptTerms()).isEmpty();
	}

	@Test
	void inferIntentAndApplyIntent_synonymAdded_producesFullUploadState() {
		TranslationState previousSource = new TranslationState();
		previousSource.getConceptTerms().put(100L, List.of("preferred"));

		TranslationState newSource = new TranslationState();
		newSource.getConceptTerms().put(100L, List.of("preferred", "synonym"));

		TranslationState targetState = new TranslationState();
		targetState.getConceptTerms().put(100L, List.of("preferred"));

		TranslationIntent intent = mergeService.inferIntent(previousSource, newSource);
		mergeService.applyIntent(targetState, intent);

		TranslationState uploadState = mergeService.buildSnowstormUploadState(intent, targetState);

		assertThat(uploadState.getConceptTerms()).containsEntry(100L, List.of("preferred", "synonym"));
	}

}
