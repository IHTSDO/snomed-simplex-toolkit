package org.snomed.simplex.service;

import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.snomed.simplex.client.domain.CodeSystem;
import org.snomed.simplex.client.domain.CodeSystemVersion;
import org.snomed.simplex.client.mlds.MldsAtomFeedService;
import org.snomed.simplex.client.mlds.MldsClient;
import org.snomed.simplex.client.mlds.domain.*;
import org.snomed.simplex.config.VersionedPackagesResourceManagerConfiguration;
import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class MldsReleaseService {

	private static final DateTimeFormatter PUBLISHED_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

	private final MldsClient mldsClient;
	private final MldsAtomFeedService mldsAtomFeedService;
	private final ResourceManager versionedPackagesResourceManager;
	private final String downloadUrlTemplate;

	public MldsReleaseService(
			MldsClient mldsClient,
			MldsAtomFeedService mldsAtomFeedService,
			VersionedPackagesResourceManagerConfiguration resourceManagerConfiguration,
			ResourceLoader resourceLoader,
			@Value("${mlds.download-url-template}") String downloadUrlTemplate) {

		this.mldsClient = mldsClient;
		this.mldsAtomFeedService = mldsAtomFeedService;
		this.versionedPackagesResourceManager = new ResourceManager(resourceManagerConfiguration, resourceLoader);
		this.downloadUrlTemplate = downloadUrlTemplate;
	}

	public MldsReleaseResult addReleaseToMlds(CodeSystem codeSystem, CodeSystemVersion version) throws ServiceExceptionWithStatusCode {
		String defaultModule = codeSystem.getDefaultModule();
		if (defaultModule == null || defaultModule.isBlank()) {
			throw new ServiceExceptionWithStatusCode("Edition has no default module configured.", HttpStatus.CONFLICT);
		}

		String packageFilename = version.releasePackage();
		if (packageFilename == null || packageFilename.isBlank()) {
			throw new ServiceExceptionWithStatusCode("Target CodeSystem version has no package set.", HttpStatus.CONFLICT);
		}

		String effectiveTime = version.effectiveDate().toString();
		String contentItemIdentifier = contentItemIdentifier(defaultModule);
		String versionUri = versionUri(defaultModule, effectiveTime);

		long releasePackageId = mldsAtomFeedService.findReleasePackageId(contentItemIdentifier)
				.orElseThrow(() -> new ServiceExceptionWithStatusCode(
						"No MLDS release package found for module " + defaultModule + ".", HttpStatus.NOT_FOUND));

		assertVersionUriNotDuplicate(releasePackageId, versionUri);

		String monthYearLabel = formatMonthYear(version.effectiveDate());
		String editionName = codeSystem.getName();
		String publishedAt = formatPublishedDate(version.effectiveDate());

		MldsReleaseVersionRequest versionRequest = new MldsReleaseVersionRequest(
				monthYearLabel + " v1.0",
				"This is the PRODUCTION Release of the " + monthYearLabel + " SNOMED CT " + editionName + ".",
				monthYearLabel + " SNOMED CT " + editionName + " release",
				"offline",
				"SCT_RF2_ALL",
				versionUri,
				"",
				"",
				publishedAt
		);

		PackageDigest packageDigest = computePackageDigest(packageFilename);
		String downloadUrl = applyTemplate(downloadUrlTemplate, defaultModule, effectiveTime, packageFilename);

		MldsReleaseVersionResponse createdVersion = mldsClient.createReleaseVersion(releasePackageId, versionRequest);
		if (createdVersion == null || createdVersion.releaseVersionId() == null) {
			throw new ServiceExceptionWithStatusCode("MLDS did not return a release version identifier.", HttpStatus.BAD_GATEWAY);
		}

		MldsReleaseFileRequest fileRequest = new MldsReleaseFileRequest(
				packageFilename,
				downloadUrl,
				packageDigest.md5Hash(),
				packageDigest.fileSize(),
				true
		);

		MldsReleaseFileResponse createdFile = mldsClient.createReleaseFile(
				releasePackageId,
				createdVersion.releaseVersionId(),
				fileRequest);
		if (createdFile == null || createdFile.releaseFileId() == null) {
			throw new ServiceExceptionWithStatusCode("MLDS did not return a release file identifier.", HttpStatus.BAD_GATEWAY);
		}

		return new MldsReleaseResult(
				releasePackageId,
				createdVersion.releaseVersionId(),
				createdFile.releaseFileId(),
				versionUri
		);
	}

	static String formatMonthYear(int effectiveDate) {
		LocalDate date = parseEffectiveDate(effectiveDate);
		String month = date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		return month + " " + date.getYear();
	}

	static String formatPublishedDate(int effectiveDate) {
		return parseEffectiveDate(effectiveDate).format(PUBLISHED_DATE_FORMAT);
	}

	static LocalDate parseEffectiveDate(int effectiveDate) {
		String value = Integer.toString(effectiveDate);
		return LocalDate.of(
				Integer.parseInt(value.substring(0, 4)),
				Integer.parseInt(value.substring(4, 6)),
				Integer.parseInt(value.substring(6, 8)));
	}

	static String contentItemIdentifier(String moduleId) {
		return "http://snomed.info/sct/" + moduleId;
	}

	static String versionUri(String moduleId, String effectiveTime) {
		return "http://snomed.info/sct/" + moduleId + "/version/" + effectiveTime;
	}

	static String applyTemplate(String template, String moduleId, String effectiveTime, String packageName) {
		return template
				.replace("{moduleId}", moduleId)
				.replace("{effectiveTime}", effectiveTime)
				.replace("{packageName}", packageName);
	}

	static boolean hasDuplicateVersionUri(List<MldsReleaseVersionResponse> releaseVersions, String versionUri) {
		if (releaseVersions == null) {
			return false;
		}
		return releaseVersions.stream()
				.map(MldsReleaseVersionResponse::versionURI)
				.filter(Objects::nonNull)
				.anyMatch(versionUri::equals);
	}

	private void assertVersionUriNotDuplicate(long releasePackageId, String versionUri) throws ServiceExceptionWithStatusCode {
		MldsReleasePackageResponse releasePackage = mldsClient.getReleasePackage(releasePackageId);
		if (hasDuplicateVersionUri(releasePackage.releaseVersions(), versionUri)) {
			throw new ServiceExceptionWithStatusCode("Release version already exists in MLDS.", HttpStatus.CONFLICT);
		}
	}

	private PackageDigest computePackageDigest(String filename) throws ServiceExceptionWithStatusCode {
		try (InputStream inputStream = versionedPackagesResourceManager.readResourceStream(filename)) {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			long fileSize = 0;
			try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = digestInputStream.read(buffer)) != -1) {
					fileSize += read;
				}
			}
			String md5Hash = HexFormat.of().formatHex(digest.digest());
			return new PackageDigest(md5Hash, fileSize);
		} catch (FileNotFoundException e) {
			throw new ServiceExceptionWithStatusCode("Release package file not found.", HttpStatus.NOT_FOUND, e);
		} catch (Exception e) {
			throw new ServiceExceptionWithStatusCode("Failed to read release package for MLDS upload.", HttpStatus.INTERNAL_SERVER_ERROR, e);
		}
	}

	private record PackageDigest(String md5Hash, long fileSize) {
	}
}
