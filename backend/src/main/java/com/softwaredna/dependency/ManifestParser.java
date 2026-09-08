package com.softwaredna.dependency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwaredna.common.domain.DependencyEcosystem;
import com.softwaredna.dependency.model.ResolvedDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads dependency manifests.
 *
 * <p>Only direct dependencies are reported, because only direct dependencies
 * are declared. Transitive trees require resolving against a registry or a
 * lock file's full graph; claiming a transitive count without doing that work
 * would be a fabricated number.
 *
 * <p>The XML parser is configured to reject DTDs and external entities. A
 * {@code pom.xml} is untrusted input, and an XXE payload in one would
 * otherwise let a repository read files from the analysis host.
 */
@Component
public class ManifestParser {

    private static final Logger log = LoggerFactory.getLogger(ManifestParser.class);

    private final ObjectMapper json = new ObjectMapper();

    /** What a manifest yielded, plus how much of it could be read. */
    public record ManifestResult(
            List<ResolvedDependency> dependencies,
            Map<String, String> declaredVersions,
            List<String> manifestsFound,
            List<String> manifestsUnreadable
    ) {
        static ManifestResult empty() {
            return new ManifestResult(List.of(), Map.of(), List.of(), List.of());
        }
    }

    /**
     * @param manifests repository-relative path to file contents
     */
    public ManifestResult parseAll(Map<String, String> manifests) {
        List<ResolvedDependency> dependencies = new ArrayList<>();
        Map<String, String> versions = new LinkedHashMap<>();
        List<String> found = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();

        manifests.forEach((path, content) -> {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            try {
                List<ResolvedDependency> parsed = switch (fileName) {
                    case "package.json" -> parsePackageJson(content, path, versions);
                    case "pom.xml" -> parsePom(content, path, versions);
                    case "build.gradle", "build.gradle.kts" ->
                            parseGradle(content, path, versions);
                    default -> List.of();
                };
                if (!parsed.isEmpty()) {
                    found.add(path);
                    dependencies.addAll(parsed);
                }
            } catch (Exception e) {
                // A malformed manifest is a fact about the repository, not a
                // reason to fail the analysis.
                log.warn("Could not parse manifest {}: {}", path, e.getMessage());
                unreadable.add(path);
            }
        });

        return new ManifestResult(List.copyOf(dependencies), Map.copyOf(versions),
                List.copyOf(found), List.copyOf(unreadable));
    }

    // ---- npm --------------------------------------------------------------

    private List<ResolvedDependency> parsePackageJson(String content, String path,
                                                      Map<String, String> versions)
            throws IOException {
        JsonNode root = json.readTree(content);
        List<ResolvedDependency> dependencies = new ArrayList<>();

        readNpmSection(root, "dependencies", "runtime", path, dependencies, versions);
        readNpmSection(root, "devDependencies", "development", path, dependencies, versions);
        readNpmSection(root, "peerDependencies", "peer", path, dependencies, versions);
        readNpmSection(root, "optionalDependencies", "optional", path, dependencies, versions);

        return dependencies;
    }

    private void readNpmSection(JsonNode root, String section, String scope, String path,
                                List<ResolvedDependency> into, Map<String, String> versions) {
        JsonNode node = root.path(section);
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String declared = entry.getValue().asText("");
            into.add(ResolvedDependency.declared(entry.getKey(), DependencyEcosystem.NPM,
                    normaliseNpmVersion(declared), true, scope, path));
            versions.putIfAbsent(entry.getKey(), declared);
        });
    }

    /** Strips range operators so a version can be compared, keeping the text. */
    private String normaliseNpmVersion(String declared) {
        if (declared == null || declared.isBlank()) {
            return "unspecified";
        }
        String trimmed = declared.trim();
        // Ranges, git URLs and file references are not comparable versions.
        if (trimmed.startsWith("git") || trimmed.startsWith("file:")
                || trimmed.startsWith("http") || trimmed.contains("||")) {
            return trimmed;
        }
        return trimmed.replaceAll("^[~^>=<\\s]+", "");
    }

    // ---- Maven ------------------------------------------------------------

    private List<ResolvedDependency> parsePom(String content, String path,
                                              Map<String, String> versions)
            throws ParserConfigurationException, SAXException, IOException {
        Document document = secureDocumentBuilder().parse(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        document.getDocumentElement().normalize();

        Map<String, String> properties = readPomProperties(document);
        List<ResolvedDependency> dependencies = new ArrayList<>();

        NodeList nodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String groupId = childText(element, "groupId");
            String artifactId = childText(element, "artifactId");
            if (groupId == null || artifactId == null) {
                continue;
            }
            String version = resolveProperty(childText(element, "version"), properties);
            String scope = childText(element, "scope");

            String name = groupId + ":" + artifactId;
            dependencies.add(ResolvedDependency.declared(name, DependencyEcosystem.MAVEN,
                    version == null ? "managed" : version, true,
                    scope == null ? "compile" : scope, path));
            versions.putIfAbsent(name, version == null ? "managed" : version);
        }
        return dependencies;
    }

    private Map<String, String> readPomProperties(Document document) {
        Map<String, String> properties = new HashMap<>();
        NodeList propertyBlocks = document.getElementsByTagName("properties");
        for (int i = 0; i < propertyBlocks.getLength(); i++) {
            NodeList children = propertyBlocks.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    properties.put(child.getNodeName(), child.getTextContent().trim());
                }
            }
        }
        return properties;
    }

    /** Expands a single {@code ${property}} reference; nested expansion is not attempted. */
    private String resolveProperty(String value, Map<String, String> properties) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String key = value.substring(2, value.length() - 1);
        return properties.getOrDefault(key, value);
    }

    /**
     * A parser hardened against XXE. The repository supplies this XML, so
     * external entity resolution would be a file-read primitive.
     */
    private DocumentBuilder secureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder();
    }

    private String childText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    // ---- Gradle -----------------------------------------------------------

    /**
     * Reads Gradle dependency declarations lexically.
     *
     * <p>A build script is a program, and evaluating one is out of the
     * question: it would mean executing code from an untrusted repository.
     * This reads the common single-line string notation only, and therefore
     * under-reports scripts that build coordinates dynamically. Under-reporting
     * is the correct failure mode here.
     */
    private List<ResolvedDependency> parseGradle(String content, String path,
                                                 Map<String, String> versions) {
        List<ResolvedDependency> dependencies = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile(
                        "(?m)^\\s*(implementation|api|compileOnly|runtimeOnly|testImplementation"
                                + "|testRuntimeOnly|annotationProcessor|kapt)"
                                + "\\s*\\(?\\s*['\"]([^'\"]+)['\"]")
                .matcher(content);

        while (matcher.find()) {
            String scope = matcher.group(1);
            String[] parts = matcher.group(2).split(":");
            if (parts.length < 2) {
                continue;
            }
            String name = parts[0] + ":" + parts[1];
            String version = parts.length >= 3 ? parts[2] : "managed";
            dependencies.add(ResolvedDependency.declared(name, DependencyEcosystem.MAVEN,
                    version, true, scope, path));
            versions.putIfAbsent(name, version);
        }
        return dependencies;
    }
}
