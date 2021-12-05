package com.snomed.derivativemanagementtool;

import com.snomed.derivativemanagementtool.client.SnowstormClient;
import com.snomed.derivativemanagementtool.domain.Product;
import com.snomed.derivativemanagementtool.service.RefsetProductUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@SpringBootApplication
public class Application implements CommandLineRunner {

	@Autowired
	private SnowstormClient snowstormClient;

	@Autowired
	private RefsetProductUpdateService refsetProductUpdateService;

	@Override
	public void run(String... args) throws Exception {
		System.out.println();
		System.out.println("Starting SNOMED-CT derivative management process..");
		System.out.println();
		snowstormClient.ping();
		System.out.println();

		File productsDir = getProductsDir();
		List<Product> products = getProducts(productsDir);
		System.out.println();
		if (products.isEmpty()) {
			System.out.println("No products found.");
		} else {
			System.out.printf("Found %s products:%n", products.size());
			for (Product product : products) {
				System.out.printf("- %s%n", product);
			}
		}
		System.out.println();

		for (Product product : products) {
			// Update
			runUpdate(product);
		}

		System.out.println();
		System.out.println();
	}

	private void runUpdate(Product product) {
		// Assume simple refset for now
		refsetProductUpdateService.update(product);
	}

	private List<Product> getProducts(File productsDir) throws IOException {
		List<Product> products = new ArrayList<>();
		File[] files = productsDir.listFiles(File::isDirectory);
		if (files != null) {
			for (File productDir : files) {
				File config = new File(productDir, "config.properties");
				if (config.isFile()) {
					Properties properties = new Properties();
					properties.load(new FileReader(config));
					String name = properties.getProperty("name");
					String codesystem = properties.getProperty("codesystem");
					String module = properties.getProperty("module");
					String refsetId = properties.getProperty("refsetId");
					products.add(new Product(productDir, name, codesystem, module, refsetId));
				}
			}
		}
		return products;
	}

	private File getProductsDir() {
		File products = new File("products");
		if (!products.isDirectory()) {
			if (products.mkdirs()) {
				System.out.println("Created 'products' directory.");
			} else {
				System.err.println("Failed to create 'products' directory.");
				System.exit(1);
			}
		} else {
			System.out.println("Found 'products' directory.");
		}
		return products;
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
