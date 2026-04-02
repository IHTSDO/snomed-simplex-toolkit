package org.snomed.simplex.diagram;

import io.micrometer.common.util.StringUtils;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.config.DiagramResourceManagerConfiguration;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.snomed.simplex.util.FileUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class DiagramResourceRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(DiagramResourceRepository.class);
	private final ResourceManager resourceManager;

	public DiagramResourceRepository(DiagramResourceManagerConfiguration resourceManagerConfiguration, ResourceLoader resourceLoader) {
		resourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);
		if (!resourceManagerConfiguration.isUseCloud()) {
			String path = resourceManagerConfiguration.getLocal().getPath();
			if (!StringUtils.isEmpty(path)) {
				File localDir = new File(path);
				if (localDir.mkdirs()) {
					LOGGER.warn("Local directory {} created for diagram storage", path);
				}
			}
		}
	}

	public void writeDiagram(String relativePath, File tempFile) throws ServiceExceptionWithStatusCode {
		try (FileInputStream in = new FileInputStream(tempFile)) {
			writeDiagram(relativePath, in);
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode("Failed to store diagram", HttpStatus.INTERNAL_SERVER_ERROR, e);
		} finally {
			FileUtils.deleteOrLogWarning(tempFile);
		}
	}

	public void writeDiagram(String relativePath, InputStream inputStream) throws IOException {
		resourceManager.writeResource(relativePath, inputStream);
	}

	public boolean exists(String relativePath) throws IOException {
		return resourceManager.doesObjectExist(relativePath);
	}

	public InputStream readDiagram(String relativePath) throws IOException {
		return resourceManager.readResourceStream(relativePath);
	}

	public static String newStagingFile() throws IOException {
		return File.createTempFile(UUID.randomUUID().toString(), ".png").getAbsolutePath();
	}
}
