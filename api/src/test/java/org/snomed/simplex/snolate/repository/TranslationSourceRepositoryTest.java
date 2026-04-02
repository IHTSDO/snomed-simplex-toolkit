package org.snomed.simplex.snolate.repository;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.snolate.domain.TranslationSource;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = TranslationSourceRepositoryTest.TestJpaApplication.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationSourceRepositoryTest {

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableJpaRepositories(basePackages = "org.snomed.simplex.snolate.repository")
	@EntityScan(basePackageClasses = TranslationSource.class)
	static class TestJpaApplication {
	}

	@Autowired
	private TranslationSourceRepository repository;

	@Test
	void saveFindAllByOrderAndElementCollection() {
		TranslationSource s1 = new TranslationSource("c1", "Second term", 2);
		s1.setSets(Set.of("alpha"));
		repository.save(s1);
		TranslationSource s0 = new TranslationSource("c0", "First term", 1);
		s0.setSets(Set.of("alpha", "beta"));
		repository.save(s0);

		assertThat(repository.findAllByOrderByOrderAsc())
				.extracting(TranslationSource::getCode)
				.containsExactly("c0", "c1");

		assertThat(repository.findById("c1")).isPresent().hasValueSatisfying(e -> {
			assertThat(e.getTerm()).isEqualTo("Second term");
			assertThat(e.getSets()).containsExactlyInAnyOrder("alpha");
		});
	}

	@Test
	void findAllHavingSetMembership_returnsOnlyRowsContainingSetCode() {
		TranslationSource a = new TranslationSource("a1", "A", 0);
		a.setSets(Set.of("set-one", "common"));
		TranslationSource b = new TranslationSource("b1", "B", 1);
		b.setSets(Set.of("set-two"));
		repository.save(a);
		repository.save(b);

		assertThat(repository.findAllHavingSetMembership("common"))
				.extracting(TranslationSource::getCode)
				.containsExactly("a1");
		assertThat(repository.findAllHavingSetMembership("set-two"))
				.extracting(TranslationSource::getCode)
				.containsExactly("b1");
	}

	@Test
	void findAllByCodeIn_loadsMatchingRows() {
		repository.save(new TranslationSource("x", "X", 0));
		repository.save(new TranslationSource("y", "Y", 1));

		assertThat(repository.findAllByCodeIn(List.of("y", "missing")))
				.extracting(TranslationSource::getCode)
				.containsExactly("y");
	}

	@Test
	void findAllByCodeInFetchingSets_initializesSetsForMutation() {
		TranslationSource row = new TranslationSource("m1", "M", 0);
		row.setSets(Set.of("existing"));
		repository.save(row);

		List<TranslationSource> loaded = repository.findAllByCodeInFetchingSets(List.of("m1"));
		assertThat(loaded).hasSize(1);
		loaded.get(0).getSets().add("new-set");
		repository.saveAll(loaded);

		assertThat(repository.findById("m1")).hasValueSatisfying(r ->
				assertThat(r.getSets()).containsExactlyInAnyOrder("existing", "new-set"));
	}
}
