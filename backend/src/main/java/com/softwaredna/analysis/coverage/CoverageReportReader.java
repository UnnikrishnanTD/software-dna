package com.softwaredna.analysis.coverage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a coverage report if the repository committed one.
 *
 * <p>This exists because coverage cannot otherwise be known. Measuring it
 * requires executing the test suite, which this service will never do with
 * code from an arbitrary repository. A committed report is the only honest
 * source, and when there is none the analysis says so rather than estimating.
 *
 * <p>Supports LCOV ({@code lcov.info}, produced by Jest, Karma, nyc and most
 * JavaScript tooling) and JaCoCo XML. Both are searched at their conventional
 * locations only; walking the whole tree for them is not worth the IO.
 */
@Component
public class CoverageReportReader {

    private static final Logger log = LoggerFactory.getLogger(CoverageReportReader.class);

    /** Per-file line coverage, plus which tool reported it. */
    public record CoverageReport(Map<String, Double> byPath, String source) {
        public static CoverageReport none() {
            return new CoverageReport(Map.of(), null);
        }

        public boolean isPresent() {
            return source != null && !byPath.isEmpty();
        }
    }

    private static final List<String> LCOV_LOCATIONS = List.of(
            "coverage/lcov.info", "lcov.info", "coverage/lcov/lcov.info",
            "target/coverage/lcov.info");

    private static final List<String> JACOCO_LOCATIONS = List.of(
            "target/site/jacoco/jacoco.xml", "build/reports/jacoco/test/jacocoTestReport.xml",
            "target/jacoco.xml", "jacoco.xml");

    public CoverageReport read(Path checkout) {
        return findLcov(checkout)
                .or(() -> findJacoco(checkout))
                .orElseGet(CoverageReport::none);
    }

    private Optional<CoverageReport> findLcov(Path checkout) {
        for (String location : LCOV_LOCATIONS) {
            Path path = checkout.resolve(location);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                Map<String, Double> coverage = parseLcov(
                        Files.readString(path, StandardCharsets.UTF_8), checkout);
                if (!coverage.isEmpty()) {
                    log.info("Read LCOV coverage for {} files from {}",
                            coverage.size(), location);
                    return Optional.of(new CoverageReport(coverage, "LCOV"));
                }
            } catch (IOException e) {
                log.warn("Could not read {}: {}", location, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * LCOV is a line-oriented format: {@code SF:} opens a file record,
     * {@code LF:} and {@code LH:} give lines found and lines hit.
     */
    private Map<String, Double> parseLcov(String content, Path checkout) {
        Map<String, Double> coverage = new HashMap<>();
        String currentFile = null;
        int found = 0;
        int hit = 0;

        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("SF:")) {
                currentFile = trimmed.substring(3).trim();
                found = 0;
                hit = 0;
            } else if (trimmed.startsWith("LF:")) {
                found = parseInt(trimmed.substring(3));
            } else if (trimmed.startsWith("LH:")) {
                hit = parseInt(trimmed.substring(3));
            } else if (trimmed.equals("end_of_record") && currentFile != null) {
                if (found > 0) {
                    coverage.put(relativise(currentFile, checkout), (hit * 100.0) / found);
                }
                currentFile = null;
            }
        }
        return coverage;
    }

    private Optional<CoverageReport> findJacoco(Path checkout) {
        for (String location : JACOCO_LOCATIONS) {
            Path path = checkout.resolve(location);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                Map<String, Double> coverage = parseJacoco(path);
                if (!coverage.isEmpty()) {
                    log.info("Read JaCoCo coverage for {} files from {}",
                            coverage.size(), location);
                    return Optional.of(new CoverageReport(coverage, "JaCoCo"));
                }
            } catch (Exception e) {
                log.warn("Could not read {}: {}", location, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private Map<String, Double> parseJacoco(Path path) throws Exception {
        // The report comes from the repository, so the parser is hardened the
        // same way the manifest parser is.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        var document = factory.newDocumentBuilder().parse(path.toFile());
        var packages = document.getElementsByTagName("package");
        Map<String, Double> coverage = new HashMap<>();

        for (int p = 0; p < packages.getLength(); p++) {
            if (!(packages.item(p) instanceof org.w3c.dom.Element packageElement)) {
                continue;
            }
            String packagePath = packageElement.getAttribute("name");
            var sourceFiles = packageElement.getElementsByTagName("sourcefile");

            for (int f = 0; f < sourceFiles.getLength(); f++) {
                if (!(sourceFiles.item(f) instanceof org.w3c.dom.Element fileElement)) {
                    continue;
                }
                var counters = fileElement.getElementsByTagName("counter");
                for (int c = 0; c < counters.getLength(); c++) {
                    if (!(counters.item(c) instanceof org.w3c.dom.Element counter)) {
                        continue;
                    }
                    if (!"LINE".equals(counter.getAttribute("type"))) {
                        continue;
                    }
                    int missed = parseInt(counter.getAttribute("missed"));
                    int covered = parseInt(counter.getAttribute("covered"));
                    if (missed + covered > 0) {
                        // JaCoCo reports the package path; reconstruct the
                        // conventional Maven source layout.
                        String filePath = "src/main/java/" + packagePath + "/"
                                + fileElement.getAttribute("name");
                        coverage.put(filePath, (covered * 100.0) / (missed + covered));
                    }
                }
            }
        }
        return coverage;
    }

    /** LCOV records absolute paths; the analysis works in repository-relative ones. */
    private String relativise(String reported, Path checkout) {
        String normalised = reported.replace('\\', '/');
        String root = checkout.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (normalised.startsWith(root)) {
            normalised = normalised.substring(root.length());
        }
        while (normalised.startsWith("/") || normalised.startsWith("./")) {
            normalised = normalised.startsWith("/")
                    ? normalised.substring(1) : normalised.substring(2);
        }
        return normalised;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
