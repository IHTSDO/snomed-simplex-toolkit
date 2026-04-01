package org.snomed.simplex.snolate.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.snolate.domain.TranslationStatus;
import org.snomed.simplex.snolate.domain.TranslationUnit;
import org.snomed.simplex.snolate.domain.TranslationUnitId;
import org.snomed.simplex.snolate.repository.TranslationUnitRepository;
import org.snomed.simplex.translation.domain.TranslationState;
import org.snomed.simplex.translation.service.TranslationSourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ContextConfiguration(classes = SnolateTranslationSourceTest.TestJpaApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SnolateTranslationSourceTest {

	private static final String LANG = "en";
	private static final String REFSET = "1000123";
	private static final String COMPOSITE = LANG + "-" + REFSET;

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableJpaRepositories(basePackages = "org.snomed.simplex.snolate.repository")
	@EntityScan(basePackages = "org.snomed.simplex.snolate.domain")
	static class TestJpaApplication {
	}

	@Autowired
	private TranslationUnitRepository translationUnitRepository;

	@Test
	void readTranslation_mapsPersistedUnits() throws Exception {
		translationUnitRepository.save(new TranslationUnit("100", COMPOSITE, List.of("alpha", "beta"), TranslationStatus.FOR_REVIEW));
		SnolateTranslationSource source = new SnolateTranslationSource(translationUnitRepository, LANG, REFSET);

		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsEntry(100L, List.of("alpha", "beta"));
		assertThat(source.getType()).isEqualTo(TranslationSourceType.SNOLATE);
	}

	@Test
	void readTranslation_rejectsNonNumericCode() {
		translationUnitRepository.save(new TranslationUnit("x", COMPOSITE, List.of("t"), TranslationStatus.APPROVED));
		SnolateTranslationSource source = new SnolateTranslationSource(translationUnitRepository, LANG, REFSET);

		assertThatThrownBy(source::readTranslation)
				.hasMessageContaining("non-numeric code");
	}

	@Test
	void writeTranslation_createsAndMergesAdditions() throws Exception {
		translationUnitRepository.save(new TranslationUnit("200", COMPOSITE, List.of("existing"), TranslationStatus.NEEDS_EDIT));
		SnolateTranslationSource source = new SnolateTranslationSource(translationUnitRepository, LANG, REFSET);

		TranslationState additions = new TranslationState();
		additions.getConceptTerms().put(200L, List.of("extra"));
		additions.getConceptTerms().put(201L, List.of("only"));
		source.writeTranslation(additions);

		assertThat(translationUnitRepository.findById(new TranslationUnitId("200", COMPOSITE))).isPresent().hasValueSatisfying(u -> {
			assertThat(u.getTerms()).containsExactly("existing", "extra");
			assertThat(u.getStatus()).isEqualTo(TranslationStatus.NEEDS_EDIT);
		});
		assertThat(translationUnitRepository.findById(new TranslationUnitId("201", COMPOSITE))).isPresent().hasValueSatisfying(u -> {
			assertThat(u.getTerms()).containsExactly("only");
			assertThat(u.getStatus()).isEqualTo(TranslationStatus.APPROVED);
		});
	}

	@Test
	void readTranslation_ignoresOtherLanguageBuckets() throws Exception {
		translationUnitRepository.save(new TranslationUnit("100", COMPOSITE, List.of("en-term"), TranslationStatus.APPROVED));
		translationUnitRepository.save(new TranslationUnit("100", "fr-" + REFSET, List.of("fr-term"), TranslationStatus.APPROVED));

		SnolateTranslationSource source = new SnolateTranslationSource(translationUnitRepository, LANG, REFSET);
		TranslationState state = source.readTranslation();

		assertThat(state.getConceptTerms()).containsOnlyKeys(100L);
		assertThat(state.getConceptTerms().get(100L)).containsExactly("en-term");
	}

	@Test
	void mergeAdditions_prependsWhenUnitWasEmpty() {
		assertThat(SnolateTranslationSource.mergeAdditions(List.of(), List.of("b", "a")))
				.containsExactly("b", "a");
	}
}
