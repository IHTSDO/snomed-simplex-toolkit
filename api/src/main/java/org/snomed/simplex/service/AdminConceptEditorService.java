package org.snomed.simplex.service;

import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.Concept;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Description;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.rest.pojos.AdminConceptDescriptionDto;
import org.snomed.simplex.rest.pojos.AdminConceptEditorDetail;
import org.snomed.simplex.rest.pojos.AdminConceptLangRefsetDto;
import org.snomed.simplex.rest.pojos.AdminConceptUpdateRequest;
import org.snomed.simplex.translation.service.TranslationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminConceptEditorService {

	private static final Comparator<Description> DESCRIPTION_DISPLAY_ORDER = Comparator
			.comparing(Description::isActive).reversed()
			.thenComparing(d -> typeSortKey(d.getType()))
			.thenComparing(Description::getTerm, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

	private final TranslationService translationService;

	public AdminConceptEditorService(TranslationService translationService) {
		this.translationService = translationService;
	}

	public AdminConceptEditorDetail loadForEditor(CodeSystem codeSystem, SnowstormClient snowstormClient, String conceptId)
			throws ServiceException {
		Concept concept = loadConceptOrThrow(codeSystem, snowstormClient, conceptId);
		List<AdminConceptLangRefsetDto> langRefsets = buildLangRefsetMetadata(codeSystem, snowstormClient, concept);
		return toEditorDetail(codeSystem, concept, langRefsets);
	}

	public AdminConceptEditorDetail applyDescriptionEdits(CodeSystem codeSystem, SnowstormClient snowstormClient,
			String conceptId, AdminConceptUpdateRequest request) throws ServiceException {
		if (request == null || request.descriptions() == null) {
			throw new ServiceExceptionWithStatusCode("Request body with descriptions is required.", HttpStatus.BAD_REQUEST);
		}
		Concept concept = loadConceptOrThrow(codeSystem, snowstormClient, conceptId);
		Map<String, Description> existingById = concept.getDescriptions().stream()
				.filter(d -> d.getDescriptionId() != null)
				.collect(Collectors.toMap(Description::getDescriptionId, Function.identity(), (a, b) -> a));

		String defaultModule = codeSystem.getDefaultModuleOrThrow();
		Map<String, String> translationLanguages = codeSystem.getTranslationLanguages() != null
				? codeSystem.getTranslationLanguages()
				: Map.of();

		List<Description> updatedDescriptions = new ArrayList<>();
		Set<String> processedIds = new HashSet<>();

		for (AdminConceptDescriptionDto dto : request.descriptions()) {
			if (dto.descriptionId() == null || dto.descriptionId().isBlank()) {
				Description created = createNewDescription(concept, dto, defaultModule, translationLanguages);
				updatedDescriptions.add(created);
				continue;
			}
			Description existing = existingById.get(dto.descriptionId());
			if (existing == null) {
				throw new ServiceExceptionWithStatusCode(
						"Description %s not found on concept %s.".formatted(dto.descriptionId(), conceptId),
						HttpStatus.BAD_REQUEST);
			}
			processedIds.add(dto.descriptionId());
			applyDescriptionEdits(existing, dto, concept.getDescriptions());
			if (!dto.active()) {
				if (existing.isReleased()) {
					existing.setActive(false);
					existing.setRemove(false);
				} else {
					existing.setRemove(true);
					existing.setActive(false);
				}
			} else {
				existing.setActive(true);
				existing.setRemove(false);
			}
			updatedDescriptions.add(existing);
		}

		for (Description existing : concept.getDescriptions()) {
			if (existing.getDescriptionId() != null && !processedIds.contains(existing.getDescriptionId())) {
				updatedDescriptions.add(existing);
			}
		}

		concept.setDescriptions(updatedDescriptions);
		snowstormClient.updateConcept(concept, codeSystem);

		Concept reloaded = loadConceptOrThrow(codeSystem, snowstormClient, conceptId);
		List<AdminConceptLangRefsetDto> langRefsets = buildLangRefsetMetadata(codeSystem, snowstormClient, reloaded);
		return toEditorDetail(codeSystem, reloaded, langRefsets);
	}

	private void applyDescriptionEdits(Description existing, AdminConceptDescriptionDto dto, List<Description> allDescriptions)
			throws ServiceExceptionWithStatusCode {
		String newTerm = dto.term() != null ? dto.term().trim() : "";
		if (existing.isReleased()) {
			if (!Objects.equals(normalizeTerm(existing.getTerm()), normalizeTerm(newTerm))) {
				throw new ServiceExceptionWithStatusCode(
						"Released description %s term cannot be changed.".formatted(existing.getDescriptionId()),
						HttpStatus.BAD_REQUEST);
			}
		} else {
			if (!Objects.equals(normalizeTerm(existing.getTerm()), normalizeTerm(newTerm))) {
				existing.setTerm(newTerm);
				existing.setCaseSignificance(translationService.guessCaseSignificance(newTerm, allDescriptions));
			}
		}

		if (dto.type() != null && !dto.type().isBlank()) {
			Description.Type type = Description.Type.valueOf(dto.type());
			existing.setType(type);
		}

		applyAcceptabilityMap(existing, dto.acceptabilityMap());
	}

	private Description createNewDescription(Concept concept, AdminConceptDescriptionDto dto, String defaultModule,
			Map<String, String> translationLanguages) throws ServiceExceptionWithStatusCode {
		String term = dto.term() != null ? dto.term().trim() : "";
		if (term.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("New description term is required.", HttpStatus.BAD_REQUEST);
		}
		Description.Type type = Description.Type.SYNONYM;
		if (dto.type() != null && !dto.type().isBlank()) {
			type = Description.Type.valueOf(dto.type());
		}
		String lang = resolveLanguageForNewDescription(dto, translationLanguages);
		Description description = new Description(type, lang, term, translationService.guessCaseSignificance(term, concept.getDescriptions()));
		description.setModuleId(defaultModule);
		description.setActive(true);
		applyAcceptabilityMap(description, dto.acceptabilityMap());
		return description;
	}

	private String resolveLanguageForNewDescription(AdminConceptDescriptionDto dto, Map<String, String> translationLanguages)
			throws ServiceExceptionWithStatusCode {
		if (dto.lang() != null && !dto.lang().isBlank()) {
			return dto.lang().trim();
		}
		if (dto.acceptabilityMap() != null && !dto.acceptabilityMap().isEmpty()) {
			String refsetId = dto.acceptabilityMap().keySet().iterator().next();
			String language = translationLanguages.get(refsetId);
			if (language != null) {
				return language;
			}
		}
		if (dto.acceptabilityMap() != null && dto.acceptabilityMap().containsKey(Concepts.US_LANG_REFSET)) {
			return "en";
		}
		throw new ServiceExceptionWithStatusCode(
				"Language or language refset is required for new descriptions.", HttpStatus.BAD_REQUEST);
	}

	private void applyAcceptabilityMap(Description description, Map<String, String> acceptabilityMap) {
		if (acceptabilityMap == null) {
			return;
		}
		Map<String, Description.Acceptability> mapped = new HashMap<>();
		for (Map.Entry<String, String> entry : acceptabilityMap.entrySet()) {
			if (entry.getValue() == null || entry.getValue().isBlank()) {
				continue;
			}
			mapped.put(entry.getKey(), Description.Acceptability.valueOf(entry.getValue()));
		}
		if (description.getAcceptabilityMap() == null) {
			description.setAcceptabilityMap(mapped);
		} else {
			description.getAcceptabilityMap().clear();
			description.getAcceptabilityMap().putAll(mapped);
		}
	}

	private Concept loadConceptOrThrow(CodeSystem codeSystem, SnowstormClient snowstormClient, String conceptId)
			throws ServiceException {
		List<Concept> concepts = snowstormClient.loadBrowserFormatConcepts(List.of(Long.parseLong(conceptId.trim())), codeSystem);
		if (concepts == null || concepts.isEmpty()) {
			throw new ServiceExceptionWithStatusCode("Concept not found.", HttpStatus.NOT_FOUND);
		}
		return concepts.get(0);
	}

	private AdminConceptEditorDetail toEditorDetail(CodeSystem codeSystem, Concept concept,
			List<AdminConceptLangRefsetDto> langRefsets) {
		List<Description> sorted = new ArrayList<>(concept.getDescriptions() != null ? concept.getDescriptions() : List.of());
		sorted.sort(DESCRIPTION_DISPLAY_ORDER);

		List<AdminConceptDescriptionDto> descriptionDtos = sorted.stream().map(this::toDescriptionDto).toList();
		String fsnTerm = concept.getFsn() != null ? concept.getFsn().getTerm() : null;
		String ptTerm = concept.getPt() != null ? concept.getPt().getTerm() : null;

		return new AdminConceptEditorDetail(
				codeSystem.getShortName(),
				concept.getConceptId(),
				concept.isActive(),
				concept.getModuleId(),
				codeSystem.getDefaultModule(),
				fsnTerm,
				ptTerm,
				descriptionDtos,
				langRefsets,
				Concepts.internationalModuleIds());
	}

	private AdminConceptDescriptionDto toDescriptionDto(Description description) {
		Map<String, String> acceptability = new LinkedHashMap<>();
		if (description.getAcceptabilityMap() != null) {
			for (Map.Entry<String, Description.Acceptability> entry : description.getAcceptabilityMap().entrySet()) {
				if (entry.getValue() != null) {
					acceptability.put(entry.getKey(), entry.getValue().name());
				}
			}
		}
		String type = description.getType() != null ? description.getType().name() : null;
		String caseSignificance = description.getCaseSignificance() != null ? description.getCaseSignificance().name() : null;
		return new AdminConceptDescriptionDto(
				description.getDescriptionId(),
				description.getTerm(),
				type,
				description.getLang(),
				description.isActive(),
				description.isReleased(),
				description.getModuleId(),
				caseSignificance,
				acceptability);
	}

	private List<AdminConceptLangRefsetDto> buildLangRefsetMetadata(CodeSystem codeSystem, SnowstormClient snowstormClient,
			Concept concept) throws ServiceException {
		Map<String, AdminConceptLangRefsetDto> byId = new LinkedHashMap<>();
		byId.put(Concepts.US_LANG_REFSET, new AdminConceptLangRefsetDto(
				Concepts.US_LANG_REFSET, shortenLangRefsetLabel("US English"), "en"));

		List<ConceptMini> translations = translationService.listTranslations(codeSystem, snowstormClient);
		for (ConceptMini translation : translations) {
			String refsetId = translation.getConceptId();
			String label = translation.getPt() != null ? translation.getPt().getTerm() : refsetId;
			String languageCode = codeSystem.getTranslationLanguages() != null
					? codeSystem.getTranslationLanguages().getOrDefault(refsetId, "en")
					: "en";
			byId.put(refsetId, new AdminConceptLangRefsetDto(refsetId, shortenLangRefsetLabel(label), languageCode));
		}

		if (concept.getDescriptions() != null) {
			for (Description description : concept.getDescriptions()) {
				if (description.getAcceptabilityMap() == null) {
					continue;
				}
				for (String refsetId : description.getAcceptabilityMap().keySet()) {
					byId.computeIfAbsent(refsetId, id -> new AdminConceptLangRefsetDto(
							id,
							shortenLangRefsetLabel(id),
							codeSystem.getTranslationLanguages() != null
									? codeSystem.getTranslationLanguages().getOrDefault(id, description.getLang())
									: description.getLang()));
				}
			}
		}

		return new ArrayList<>(byId.values());
	}

	static String shortenLangRefsetLabel(String label) {
		if (label == null || label.isBlank()) {
			return label;
		}
		String shortened = label;
		for (String phrase : List.of("language reference set", "language refset", "reference set", "synonyms", "refset", "language")) {
			shortened = shortened.replaceAll("(?i)\\s*" + Pattern.quote(phrase) + "\\s*", " ");
		}
		shortened = shortened.replaceAll("\\s+", " ").trim();
		return shortened.replaceAll(",\\s*$", "").trim();
	}

	private static int typeSortKey(Description.Type type) {
		if (type == null) {
			return 99;
		}
		return switch (type) {
			case FSN -> 0;
			case SYNONYM -> 1;
			case TEXT_DEFINITION -> 2;
		};
	}

	private static String normalizeTerm(String term) {
		return term == null ? "" : term.trim();
	}

}
