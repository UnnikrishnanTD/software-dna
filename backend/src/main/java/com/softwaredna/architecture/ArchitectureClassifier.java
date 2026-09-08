package com.softwaredna.architecture;

import com.softwaredna.codebase.model.ScannedFile;
import com.softwaredna.common.domain.ArchitectureLayer;
import com.softwaredna.common.domain.ArchitectureNodeKind;

import java.util.Locale;

/**
 * Assigns a file to an architectural layer and a node kind.
 *
 * <p>These are conventions, not facts, and the classifier says so: a project
 * that does not follow any recognised convention lands in {@link
 * ArchitectureLayer#DOMAIN} with kind {@link ArchitectureNodeKind#MODULE},
 * which is the neutral answer rather than a confident wrong one.
 *
 * <p>Directory role is checked before file-name suffix, because a repository's
 * folder structure expresses intent more reliably than any single class name.
 */
final class ArchitectureClassifier {

    private ArchitectureClassifier() {
    }

    /** Directory names that signal each layer, most specific first. */
    private static final String[][] LAYER_MARKERS = {
            {"presentation", "ui", "views", "view", "pages", "page", "screens", "screen",
                    "components", "component", "widgets", "web", "frontend", "webapp",
                    "controllers", "controller", "routes", "handlers", "resources", "rest",
                    "api", "graphql", "templates"},
            {"application", "app", "usecase", "usecases", "use-cases", "services", "service",
                    "facade", "facades", "state", "store", "stores", "workflow", "orchestration",
                    "commands", "queries"},
            {"domain", "model", "models", "entity", "entities", "core", "business",
                    "aggregate", "aggregates", "value", "policy"},
            {"infrastructure", "infra", "persistence", "repository", "repositories", "dao",
                    "adapters", "adapter", "gateway", "gateways", "client", "clients",
                    "config", "configuration", "db", "database", "migration", "migrations",
                    "platform", "integration", "messaging", "cache"},
    };

    private static final ArchitectureLayer[] LAYER_ORDER = {
            ArchitectureLayer.PRESENTATION,
            ArchitectureLayer.APPLICATION,
            ArchitectureLayer.DOMAIN,
            ArchitectureLayer.INFRASTRUCTURE,
    };

    /**
     * Layer for a module, decided from its path.
     *
     * <p>Later path segments win: {@code app/orders/infrastructure} is
     * infrastructure, not application, because the deeper segment is the more
     * specific statement of intent.
     */
    static ArchitectureLayer layerFor(String modulePath) {
        String[] segments = modulePath.toLowerCase(Locale.ROOT).split("/");

        ArchitectureLayer decided = null;
        for (String segment : segments) {
            for (int layerIndex = 0; layerIndex < LAYER_MARKERS.length; layerIndex++) {
                for (String marker : LAYER_MARKERS[layerIndex]) {
                    if (segment.equals(marker)) {
                        decided = LAYER_ORDER[layerIndex];
                    }
                }
            }
        }
        return decided == null ? ArchitectureLayer.DOMAIN : decided;
    }

    /**
     * Node kind for a module, decided from the files it contains and its name.
     *
     * <p>The dominant suffix among a module's files is a better signal than the
     * module name alone: a folder called {@code orders} holding
     * {@code OrderController} and {@code OrderResource} is an API surface
     * whatever it is named.
     */
    static ArchitectureNodeKind kindFor(String modulePath, Iterable<ScannedFile> files) {
        int api = 0;
        int service = 0;
        int component = 0;
        int store = 0;
        int datastore = 0;

        for (ScannedFile file : files) {
            String name = file.name().toLowerCase(Locale.ROOT);
            if (name.contains("controller") || name.contains("resource")
                    || name.contains("endpoint") || name.contains("router")
                    || name.contains(".routes.")) {
                api++;
            } else if (name.contains("component") || name.endsWith(".tsx")
                    || name.endsWith(".vue") || name.contains("page")
                    || name.contains("screen") || name.contains("view")) {
                component++;
            } else if (name.contains("store") || name.contains("reducer")
                    || name.contains("slice") || name.contains("state")) {
                store++;
            } else if (name.contains("repository") || name.contains("dao")
                    || name.contains("entity") || name.contains("mapper")
                    || name.endsWith(".sql")) {
                datastore++;
            } else if (name.contains("service") || name.contains("facade")
                    || name.contains("usecase") || name.contains("handler")
                    || name.contains("manager")) {
                service++;
            }
        }

        int best = Math.max(api, Math.max(service, Math.max(component,
                Math.max(store, datastore))));

        if (best == 0) {
            return moduleKindFromPath(modulePath);
        }
        if (best == api) return ArchitectureNodeKind.API;
        if (best == component) return ArchitectureNodeKind.COMPONENT;
        if (best == store) return ArchitectureNodeKind.STORE;
        if (best == datastore) return ArchitectureNodeKind.DATASTORE;
        return ArchitectureNodeKind.SERVICE;
    }

    private static ArchitectureNodeKind moduleKindFromPath(String modulePath) {
        String lower = modulePath.toLowerCase(Locale.ROOT);
        if (lower.isEmpty() || lower.equals("src") || lower.equals("app")) {
            return ArchitectureNodeKind.APP;
        }
        return ArchitectureNodeKind.MODULE;
    }

    /**
     * A readable name for a module path: the last meaningful segment, in
     * PascalCase, so {@code src/app/features/order-history} reads as
     * {@code OrderHistory}.
     */
    static String displayName(String modulePath) {
        if (modulePath.isEmpty()) {
            return "Root";
        }
        String[] segments = modulePath.split("/");
        String last = segments[segments.length - 1];

        StringBuilder name = new StringBuilder();
        for (String word : last.split("[-_.]")) {
            if (word.isEmpty()) continue;
            name.append(Character.toUpperCase(word.charAt(0)));
            name.append(word.substring(1));
        }
        return name.isEmpty() ? last : name.toString();
    }
}
