package org.snomed.simplex.weblate.rf2;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.ihtsdo.otf.snomedboot.domain.Concept;
import org.ihtsdo.otf.snomedboot.domain.Description;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentStore;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentStoreComponentFactoryImpl;
import org.snomed.simplex.client.domain.Concepts;

import java.util.Set;

public class RF2LoadingComponentFactoryWithPT extends ComponentStoreComponentFactoryImpl {

	private final String languageRefsetId;
	private final Set<Long> synonymIds = new LongOpenHashSet();
	private final Set<Long> preferredSynonymIds = new LongOpenHashSet();

	public RF2LoadingComponentFactoryWithPT(ComponentStore componentStore, String languageRefsetId) {
		super(componentStore);
		this.languageRefsetId = languageRefsetId;
	}

	@Override
	public void newDescriptionState(String id, String effectiveTime, String active, String moduleId, String conceptId, String languageCode, String typeId, String term, String caseSignificanceId) {
		super.newDescriptionState(id, effectiveTime, active, moduleId, conceptId, languageCode, typeId, term, caseSignificanceId);
		if ("1".equals(active) && Concepts.SYNONYM.equals(typeId)) {
			synonymIds.add(Long.parseLong(id));
		}
	}

	@Override
	public void newReferenceSetMemberState(String filename, String[] fieldNames, String id, String effectiveTime, String active, String moduleId, String refsetId, String referencedComponentId, String... otherValues) {
		super.newReferenceSetMemberState(filename, fieldNames, id, effectiveTime, active, moduleId, refsetId, referencedComponentId, otherValues);
		long descriptionId = Long.parseLong(referencedComponentId);
		if ("1".equals(active) && languageRefsetId.equals(refsetId) && Concepts.PREFERRED.equals(otherValues[0]) && synonymIds.contains(descriptionId)) {
			preferredSynonymIds.add(descriptionId);
		}
	}

	public String getPt(Concept concept) {
		for (Description description : concept.getDescriptions()) {
			if (preferredSynonymIds.contains(description.getId())) {
				return description.getTerm();
			}
		}
		return "";
	}
}
