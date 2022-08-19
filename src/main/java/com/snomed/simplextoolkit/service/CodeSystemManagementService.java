package com.snomed.simplextoolkit.service;

import com.snomed.simplextoolkit.domain.Product;
import com.snomed.simplextoolkit.exceptions.ServiceException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CodeSystemManagementService {

	public void init() throws ServiceException {
//
//		CodeSystemProperties codeSystemProperties = codeSystemConfigService.getCodeSystemProperties();
//
//		File productsDir = getProductsDir();
//		List<Product> products = getProducts(productsDir);
//		System.out.println();
//		if (products.isEmpty()) {
//			System.out.println("No products found.");
//		} else {
//			System.out.printf("Found %s products:%n", products.size());
//			for (Product product : products) {
//				System.out.printf("- %s%n", product);
//			}
//		}
//		System.out.println();
//
//		for (Product product : products) {
//			// Update
//			runUpdate(product);
//		}
//
//		System.out.println();
//		System.out.println();
//
//		snowstormClient.ping();
//		System.out.println();
	}

	private List<Product> getProducts(File productsDir) throws IOException {
		List<Product> products = new ArrayList<>();
		File[] files = productsDir.listFiles(File::isDirectory);
		if (files != null) {
			for (File productDir : files) {
				File config = new File(productDir, "code-system.properties");
				File[] productFiles = productDir.listFiles();
				if (config.isFile() && productFiles != null) {
					Properties properties = new Properties();
					properties.load(new FileReader(config));
					String name = properties.getProperty("name");
					String codesystem = properties.getProperty("codesystem");
					String module = properties.getProperty("module");
					String refsetId = properties.getProperty("refsetId");

					// Refset or Translation?
					boolean descriptionSnapshotFound = false;
					boolean otherRefsetFound = false;
					for (File productFile : productFiles) {
						if (productFile.isFile()) {
							String productFilename = productFile.getName();
							if (productFilename.startsWith("sct2_Description_Snapshot") && productFilename.endsWith(".txt")) {
								descriptionSnapshotFound = true;
							} else if (productFilename.endsWith(".txt") || productFilename.endsWith(".xlsx")) {
								otherRefsetFound = true;
							} else if (productFilename.endsWith(".xls")) {
								System.err.println("Warning: Old style Excel files with file extension '.xls' can not be processed. " +
										"Please use the newer XLSX filetype (Excel 2007 and later).");
							}
						}
					}

					if (!descriptionSnapshotFound && !otherRefsetFound) {
						System.err.println("Product does not appear to have any input files. Can not detect product type.");
					}

					products.add(new Product(productDir, name, codesystem, module, refsetId));
				}
			}
		}
		return products;
	}

}
