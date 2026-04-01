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
}
