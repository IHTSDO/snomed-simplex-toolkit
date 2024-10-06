package org.snomed.simplex.service.job;

import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.domain.activity.Activity;

public class ExternalServiceJob extends AsyncJob {

	private final String branch;
	private final long contentHeadTimestamp;
	private String link;
	private Activity activity;

	public ExternalServiceJob(CodeSystem codeSystem, String display, String branch, long contentHeadTimestamp) {
		super(codeSystem, display);
		this.branch = branch;
		this.contentHeadTimestamp = contentHeadTimestamp;
	}

	public ExternalServiceJob(CodeSystem codeSystem, String display) {
		super(codeSystem, display);
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

	public Activity getActivity() {
		return activity;
	}

	public void setActivity(Activity activity) {
		this.activity = activity;
	}

	@Override
	public String toString() {
		return "ExternalServiceJob{" +
				super.toString() +
				", 'branch='" + branch + '\'' +
				", contentHeadTimestamp=" + contentHeadTimestamp +
				", link='" + link + '\'' +
				'}';
	}
}
