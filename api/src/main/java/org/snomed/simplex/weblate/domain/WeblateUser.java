package org.snomed.simplex.weblate.domain;

import com.fasterxml.jackson.annotation.JsonGetter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeblateUser {
	private int id;
	private String email;
	private String fullName;
	private String username;
	private Set<String> groupUrls;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	@JsonGetter("full_name")
	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@JsonGetter("groups")
	public Set<String> getGroupUrls() {
		return groupUrls;
	}

	public void setGroupUrls(Set<String> groupUrls) {
		this.groupUrls = groupUrls;
	}

	public Set<Integer> getGroupIds() {
		if (groupUrls == null) {
			return Collections.emptySet();
		}
		Set<Integer> groupIds = new HashSet<>();
		Pattern pattern = Pattern.compile(".*/groups/([\\d]+)/.*");
		for (String groupUrl : groupUrls) {
			Matcher matcher = pattern.matcher(groupUrl);
			if (matcher.matches()) {
				groupIds.add(Integer.parseInt(matcher.group(1)));
			}
		}
		return groupIds;
	}
}
