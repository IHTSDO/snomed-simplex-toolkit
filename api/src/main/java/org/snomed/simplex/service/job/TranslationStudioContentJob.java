package org.snomed.simplex.service.job;

import org.snomed.simplex.client.domain.CodeSystem;

public class TranslationStudioContentJob extends ContentJob {

	public TranslationStudioContentJob(CodeSystem codeSystem, String display, String refsetId) {
		super(codeSystem, display, refsetId);
	}

	@Override
	public JobType getJobType() {
		return JobType.TRANSLATION_STUDIO;
	}
}
