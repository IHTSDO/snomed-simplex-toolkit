package org.snomed.simplex.client.srs.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.DescriptionMini;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseManifest;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseManifestFile;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseManifestFolder;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.String.format;
import static org.snomed.simplex.util.CollectionUtils.orEmpty;

@Service
public class ReleaseManifestService {

	private final MappingJackson2XmlHttpMessageConverter xmlConverter;

	public ReleaseManifestService(@Autowired MappingJackson2XmlHttpMessageConverter xmlConverter) {
		this.xmlConverter = xmlConverter;
	}

	@SuppressWarnings("unchecked")
	public String generateManifestXml(CodeSystem codeSystem, String productName, String effectiveTime, boolean editionPackage, SnowstormClient snowstormClient) throws ServiceException {
		String formattedName = productName.replace(" ", "");
		String rootFolderName = String.format("SnomedCT_%sSimplexEdition_Production_%sT120000Z", formattedName, effectiveTime);
		ReleaseManifestFolder rootFolder = new ReleaseManifestFolder(rootFolderName);
		ReleaseManifest manifest = new ReleaseManifest(rootFolder);
		rootFolder.getOrAddFile(format("Readme_en_%s.txt", effectiveTime)).clearSource();

		ReleaseManifestFolder snapshotFolder = rootFolder.getOrAddFolder("Snapshot");

		ReleaseManifestFolder terminologyFolder = snapshotFolder.getOrAddFolder("Terminology");
		terminologyFolder.getOrAddFile(getCoreComponentFilename("Concept", "", formattedName, effectiveTime));

		List<String> languageCodes = new ArrayList<>(codeSystem.getLanguages().keySet());
		for (String languageCode : languageCodes) {
			terminologyFolder.getOrAddFile(getCoreComponentFilename("Description", format("-%s", languageCode), formattedName, effectiveTime))
					.addLanguageCode(languageCode);
		}
		for (String languageCode : languageCodes) {
			terminologyFolder.getOrAddFile(getCoreComponentFilename("TextDefinition", format("-%s", languageCode), formattedName, effectiveTime))
					.addLanguageCode(languageCode);
		}
		terminologyFolder.getOrAddFile(getCoreComponentFilename("Identifier", "", formattedName, effectiveTime));
		terminologyFolder.getOrAddFile(getCoreComponentFilename("Relationship", "", formattedName, effectiveTime));
		terminologyFolder.getOrAddFile(getCoreComponentFilename("RelationshipConcreteValues", "", formattedName, effectiveTime));
		// OWLExpression file is added by the refset logic

		boolean filterByModule = !editionPackage;
		Map<String, ConceptMini> refsets = snowstormClient.getCodeSystemRefsetsWithTypeInformation(codeSystem, filterByModule);
		ReleaseManifestFolder refsetFolder = snapshotFolder.getOrAddFolder("Refset");
		Set<String> refsetsWithMissingExportConfiguration = new HashSet<>();
		for (ConceptMini refset : refsets.values()) {
			if (refset.getConceptId().equals("554481000005106")) {// Ignore badly set up DK refset
				continue;
			}
			DescriptionMini pt = refset.getPt();
			String exportDir = null;
			String exportName = null;
			String fieldTypes = null;
			List<String> fieldNameList = null;
			Map<String, Object> extraFields = refset.getExtraFields();
			if (extraFields != null) {
				Map<String, Object> referenceSetType = (Map<String, Object>) extraFields.get("referenceSetType");
				if (referenceSetType != null && referenceSetType.get("fileConfiguration") != null) {
					Map<String, Object> fileConfiguration = (Map<String, Object>) referenceSetType.get("fileConfiguration");
					if (fileConfiguration != null) {
						exportDir = (String) fileConfiguration.get("exportDir");
						exportName = (String) fileConfiguration.get("name");
						fieldTypes = (String) fileConfiguration.get("fieldTypes");
						fieldNameList = (List<String>) fileConfiguration.get("fieldNameList");
					}
				}
			}
			if (exportDir == null || fieldTypes == null) {
				refsetsWithMissingExportConfiguration.add(refset.getConceptId());
				continue;
			}
			ReleaseManifestFolder outputFolder = exportDir.startsWith("/") ? snapshotFolder : refsetFolder;
			String[] split = exportDir.split("/");
			for (String folderName : split) {
				if (!folderName.isEmpty()) {
					outputFolder = outputFolder.getOrAddFolder(folderName);
				}
			}
			String refsetFileName = getRefsetFilename(pt.getTerm(), exportName, fieldTypes, formattedName, effectiveTime);
			ReleaseManifestFile refsetFile = outputFolder.getOrAddFile(refsetFileName);
			if (refsetFile.getField() == null) {
				for (String fieldName : fieldNameList) {
					refsetFile.addField(fieldName);
				}
			}
			refsetFile.addRefset(refset.getConceptId(), pt.getTerm());
		}
		if (!refsetsWithMissingExportConfiguration.isEmpty()) {
			throw new ServiceException(format("Unable to generate build manifest file because the following refsets do not have an export configuration: %s",
					refsetsWithMissingExportConfiguration));
		}

		cloneSnapshotToFull(snapshotFolder, rootFolder.getOrAddFolder("Full"));

		ObjectMapper objectMapper = xmlConverter.getObjectMapper()
				.configure(SerializationFeature.INDENT_OUTPUT, true);
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

		try {
			return objectMapper.writeValueAsString(manifest);
		} catch (JsonProcessingException e) {
			throw new ServiceException(format("Failed to write release manifest as XML for code system %s.", codeSystem.getShortName()), e);
		}
	}

	private void cloneSnapshotToFull(ReleaseManifestFolder source, ReleaseManifestFolder target) {
		for (ReleaseManifestFile file : orEmpty(source.getFile())) {
			String name = file.getName().replace("_Snapshot", "_Full").replace("Snapshot_", "Full_");
			target.addFile(file.copy(name));
		}
		for (ReleaseManifestFolder folder : orEmpty(source.getFolder())) {
			cloneSnapshotToFull(folder, target.getOrAddFolder(folder.getName()));
		}
	}

	private String getCoreComponentFilename(String exportName, String langPostfix, String formattedName, String effectiveTime) {
		return format("sct2_%s_%s%s_%s_%s.txt", exportName, "Snapshot", langPostfix, formattedName, effectiveTime);
	}

	private String getRefsetFilename(String term, String exportName, String fieldTypes, String formattedName, String effectiveTime) {
		String prefix = "der2";
		if (exportName.equals("OWLExpression")) {
			prefix = "sct2";
		} else {
			StringBuilder builder = new StringBuilder();
			String[] split = term.split(" ");
			for (String word : split) {
				builder.append(word.substring(0, 1).toUpperCase());
				if (word.length() > 1) {
					builder.append(word.substring(1));
				}
			}
			exportName = builder.toString();
		}
		return format("%s_%sRefset_%s%s_%s_%s.txt", prefix, fieldTypes, exportName, "Snapshot", formattedName, effectiveTime);
	}

}
