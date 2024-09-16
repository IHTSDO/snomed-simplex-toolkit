package org.snomed.simplex.service;

import org.junit.jupiter.api.Test;
import org.snomed.simplex.TestConfig;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
class ActivityServiceTest {

	private final ActivityService activityService;

	public ActivityServiceTest(@Autowired ActivityService activityService) {
		this.activityService = activityService;
	}

	@Test
	void getFilePath() {
		Activity activity = new Activity("SNOMEDCT-TEST", ComponentType.SUBSET, ActivityType.UPDATE);
		GregorianCalendar calendar = new GregorianCalendar(2024, Calendar.OCTOBER, 16, 15, 33, 50);
		calendar.set(Calendar.MILLISECOND, 6);
		activity.setStartDate(calendar.getTime());

		assertEquals("SNOMEDCT-TEST/2024_10_16/2024_10_16_15_33_50_006.txt", activityService.getFilePath(activity, "txt"));
		assertEquals("SNOMEDCT-TEST/2024_10_16/2024_10_16_15_33_50_006.csv", activityService.getFilePath(activity, "csv"));
	}
}
