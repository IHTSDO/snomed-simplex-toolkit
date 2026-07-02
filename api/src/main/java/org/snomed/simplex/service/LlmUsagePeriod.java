package org.snomed.simplex.service;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;

public enum LlmUsagePeriod {
	DAY("day", 1),
	WEEK("week", 7),
	MONTH("month", 30),
	THREE_MONTHS("3months", 90),
	SIX_MONTHS("6months", 180),
	YEAR("year", 365),
	ALL("all", -1);

	private final String param;
	private final int days;

	LlmUsagePeriod(String param, int days) {
		this.param = param;
		this.days = days;
	}

	public String getParam() {
		return param;
	}

	public int getDays() {
		return days;
	}

	public static LlmUsagePeriod fromParam(String param) throws ServiceExceptionWithStatusCode {
		if (param == null || param.isBlank()) {
			throw new ServiceExceptionWithStatusCode("period is required", HttpStatus.BAD_REQUEST);
		}
		for (LlmUsagePeriod period : values()) {
			if (period.param.equalsIgnoreCase(param)) {
				return period;
			}
		}
		throw new ServiceExceptionWithStatusCode("Invalid period: " + param, HttpStatus.BAD_REQUEST);
	}
}
