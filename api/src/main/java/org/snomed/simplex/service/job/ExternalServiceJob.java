package org.snomed.simplex.service.job;

import org.snomed.simplex.client.domain.CodeSystem;

public class ExternalServiceJob extends AsyncJob {

	private final String branch;
	private final long contentHeadTimestamp;
	private String link;

	public ExternalServiceJob(String codeSystem, String display, String branch, long contentHeadTimestamp) {
		super(codeSystem, display);
		this.branch = branch;
		this.contentHeadTimestamp = contentHeadTimestamp;
	}

	public ExternalServiceJob(CodeSystem codeSystem, String display) {
		super(codeSystem.getShortName(), display);
		branch = codeSystem.getWorkingBranchPath();
		contentHeadTimestamp = codeSystem.getContentHeadTimestamp();
	}

	@Override
	public JobType getJobType() {
		return JobType.EXTERNAL_SERVICE;
	}

	public String getBranch() {
		return branch;
	}

	public long getContentHeadTimestamp() {
		return contentHeadTimestamp;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}
}
