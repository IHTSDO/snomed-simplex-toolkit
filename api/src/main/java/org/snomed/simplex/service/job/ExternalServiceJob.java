package org.snomed.simplex.service.job;

public class ExternalServiceJob extends AsyncJob {

	private String branch;
	private String link;

	public ExternalServiceJob(String codeSystem, String display) {
		super(codeSystem, display);
	}

	@Override
	public JobType getJobType() {
		return JobType.EXTERNAL_SERVICE;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public String getBranch() {
		return branch;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}
}
