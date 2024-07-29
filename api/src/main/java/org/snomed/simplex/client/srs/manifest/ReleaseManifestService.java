package org.snomed.simplex.client.srs.manifest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.snomed.simplex.client.SnowstormClient;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.ConceptMini;
import org.snomed.simplex.client.domain.ReferencedComponent;
import org.snomed.simplex.client.domain.RefsetMember;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseManifest;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseManifestFile;
import org.snomed.simplex.client.srs.manifest.domain.ReleaseManifestFolder;
import org.snomed.simplex.domain.Page;
import org.snomed.simplex.exceptions.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.String.format;
import static org.snomed.simplex.util.CollectionUtils.orEmpty;

/**
 * Service for generating manifests to be used in the snomed-release-service.
 * Manifests are used to specify the layout of an RF2 release.
 */
@Service
public class ReleaseManifestService {

	private final MappingJackson2XmlHttpMessageConverter xmlConverter;

	public ReleaseManifestService(@Autowired MappingJackson2XmlHttpMessageConverter xmlConverter) {
		this.xmlConverter = xmlConverter;
	}

	@SuppressWarnings("unchecked")
	public String generateManifestXml(CodeSystem codeSystem, String productName, String effectiveTime, boolean editionPackage,
			SnowstormClient snowstormClient) throws ServiceException {

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
		terminologyFolder.getOrAddFile(getCoreComponentFilename("StatedRelationship", "", formattedName, effectiveTime));
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
			String exportDir = null;
			String exportName = null;
			String languageCode = null;
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
						if (exportName.equals("Language")) {
							languageCode = "-en";

							Page<RefsetMember> refsetMembers = snowstormClient.getRefsetMembers(refset.getConceptId(), codeSystem, true, 0, 5);
							if (refsetMembers.getTotal() > 0) {
								RefsetMember firstLanguageRefsetMember = refsetMembers.getItems().get(0);
								ReferencedComponent referencedComponent = firstLanguageRefsetMember.getReferencedComponent();
								if (referencedComponent != null) {
									languageCode = String.format("-%s", referencedComponent.getLang());
								}
							}
						} else {
							languageCode = "";
						}
						fieldTypes = (String) fileConfiguration.get("fieldTypes");
						fieldNameList = (List<String>) fileConfiguration.get("fieldNameList");

						// Workaround for International maps being outdated
						if (editionPackage && (exportName.equals("SimpleMapFromSCT") || exportName.equals("SimpleMapToSCT"))) {
							exportName = "SimpleMap";
							fieldNameList = List.of("mapTarget");
						}
					}
				}
			}
			if (exportDir == null || fieldTypes == null) {
				refsetsWithMissingExportConfiguration.add(refset.getConceptId());
				continue;
			}
			ReleaseManifestFolder outputFolder = getRefsetOutputFolder(exportDir, snapshotFolder, refsetFolder);
			ReleaseManifestFile refsetFile = getRefsetFile(effectiveTime, exportName, languageCode, fieldTypes, formattedName, outputFolder);
			addRefsetAndFields(refset, refsetFile, fieldNameList);
		}

		// If missing, force inclusion of empty MemberAnnotationStringValue refset
		String memberAnnotationStringRefset = "1292995002";
		if (!refsets.containsKey(memberAnnotationStringRefset)) {
			ConceptMini annotationRefset = snowstormClient.getRefset(memberAnnotationStringRefset, codeSystem);
			ReleaseManifestFolder outputFolder = getRefsetOutputFolder("Metadata", snapshotFolder, refsetFolder);
			ReleaseManifestFile refsetFile = getRefsetFile(effectiveTime, "MemberAnnotationStringValue", "", "sscs", formattedName, outputFolder);
			addRefsetAndFields(annotationRefset, refsetFile, List.of("referencedMemberId", "languageDialectCode", "typeId", "value"));

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

	private static void addRefsetAndFields(ConceptMini refset, ReleaseManifestFile refsetFile, List<String> fieldNameList) {
		if (refsetFile.getField() == null) {
			for (String fieldName : fieldNameList) {
				refsetFile.addField(fieldName);
			}
		}
		refsetFile.addRefset(refset.getConceptId(), refset.getPt().getTerm());
	}

	private ReleaseManifestFile getRefsetFile(String effectiveTime, String exportName, String languageCode, String fieldTypes, String formattedName, ReleaseManifestFolder outputFolder) {
		String refsetFileName = getRefsetFilename(exportName, languageCode, fieldTypes, formattedName, effectiveTime);
		return outputFolder.getOrAddFile(refsetFileName);
	}

	private static ReleaseManifestFolder getRefsetOutputFolder(String exportDir, ReleaseManifestFolder snapshotFolder, ReleaseManifestFolder refsetFolder) {
		ReleaseManifestFolder outputFolder = exportDir.startsWith("/") ? snapshotFolder : refsetFolder;
		String[] split = exportDir.split("/");
		for (String folderName : split) {
			if (!folderName.isEmpty()) {
				outputFolder = outputFolder.getOrAddFolder(folderName);
			}
		}
		return outputFolder;
	}

	private void cloneSnapshotToFull(ReleaseManifestFolder source, ReleaseManifestFolder target) {
		for (ReleaseManifestFile file : orEmpty(source.getFile())) {
			String name = file.getName().replace("_Snapshot", "_Full").replace("Snapshot_", "Full_").replace("Snapshot-", "Full-");
			target.addFile(file.copy(name));
		}
		for (ReleaseManifestFolder folder : orEmpty(source.getFolder())) {
			cloneSnapshotToFull(folder, target.getOrAddFolder(folder.getName()));
		}
	}

	private String getCoreComponentFilename(String exportName, String langPostfix, String formattedName, String effectiveTime) {
		return format("sct2_%s_%s%s_%s_%s.txt", exportName, "Snapshot", langPostfix, formattedName, effectiveTime);
	}

	private String getRefsetFilename(String exportName, String languageCode, String fieldTypes, String formattedName, String effectiveTime) {
		String prefix = "der2";
		if (exportName.equals("OWLExpression")) {
			prefix = "sct2";
		}
		return format("%s_%sRefset_%s%s%s_%s_%s.txt", prefix, fieldTypes, exportName, "Snapshot", languageCode, formattedName, effectiveTime);
	}

}
