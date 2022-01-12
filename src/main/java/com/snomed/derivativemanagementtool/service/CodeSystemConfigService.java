package com.snomed.derivativemanagementtool.service;

import com.snomed.derivativemanagementtool.domain.CodeSystemProperties;
import com.snomed.derivativemanagementtool.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Properties;

@Service
public class CodeSystemConfigService {

	public static final String LOCAL_FILES_PATH = "local-files";
	public static final String CODE_SYSTEM_PROPERTIES = "code-system.properties";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public CodeSystemProperties getConfig() throws ServiceException {
		Properties properties = new Properties();
		File propertiesFile = getOrCreateCodeSystemPropertiesFile();
		try {
			properties.load(new FileReader(propertiesFile));
			logger.debug("Read '{}' file.", CODE_SYSTEM_PROPERTIES);
			return new CodeSystemProperties(properties);
		} catch (IOException e) {
			throw new ServiceException("Failed to read properties file.", e);
		}
	}

	public void saveConfig(CodeSystemProperties codeSystemProperties) throws ServiceException {
		File propertiesFile = getOrCreateCodeSystemPropertiesFile();
		Properties properties = codeSystemProperties.createProperties();
		try (FileOutputStream outputStream = new FileOutputStream(propertiesFile)) {
			properties.store(outputStream, null);
			logger.debug("Stored '{}' file.", CODE_SYSTEM_PROPERTIES);
		} catch (IOException e) {
			throw new ServiceException("Failed to save properties file.", e);
		}
	}

	private File getOrCreateCodeSystemPropertiesFile() throws ServiceException {
		File productsDirectory = getOrCreateProductsDir();
		File file = new File(productsDirectory, CODE_SYSTEM_PROPERTIES);
		if (!file.isFile()) {
			try {
				if (!file.createNewFile()) {
					throw new ServiceException("Failed to create product properties file.");
				}
			} catch (IOException e) {
				throw new ServiceException("Failed to create product properties file.", e);
			}
		} else {
			logger.debug("Found '{}' file.", CODE_SYSTEM_PROPERTIES);
		}
		return file;
	}

	private File getOrCreateProductsDir() {
		File products = new File(LOCAL_FILES_PATH);
		if (!products.isDirectory()) {
			if (products.mkdirs()) {
				System.out.printf("Created '%s' directory.%n", LOCAL_FILES_PATH);
			} else {
				System.err.printf("Failed to create '%s' directory.%n", LOCAL_FILES_PATH);
				System.exit(1);
			}
		} else {
			logger.debug("Found '{}' directory.", LOCAL_FILES_PATH);
		}
		return products;
	}
}
