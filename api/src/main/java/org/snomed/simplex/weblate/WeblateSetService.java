package org.snomed.simplex.weblate;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ServiceHelper;
import org.snomed.simplex.weblate.domain.WeblateTranslationSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WeblateSetService {

	private final WeblateService weblateService;
	private final WeblateSetRepository weblateSetRepository;

	public WeblateSetService(WeblateService weblateService, WeblateSetRepository weblateSetRepository) {
		this.weblateService = weblateService;
		this.weblateSetRepository = weblateSetRepository;
	}


	public List<WeblateTranslationSet> findByCodeSystemAndRefset(String codeSystem, String refsetId) {
		return weblateSetRepository.findByCodesystemAndRefset(codeSystem, refsetId);
	}

	public WeblateTranslationSet createSet(WeblateTranslationSet translationSet) throws ServiceExceptionWithStatusCode {
		ServiceHelper.requiredParameter("codesystem", translationSet.getCodesystem());
		ServiceHelper.requiredParameter("name", translationSet.getName());
		ServiceHelper.requiredParameter("refset", translationSet.getRefset());
		ServiceHelper.requiredParameter("label", translationSet.getLabel());
		Optional<WeblateTranslationSet> optional = weblateSetRepository.findByCodesystemAndLabelAndRefset(translationSet.getCodesystem(), translationSet.getLabel(), translationSet.getRefset());
		if (optional.isPresent()) {
			throw new ServiceExceptionWithStatusCode("A translation set with this label already exists.", HttpStatus.CONFLICT);
		}
		return weblateSetRepository.save(translationSet);
	}
}
