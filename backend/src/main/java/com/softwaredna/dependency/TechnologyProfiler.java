package com.softwaredna.dependency;

import com.softwaredna.codebase.model.Language;
import com.softwaredna.common.domain.DependencyEcosystem;
import com.softwaredna.common.domain.TechnologyCategory;
import com.softwaredna.dependency.model.ResolvedDependency;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Describes what a repository is built from.
 *
 * <p>Languages come from measured lines of code. Frameworks come from declared
 * dependencies, recognised by name against a small table of well-known
 * packages — a framework is only reported when the repository actually depends
 * on it, never inferred from a file extension.
 *
 * <p>Share is the language's percentage of measured source lines, so the
 * figures always sum to the code that was actually analysed.
 */
@Component
public class TechnologyProfiler {

    public record Technology(
            String name,
            TechnologyCategory category,
            double share,
            int linesOfCode,
            String version,
            int dependencyCount
    ) {
    }

    /** Package name to the framework it identifies. */
    private static final Map<String, FrameworkSpec> FRAMEWORKS = Map.ofEntries(
            Map.entry("@angular/core", new FrameworkSpec("Angular", TechnologyCategory.FRONTEND)),
            Map.entry("react", new FrameworkSpec("React", TechnologyCategory.FRONTEND)),
            Map.entry("vue", new FrameworkSpec("Vue", TechnologyCategory.FRONTEND)),
            Map.entry("svelte", new FrameworkSpec("Svelte", TechnologyCategory.FRONTEND)),
            Map.entry("next", new FrameworkSpec("Next.js", TechnologyCategory.FRONTEND)),
            Map.entry("rxjs", new FrameworkSpec("RxJS", TechnologyCategory.FRONTEND)),
            Map.entry("express", new FrameworkSpec("Express", TechnologyCategory.BACKEND)),
            Map.entry("nestjs", new FrameworkSpec("NestJS", TechnologyCategory.BACKEND)),
            Map.entry("@nestjs/core", new FrameworkSpec("NestJS", TechnologyCategory.BACKEND)),
            Map.entry("org.springframework.boot:spring-boot-starter-web",
                    new FrameworkSpec("Spring Boot", TechnologyCategory.BACKEND)),
            Map.entry("org.springframework.boot:spring-boot-starter",
                    new FrameworkSpec("Spring Boot", TechnologyCategory.BACKEND)),
            Map.entry("org.springframework.boot:spring-boot-starter-data-jpa",
                    new FrameworkSpec("Spring Data JPA", TechnologyCategory.BACKEND)),
            Map.entry("org.hibernate:hibernate-core",
                    new FrameworkSpec("Hibernate", TechnologyCategory.BACKEND)),
            Map.entry("org.postgresql:postgresql",
                    new FrameworkSpec("PostgreSQL", TechnologyCategory.DATASTORE)),
            Map.entry("mysql:mysql-connector-java",
                    new FrameworkSpec("MySQL", TechnologyCategory.DATASTORE)),
            Map.entry("mongodb", new FrameworkSpec("MongoDB", TechnologyCategory.DATASTORE)),
            Map.entry("mongoose", new FrameworkSpec("MongoDB", TechnologyCategory.DATASTORE)),
            Map.entry("redis", new FrameworkSpec("Redis", TechnologyCategory.DATASTORE)),
            Map.entry("ioredis", new FrameworkSpec("Redis", TechnologyCategory.DATASTORE)),
            Map.entry("io.lettuce:lettuce-core",
                    new FrameworkSpec("Redis", TechnologyCategory.DATASTORE)),
            Map.entry("jest", new FrameworkSpec("Jest", TechnologyCategory.TOOLING)),
            Map.entry("vitest", new FrameworkSpec("Vitest", TechnologyCategory.TOOLING)),
            Map.entry("karma", new FrameworkSpec("Karma", TechnologyCategory.TOOLING)),
            Map.entry("@playwright/test",
                    new FrameworkSpec("Playwright", TechnologyCategory.TOOLING)),
            Map.entry("cypress", new FrameworkSpec("Cypress", TechnologyCategory.TOOLING)),
            Map.entry("org.junit.jupiter:junit-jupiter",
                    new FrameworkSpec("JUnit", TechnologyCategory.TOOLING)),
            Map.entry("typescript", new FrameworkSpec("TypeScript", TechnologyCategory.LANGUAGE)));

    private record FrameworkSpec(String displayName, TechnologyCategory category) {
    }

    public List<Technology> profile(Map<Language, Integer> linesByLanguage,
                                    List<ResolvedDependency> dependencies,
                                    boolean hasDockerfile) {
        int totalLines = linesByLanguage.values().stream().mapToInt(Integer::intValue).sum();
        List<Technology> technologies = new ArrayList<>();

        // --- Languages, from measured lines ---
        linesByLanguage.forEach((language, lines) -> {
            if (lines <= 0) {
                return;
            }
            technologies.add(new Technology(
                    language.displayName(),
                    TechnologyCategory.LANGUAGE,
                    totalLines == 0 ? 0 : round((lines * 100.0) / totalLines),
                    lines,
                    null,
                    0));
        });

        // --- Frameworks, from declared dependencies ---
        Map<String, List<ResolvedDependency>> byFramework = new java.util.LinkedHashMap<>();
        for (ResolvedDependency dependency : dependencies) {
            FrameworkSpec spec = FRAMEWORKS.get(dependency.name().toLowerCase(Locale.ROOT));
            if (spec == null) {
                spec = FRAMEWORKS.get(dependency.name());
            }
            if (spec != null) {
                byFramework.computeIfAbsent(spec.displayName(), key -> new ArrayList<>())
                        .add(dependency);
            }
        }

        byFramework.forEach((name, matched) -> {
            FrameworkSpec spec = FRAMEWORKS.values().stream()
                    .filter(candidate -> candidate.displayName().equals(name))
                    .findFirst().orElse(null);
            if (spec == null) {
                return;
            }
            // A framework has no lines of its own; share stays with the
            // languages so the percentages remain a real composition.
            technologies.add(new Technology(name, spec.category(), 0, 0,
                    matched.get(0).version(), matched.size()));
        });

        if (hasDockerfile) {
            technologies.add(new Technology("Docker", TechnologyCategory.INFRASTRUCTURE,
                    0, 0, null, 0));
        }

        long mavenCount = dependencies.stream()
                .filter(dependency -> dependency.ecosystem() == DependencyEcosystem.MAVEN)
                .count();
        if (mavenCount > 0 && technologies.stream()
                .noneMatch(technology -> technology.name().equals("Maven"))) {
            technologies.add(new Technology("Maven", TechnologyCategory.TOOLING,
                    0, 0, null, (int) mavenCount));
        }

        technologies.sort(Comparator.comparingDouble(Technology::share).reversed()
                .thenComparing(Technology::name));
        return List.copyOf(technologies);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
