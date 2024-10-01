package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.domain.activity.Activity;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.service.ActivityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("api/{codeSystem}/activities")
@Tag(name = "Activity Tracker", description = "-")
public class ActivityController {

	private final ActivityService activityService;

	public ActivityController(ActivityService activityService) {
		this.activityService = activityService;
	}

	@GetMapping
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Page<Activity> getActivities(@PathVariable String codeSystem,
			@RequestParam(required = false) String componentId,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit) {

		return activityService.findActivities(codeSystem, componentId, ControllerHelper.getPageRequest(offset, limit));
	}

	@GetMapping(path = "/{startDate}")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public Activity getActivity(@PathVariable String codeSystem, @PathVariable Long startDate) throws ServiceExceptionWithStatusCode {
		return activityService.findActivityOrThrow(codeSystem, startDate);
	}

	@GetMapping(path = "/{startDate}/input-file")
	@PreAuthorize("hasPermission('AUTHOR', #codeSystem)")
	public void getActivityInputFile(@PathVariable String codeSystem, @PathVariable Long startDate, HttpServletResponse response)
			throws ServiceExceptionWithStatusCode, IOException {

		Activity activity = activityService.findActivityOrThrow(codeSystem, startDate);
		String fileUpload = activity.getFileUpload();
		if (fileUpload == null) {
			throw new ServiceExceptionWithStatusCode("Activity has no file upload", HttpStatus.NOT_FOUND);
		}
		String filename = fileUpload.substring(fileUpload.lastIndexOf('/') + 1);
		response.setHeader("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename));
		try (InputStream activityInputFile = activityService.getActivityInputFile(activity)) {
			StreamUtils.copy(activityInputFile, response.getOutputStream());
		}
	}

}
