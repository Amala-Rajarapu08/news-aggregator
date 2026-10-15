package com.pulseai.ingestion;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
public class RssFeedParser {

    public List<ProviderArticle> parse(String xml) throws ParserConfigurationException, SAXException, java.io.IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element channel = firstElement(document, "channel");
        Element feed = channel == null ? document.getDocumentElement() : channel;
        String sourceName = childText(feed, "title");
        String sourceHomeUrl = childLink(feed);
        String language = childText(feed, "language", "lang");
        String feedCategory = childText(feed, "category");

        List<ProviderArticle> parsed = new ArrayList<>();
        NodeList allElements = document.getElementsByTagName("*");
        for (int index = 0; index < allElements.getLength(); index++) {
            Node node = allElements.item(index);
            if (!(node instanceof Element entry) || !isEntry(entry)) {
                continue;
            }
            String articleUrl = childLink(entry);
            parsed.add(new ProviderArticle(
                    childText(entry, "guid", "id"),
                    childText(entry, "title"),
                    childText(entry, "description", "summary", "encoded", "content"),
                    articleUrl,
                    childImage(entry, articleUrl),
                    childText(entry, "creator", "author"),
                    childText(entry, "pubDate", "published", "updated", "date"),
                    sourceName,
                    sourceHomeUrl,
                    firstNonBlank(childText(entry, "category"), feedCategory),
                    language));
        }
        return parsed;
    }

    private Element firstElement(Document document, String name) {
        NodeList elements = document.getElementsByTagNameNS("*", name);
        if (elements.getLength() == 0) {
            elements = document.getElementsByTagName(name);
        }
        return elements.getLength() == 0 ? null : (Element) elements.item(0);
    }

    private boolean isEntry(Element element) {
        String localName = localName(element);
        return "item".equalsIgnoreCase(localName) || "entry".equalsIgnoreCase(localName);
    }

    private String childText(Element parent, String... names) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && matches(localName(element), names)) {
                String text = element.getTextContent();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String childLink(Element parent) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "link".equalsIgnoreCase(localName(element))) {
                String href = element.getAttribute("href");
                String rel = element.getAttribute("rel");
                if (!href.isBlank() && (rel.isBlank() || "alternate".equalsIgnoreCase(rel))) {
                    return href.trim();
                }
                String text = element.getTextContent();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String childImage(Element parent, String articleUrl) {
        List<Element> candidates = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                candidates.add(element);
            }
        }
        NodeList descendants = parent.getElementsByTagName("*");
        for (int index = 0; index < descendants.getLength(); index++) {
            if (descendants.item(index) instanceof Element element && !candidates.contains(element)) {
                candidates.add(element);
            }
        }
        for (Element element : candidates) {
            String name = localName(element).toLowerCase(java.util.Locale.ROOT);
            String rawUrl = firstNonBlank(element.getAttribute("url"), element.getAttribute("href"));
            if (name.equals("image") && (rawUrl == null || rawUrl.isBlank())) {
                rawUrl = childText(element, "url");
            }
            if (rawUrl != null && isImageResource(element, name, rawUrl)) {
                String resolved = resolveHttpUrl(rawUrl, articleUrl);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        // Some publisher feeds supply their image inside the RSS HTML excerpt.
        // Extract only the URL; never render the markup or fetch the article body.
        for (String field : List.of("encoded", "content", "description", "summary")) {
            String html = childText(parent, field);
            if (html != null) {
                var matcher = java.util.regex.Pattern
                        .compile("(?is)<img\\b[^>]*?\\b(?:src|data-src)\\s*=\\s*['\"]([^'\"]+)['\"]")
                        .matcher(html);
                if (matcher.find()) {
                    return resolveHttpUrl(matcher.group(1), articleUrl);
                }
            }
        }
        return null;
    }

    private boolean isImageResource(Element element, String name, String url) {
        if (name.equals("thumbnail")) {
            return true;
        }
        if (name.equals("image")) {
            return true;
        }
        if (name.equals("link") && !"enclosure".equalsIgnoreCase(element.getAttribute("rel"))) {
            return false;
        }
        if (!name.equals("content") && !name.equals("enclosure") && !name.equals("link")) {
            return false;
        }
        String mediaType = element.getAttribute("type").toLowerCase(java.util.Locale.ROOT);
        String medium = element.getAttribute("medium").toLowerCase(java.util.Locale.ROOT);
        return mediaType.startsWith("image/") || medium.equals("image") || hasImageExtension(url);
    }

    private boolean hasImageExtension(String value) {
        try {
            String path = URI.create(value).getPath();
            if (path == null) {
                return false;
            }
            String lower = path.toLowerCase(java.util.Locale.ROOT);
            return List.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".bmp", ".svg")
                    .stream().anyMatch(lower::endsWith);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String resolveHttpUrl(String rawUrl, String articleUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI image = URI.create(org.springframework.web.util.HtmlUtils.htmlUnescape(rawUrl.trim()));
            if (!image.isAbsolute() && articleUrl != null) {
                image = URI.create(articleUrl).resolve(image);
            }
            String scheme = image.getScheme();
            if ((scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                    || image.getHost() == null
                    || image.getUserInfo() != null) {
                return null;
            }
            return image.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean matches(String value, String... names) {
        for (String name : names) {
            if (name.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String localName(Element element) {
        String localName = element.getLocalName();
        if (localName != null) {
            return localName;
        }
        String name = element.getTagName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
