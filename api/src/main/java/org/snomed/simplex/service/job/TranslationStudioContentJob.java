package org.snomed.simplex.service.job;

import org.snomed.simplex.client.domain.CodeSystem;

import java.util.Date;

public class TranslationStudioContentJob extends ContentJob {

	public TranslationStudioContentJob(CodeSystem codeSystem, String display, String refsetId) {
		super(codeSystem, display, refsetId);
	}

	public TranslationStudioContentJob(String codeSystemShortName, String display, String refsetId, String id, Date created) {
		super(null, codeSystemShortName, display, id, created, refsetId);
	}

	@Override
	public JobType getJobType() {
		return JobType.TRANSLATION_STUDIO;
	}
}
