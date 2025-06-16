package org.snomed.simplex.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Version", description = "Build Version")
@RequestMapping("api")
public class VersionController {

	private final BuildProperties buildProperties;

	public VersionController(@Autowired(required = false) BuildProperties buildProperties) {
		this.buildProperties = buildProperties;
	}

	@Operation(summary = "Software build version and timestamp.")
	@RequestMapping(value = "/version", method = RequestMethod.GET, produces = "application/json")
	public BuildVersion getBuildInformation() {
		if (buildProperties == null) {
			return new BuildVersion("Dev", null);
		}
		return new BuildVersion(buildProperties.getVersion(), buildProperties.getTime().toString());
	}

	public static class BuildVersion {

		private String version;
		private String time;

		public BuildVersion(String version, String time) {
			this.version = version;
			this.time = time;
		}

		public String getVersion() {
			return version;
		}

		public String getTime() {
			return time;
		}
	}

}
