package org.snomed.simplex.service;

import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.Component;
import org.snomed.simplex.config.ActivityResourceManagerConfiguration;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.domain.activity.ActivityType;
import org.snomed.simplex.domain.activity.ComponentType;
import org.snomed.simplex.exceptions.ServiceException;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.external.ExternalFunctionJobService;
import org.snomed.simplex.service.job.ContentJob;
import org.snomed.simplex.service.job.ExternalServiceJob;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;
import java.util.Optional;

@Service
public class ActivityService {

	private final ActivityRepository repository;
	private final ResourceManager resourceManager;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ActivityService(ActivityRepository repository, ActivityResourceManagerConfiguration resourceManagerConfiguration,
			ResourceLoader resourceLoader, @Value("${user-activity.end-on-startup}") boolean endActivitiesOnStartup) {

		this.repository = repository;
		resourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);

		// Remove entries with old activity-types
		repository.deleteAllByActivityType("STOP_RELEASE_PREP");
		repository.deleteAllByActivityType("REMOVE_CONTENT_APPROVAL");
		repository.deleteAllByActivityType("ADD_CONTENT_APPROVAL");
		repository.deleteAllByActivityType("START_BUILD");

		if (endActivitiesOnStartup) {
			// End activities that can't recover
			List<Activity> runningActivities = repository.findActivitiesByEndDate(null);
			List<Activity> activitiesToEnd = runningActivities.stream()
					.filter(activity -> activity.getActivityType() != ActivityType.FINALIZE_RELEASE).toList();
			for (Activity activity : activitiesToEnd) {
				activity.setError(true);
				activity.setMessage("Simplex was restarted. Please try again.");
				activity.end();
			}
			if (!activitiesToEnd.isEmpty()) {
				repository.saveAll(activitiesToEnd);
			}
			logger.info("{} activities ended with error due to restart.", activitiesToEnd.size());
		} else {
			logger.info("Skipping ending activities during startup.");
		}
	}

	public org.snomed.simplex.domain.Page<Activity> findActivities(String codesystem, String componentId, PageRequest pageRequest) {
		org.springframework.data.domain.Page<Activity> springPage;
		if (componentId == null) {
			springPage = repository.findActivitiesByCodesystemIsOrderByStartDateDesc(codesystem, pageRequest);
		} else {
			springPage = repository.findActivitiesByCodesystemIsAndComponentIdOrderByStartDateDesc(codesystem, componentId, pageRequest);
		}
		return new Page<>(springPage);
	}

	public Activity findActivityOrThrow(String codeSystem, Long startDate) throws ServiceExceptionWithStatusCode {
		Optional<Activity> activity = repository.findActivityByCodesystemAndStartDate(codeSystem, startDate);
		return activity.orElseThrow(() -> new ServiceExceptionWithStatusCode("Activity not found.", HttpStatus.NOT_FOUND));
	}

	public Activity findLatestByCodeSystemAndActivityType(String codeSystem, ActivityType activityType) {
		PageRequest pageRequest = PageRequest.of(0, 1);
		org.springframework.data.domain.Page<Activity> page =
				repository.findActivityByCodesystemAndActivityTypeOrderByStartDateDesc(codeSystem, activityType, pageRequest);
		return page.isEmpty() ? null : page.iterator().next();
	}

	public InputStream getActivityInputFile(Activity activity) throws IOException {
		String fileUpload = activity.getFileUpload();
		if (fileUpload == null) {
			return null;
		}
		return resourceManager.readResourceStreamOrNullIfNotExists(fileUpload);
	}

	public <T> T runActivity(String codeSystem, ComponentType componentType, ActivityType activityType,
			ServiceCallable<T> callable) throws ServiceException {
		return doRunActivity(codeSystem, componentType, activityType, null, callable);
	}

	public void runActivity(String codeSystem, ComponentType componentType, ActivityType activityType, String componentId,
			ServiceCallable<Void> callable) throws ServiceException {
		doRunActivity(codeSystem, componentType, activityType, componentId, callable);
	}

	private <T> T doRunActivity(String codeSystem, ComponentType componentType, ActivityType activityType, String componentId,
			ServiceCallable<T> callable) throws ServiceException {

		Activity activity = createActivity(codeSystem, componentType, activityType);
		activity.setComponentId(componentId);
		try {
			T result = callable.call();
			if (activityType == ActivityType.CREATE && result instanceof Component component) {
				// Populate newly created component id
				activity.setComponentId(component.getId());
			}
			return result;
		} catch (ServiceException e) {
			activity.exception(e);
			throw e;
		} finally {
			// Instant activities end straight away
			activity.end();
			repository.save(activity);
		}
	}

	public void addQueuedContentActivity(Activity activity, ContentJob job) throws ServiceException {
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

		logger.info("Recording user activity {}", activity);
		repository.save(activity);
	}

	public <T> ExternalServiceJob startExternalServiceActivity(CodeSystem codeSystem, ComponentType componentType, ActivityType activityType,
			ExternalFunctionJobService<T> service, T param) throws ServiceException {

		Activity activity = createActivity(codeSystem.getShortName(), componentType, activityType);
		try {
			return service.callService(codeSystem, activity, param);
		} catch (ServiceException e) {
			// External service function call failed
			activity.exception(e);
			throw e;
		} finally {
			// Long-running activities are not ended here
			repository.save(activity);
		}
	}

	public void endAsynchronousActivity(Activity activity) {
		activity.end();
		repository.save(activity);
	}

	private static @NotNull Activity createActivity(String codeSystem, ComponentType componentType, ActivityType activityType) {
		return new Activity(SecurityUtil.getUsername(), codeSystem, componentType, activityType);
	}

	protected String getFilePath(Activity activity, String fileExtension) {
		Date startDate = activity.getStartDate();
		String yearMonthDay = new SimpleDateFormat("yyyy_MM_dd").format(startDate);
		String dayMonthYearHourMinuteSecondMilli = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS").format(startDate);
		return "%s/%s/%s.%s".formatted(activity.getCodesystem(), yearMonthDay, dayMonthYearHourMinuteSecondMilli, fileExtension);
	}
}
