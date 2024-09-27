package org.snomed.simplex.service;

import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.config.ActivityResourceManagerConfiguration;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.job.AsyncJob;
import org.snomed.simplex.service.job.ContentJob;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

@Service
public class ActivityService {

	private final ActivityRepository repository;
	private final ResourceManager resourceManager;

	public ActivityService(ActivityRepository repository, ActivityResourceManagerConfiguration resourceManagerConfiguration,
			ResourceLoader resourceLoader) {

		this.repository = repository;
		resourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);

		// Remove entries with old activity-types
		repository.deleteAllByActivityType("STOP_RELEASE_PREP");
	}

	public org.snomed.simplex.domain.Page<Activity> findActivities(String codesystem, PageRequest pageRequest) {
		org.springframework.data.domain.Page<Activity> springPage = repository.findActivitiesByCodesystemIsOrderByStartDateDesc(codesystem, pageRequest);
		return new Page<>(springPage);
	}

	public Activity findActivityOrThrow(String codeSystem, Long startDate) throws ServiceExceptionWithStatusCode {
		Optional<Activity> activity = repository.findActivityByCodesystemAndStartDate(codeSystem, startDate);
		return activity.orElseThrow(() -> new ServiceExceptionWithStatusCode("Activity not found.", HttpStatus.NOT_FOUND));
	}

	public InputStream getActivityInputFile(Activity activity) throws IOException {
		String fileUpload = activity.getFileUpload();
		if (fileUpload == null) {
			return null;
		}
		return resourceManager.readResourceStreamOrNullIfNotExists(fileUpload);
	}

	public <T> T recordActivity(String codeSystem, ComponentType componentType, ActivityType activityType,
			ServiceCallable<T> callable) throws ServiceException {

		Activity activity = new Activity(SecurityUtil.getUsername(), codeSystem, componentType, activityType);
		try {
			return callable.call();
		} catch (ServiceException e) {
			activity.exception(e);
			throw e;
		} finally {
			activity.end();
			repository.save(activity);
		}
	}

	public void recordQueuedActivity(Activity activity, AsyncJob asyncJob) throws ServiceException {
		if (asyncJob instanceof ContentJob job) {
			// Save user upload file
			File userInputFile = job.getInputFileCopy();
			String inputFileOriginalName = job.getInputFileOriginalName();
			String fileExtension = "txt";
			if (inputFileOriginalName != null && inputFileOriginalName.contains(".")) {
				String inputFileExtension = inputFileOriginalName.substring(inputFileOriginalName.lastIndexOf(".") + 1);
				if (!inputFileExtension.isEmpty()) {
					fileExtension = inputFileExtension;
				}
			}
			if (userInputFile != null) {
				String filePath = getFilePath(activity, fileExtension);
				try (InputStream inputStream = new FileInputStream(userInputFile)){
					resourceManager.writeResource(filePath, inputStream);
					activity.setFileUpload(filePath);
				} catch (IOException e) {
					throw new ServiceException("Failed to save user input file for activity tracker.", e);
				}
			}
		}

		Logger logger = LoggerFactory.getLogger(getClass());
		logger.info("Recording user activity {}", activity);
		repository.save(activity);
	}

	public void recordCompletedActivity(Activity activity) {
		activity.end();
		repository.save(activity);
	}

	protected String getFilePath(Activity activity, String fileExtension) {
		Date startDate = activity.getStartDate();
		String yearMonthDay = new SimpleDateFormat("yyyy_MM_dd").format(startDate);
		String dayMonthYearHourMinuteSecondMilli = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS").format(startDate);
		return "%s/%s/%s.%s".formatted(activity.getCodesystem(), yearMonthDay, dayMonthYearHourMinuteSecondMilli, fileExtension);
	}
}
