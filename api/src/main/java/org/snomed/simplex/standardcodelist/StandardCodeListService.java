package org.snomed.simplex.standardcodelist;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class StandardCodeListService {

	private final StandardCodeListRepository repository;

	public StandardCodeListService(StandardCodeListRepository repository) {
		this.repository = repository;
	}

	public List<StandardCodeListMetadata> listCatalogues() {
		List<StandardCodeListMetadata> catalogues = new ArrayList<>();
		for (String catalogueId : repository.listCatalogueIds()) {
			StandardCodeListMetadata metadata = repository.readMetadataOrNull(catalogueId);
			if (metadata != null) {
				catalogues.add(metadata);
			}
		}
		return catalogues;
	}

	public StandardCodeListMetadata getCatalogueOrThrow(String catalogueId) throws ServiceExceptionWithStatusCode {
		StandardCodeListMetadata metadata = repository.readMetadataOrNull(catalogueId);
		if (metadata == null) {
			throw new ServiceExceptionWithStatusCode("Standard code list catalogue not found: " + catalogueId, HttpStatus.NOT_FOUND);
		}
		return metadata;
	}

	public StandardCodeListValueSetMetadata getValueSetOrThrow(String catalogueId, String valueSetName) throws ServiceExceptionWithStatusCode {
		StandardCodeListMetadata catalogue = getCatalogueOrThrow(catalogueId);
		return catalogue.getValueSets().stream()
				.filter(valueSet -> valueSet.getName().equals(valueSetName))
				.findFirst()
				.orElseThrow(() -> new ServiceExceptionWithStatusCode(
						"Value set '%s' not found in standard code list catalogue '%s'.".formatted(valueSetName, catalogueId),
						HttpStatus.NOT_FOUND));
	}

	public List<String> getConceptIds(String catalogueId, String valueSetName) throws ServiceExceptionWithStatusCode {
		StandardCodeListValueSetMetadata valueSet = getValueSetOrThrow(catalogueId, valueSetName);
		try {
			return repository.readConceptCodes(catalogueId, valueSet.getFile());
		} catch (IOException e) {
			throw new ServiceExceptionWithStatusCode(
					"Failed to read standard code list value set '%s'.".formatted(valueSetName), HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}
}
