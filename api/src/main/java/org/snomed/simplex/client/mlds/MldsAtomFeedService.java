package org.snomed.simplex.client.mlds;

import org.snomed.simplex.exceptions.ServiceExceptionWithStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MldsAtomFeedService {

	private static final Pattern RELEASE_PACKAGE_ID_PATTERN = Pattern.compile("/api/releasePackages/(\\d+)/");

	private final MldsClient mldsClient;

	public MldsAtomFeedService(MldsClient mldsClient) {
		this.mldsClient = mldsClient;
	}

	public Optional<Long> findReleasePackageId(String contentItemIdentifier) throws ServiceExceptionWithStatusCode {
		String feedXml = mldsClient.fetchFeed();
		if (feedXml == null || feedXml.isBlank()) {
			throw new ServiceExceptionWithStatusCode("MLDS syndication feed returned empty content.", HttpStatus.BAD_GATEWAY);
		}
		return parseReleasePackageId(feedXml, contentItemIdentifier);
	}

	Optional<Long> parseReleasePackageId(String feedXml, String contentItemIdentifier) throws ServiceExceptionWithStatusCode {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(feedXml)));
			NodeList entries = document.getElementsByTagNameNS("*", "entry");
			for (int i = 0; i < entries.getLength(); i++) {
				Node entryNode = entries.item(i);
				if (!(entryNode instanceof Element entry)) {
					continue;
				}
				String entryContentItemIdentifier = getChildElementText(entry, "contentItemIdentifier");
				if (!contentItemIdentifier.equals(entryContentItemIdentifier)) {
					continue;
				}
				Long releasePackageId = extractReleasePackageIdFromLinks(entry);
				if (releasePackageId != null) {
					return Optional.of(releasePackageId);
				}
			}
			return Optional.empty();
		} catch (Exception e) {
			throw new ServiceExceptionWithStatusCode("Failed to parse MLDS syndication feed.", HttpStatus.BAD_GATEWAY, e);
		}
	}

	private static String getChildElementText(Element parent, String localName) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element element && elementLocalNameMatches(element, localName)) {
				return element.getTextContent().trim();
			}
		}
		return null;
	}

	private static boolean elementLocalNameMatches(Element element, String localName) {
		String name = element.getLocalName();
		if (name == null || name.isEmpty()) {
			name = element.getNodeName();
			int colonIndex = name.indexOf(':');
			if (colonIndex >= 0) {
				name = name.substring(colonIndex + 1);
			}
		}
		return localName.equals(name);
	}

	private static Long extractReleasePackageIdFromLinks(Element entry) {
		NodeList links = entry.getElementsByTagNameNS("*", "link");
		for (int i = 0; i < links.getLength(); i++) {
			Node linkNode = links.item(i);
			if (!(linkNode instanceof Element link)) {
				continue;
			}
			String href = link.getAttribute("href");
			Matcher matcher = RELEASE_PACKAGE_ID_PATTERN.matcher(href);
			if (matcher.find()) {
				return Long.parseLong(matcher.group(1));
			}
		}
		return null;
	}
}
