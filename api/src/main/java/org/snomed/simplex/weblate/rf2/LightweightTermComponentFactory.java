package org.snomed.simplex.weblate.rf2;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.simplex.client.domain.Concepts;
import org.snomed.simplex.weblate.domain.Term;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Long.parseLong;

public class LightweightTermComponentFactory extends ImpotentComponentFactory {

	private final Map<Long, Map<Long, Term>> conceptTerms;
	private final Map<Long, Term> descriptionMap;
	private final String languageFilter;
	private final Long languageRefsetId;
	private static final Term DUMMY_TERM = new Term("");

	public LightweightTermComponentFactory(String languageFilter, Long languageRefsetId) {
		conceptTerms = new Long2ObjectOpenHashMap<>();
		descriptionMap = new Long2ObjectOpenHashMap<>();
		this.languageFilter = languageFilter;
		this.languageRefsetId = languageRefsetId;
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId,
			String term, String caseSignificanceId) {

		if ("1".equals(active) && typeId.equals(Concepts.SYNONYM) && (languageFilter == null || languageFilter.equals(languageCode))) {
			long descriptionId = parseLong(id);
			Term value = new Term(term);
			conceptTerms.computeIfAbsent(parseLong(conceptId), i -> new HashMap<>()).put(descriptionId, value);
			descriptionMap.put(descriptionId, value);
		}
	}

	@Override
	public void newReferenceSetMemberState(String filename, String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId,
			String referencedComponentId, String... otherValues) {

		if ("1".equals(active) && languageRefsetId.equals(parseLong(refsetId))) {
			descriptionMap.getOrDefault(parseLong(referencedComponentId), DUMMY_TERM).addAcceptability(languageRefsetId, parseLong(otherValues[0]));
		}
	}

	public Map<Long, Map<Long, Term>> getConceptTerms() {
		return conceptTerms;
	}
}
