package com.techmath.allcrud.generator;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Parses allcrud-generator.yml into a GenerationRequest, combined with the specPath/sourceRoot
// the caller supplies separately (the yml doesn't carry those - they're wherever the caller/
// plugin decides, e.g. a Gradle task property).
//
// Manual SnakeYAML Map/List walking, not a data-binding library: the validation rules here
// (onRegenerate only ever nests under "pojo"; unitTest/integrationTest never take a global
// "package" - see below; every layer name must be one of the 7 known ones) need precise,
// project-specific error messages pointing at the exact yml path that's wrong - a generic
// data-binding failure wouldn't give a caller that.
//
// Expected shape (v2 - breaks compatibility with the old packages/defaults/"generate: [...]"
// format on purpose: no external users yet, and per-layer enabled:boolean at both the global
// and per-resource level is a cleaner model than a list that either replaces or doesn't):
//
//   pojoNamingStyle: VO   # or DTO
//   routing:
//     basePathPrefix: /v1   # optional, default "" (no opinion, e.g. no forced "/api")
//   exceptionHandler:
//     enabled: true            # optional, default true (opt-out, unlike every other artifact)
//     package: com.acme.web   # optional, falls back to generation.controller.package
//     className: GlobalExceptionHandler   # optional, this is the default if omitted
//   generation:
//     pojo:
//       enabled: true              # optional, default true
//       package: com.acme.dto      # required if this layer is enabled (globally)
//       onRegenerate: preserve     # optional, default preserve - ONLY pojo accepts this key
//     repository:
//       enabled: true              # optional, default true
//       package: com.acme.persistence
//     converter:
//       enabled: true              # optional, default true
//       package: com.acme.persistence
//     service:
//       enabled: true              # optional, default true
//       package: com.acme.service
//     controller:
//       enabled: true              # optional, default true
//       package: com.acme.web
//     unitTest:
//       enabled: false             # optional, default FALSE (unlike the 5 layers above)
//       # NO "package" key here - unitTest's package is always resolved dynamically, per
//       # resource, from THAT resource's own resolved service package (including any
//       # per-resource override it has) - see resources.<name>.unitTest.package below and
//       # AllcrudGenerator#resolveEffectivePackage. A fixed global value would be wrong the
//       # instant one resource overrides its service package and unitTest doesn't follow.
//     integrationTest:
//       enabled: false             # optional, default FALSE
//       # same story, falls back to THIS resource's own resolved controller package
//   resources:
//     # OPCIONAL - only exceptions to the global "generation" block above.
//     Order:
//       repository:
//         enabled: false   # per-resource override of just this layer's enabled flag
//       converter:
//         enabled: false
//       service:
//         enabled: false
//       basePath: /custom/orders
//     Product:
//       pojo:
//         package: com.acme.catalog.dto   # per-resource package override - works the same
//                                          # way on every one of the 7 layers, not just pojo
//         onRegenerate: overwrite
//       repository:
//         package: com.acme.catalog.persistence
//       unitTest:
//         enabled: true
//         package: com.acme.catalog.test.unit   # the one place unitTest/integrationTest DO
//                                                 # accept "package" - a pointwise override,
//                                                 # not a global default
//
// resources.<name>.basePath (a sibling of the 7 layer keys, not shown above under Order) is a
// FINAL absolute @RequestMapping path for that resource - see ResourceOverride.
public final class AllcrudGeneratorYamlConfig {

    private static final Set<String> ROOT_ALLOWED_KEYS =
            Set.of("pojoNamingStyle", "routing", "exceptionHandler", "generation", "resources");

    private static final Set<String> GENERATION_ALLOWED_KEYS = layerKeys();
    private static final Set<String> RESOURCE_ALLOWED_KEYS = withBasePath(layerKeys());

    // "package" is deliberately absent here (unlike POJO_LAYER_KEYS/PRODUCTION_LAYER_KEYS
    // below) - see the class-level comment's generation.unitTest/integrationTest entries for
    // why a GLOBAL fixed package for these two layers would be wrong.
    private static final Set<String> GLOBAL_TEST_LAYER_ALLOWED_KEYS = Set.of("enabled");
    // Per-resource, unitTest/integrationTest DO accept "package" - a pointwise override, not a
    // global default, same mechanism as every other layer's per-resource package override.
    private static final Set<String> RESOURCE_TEST_LAYER_ALLOWED_KEYS = Set.of("enabled", "package");
    private static final Set<String> PRODUCTION_LAYER_ALLOWED_KEYS = Set.of("enabled", "package");
    private static final Set<String> POJO_LAYER_ALLOWED_KEYS = Set.of("enabled", "package", "onRegenerate");

    private static final Set<String> ROUTING_ALLOWED_KEYS = Set.of("basePathPrefix");
    private static final Set<String> EXCEPTION_HANDLER_ALLOWED_KEYS = Set.of("enabled", "package", "className");
    private static final String DEFAULT_EXCEPTION_HANDLER_CLASS_NAME = "GlobalExceptionHandler";

    private final PojoNamingStyle pojoNamingStyle;
    private final Map<GeneratedLayer, String> packages;
    private final Set<GeneratedLayer> defaultLayersToGenerate;
    private final OnRegenerate defaultPojoOnRegenerate;
    private final Map<String, ResourceOverride> resourceOverrides;
    private final String basePathPrefix;
    private final ExceptionHandlerConfig exceptionHandler;

    private AllcrudGeneratorYamlConfig(
            PojoNamingStyle pojoNamingStyle,
            Map<GeneratedLayer, String> packages,
            Set<GeneratedLayer> defaultLayersToGenerate,
            OnRegenerate defaultPojoOnRegenerate,
            Map<String, ResourceOverride> resourceOverrides,
            String basePathPrefix,
            ExceptionHandlerConfig exceptionHandler
    ) {
        this.pojoNamingStyle = pojoNamingStyle;
        this.packages = packages;
        this.defaultLayersToGenerate = defaultLayersToGenerate;
        this.defaultPojoOnRegenerate = defaultPojoOnRegenerate;
        this.resourceOverrides = resourceOverrides;
        this.basePathPrefix = basePathPrefix;
        this.exceptionHandler = exceptionHandler;
    }

    public static AllcrudGeneratorYamlConfig load(Path ymlPath) {
        Map<String, Object> root = IoExceptions.readYaml(ymlPath);
        if (root == null) {
            throw new IllegalArgumentException("Empty or invalid YAML at " + ymlPath);
        }
        return parse(root, ymlPath);
    }

    public GenerationRequest toGenerationRequest(Path specPath, Path sourceRoot, Path testSourceRoot) {
        return new GenerationRequest(
                specPath, sourceRoot, testSourceRoot, pojoNamingStyle, defaultLayersToGenerate, packages,
                defaultPojoOnRegenerate, resourceOverrides, basePathPrefix, exceptionHandler);
    }

    private static AllcrudGeneratorYamlConfig parse(Map<String, Object> root, Path ymlPath) {
        requireOnlyKeys(root, ROOT_ALLOWED_KEYS, "<root>", ymlPath);

        PojoNamingStyle pojoNamingStyle = parsePojoNamingStyle(root, ymlPath);
        GenerationSection generation = parseGeneration(root, ymlPath);

        Map<String, ResourceOverride> resourceOverrides = new LinkedHashMap<>();
        Object resourcesNode = root.get("resources");
        if (resourcesNode != null) {
            Map<String, Object> resources = requireMap(resourcesNode, "resources", ymlPath);
            for (Map.Entry<String, Object> entry : resources.entrySet()) {
                String resourceName = entry.getKey();
                String location = "resources." + resourceName;
                Map<String, Object> resourceNode = requireMap(entry.getValue(), location, ymlPath);
                requireOnlyKeys(resourceNode, RESOURCE_ALLOWED_KEYS, location, ymlPath);

                resourceOverrides.put(resourceName, parseResource(resourceNode, location, generation, ymlPath));
            }
        }

        String basePathPrefix = parseRoutingBasePathPrefix(root, ymlPath);
        ExceptionHandlerConfig exceptionHandler = parseExceptionHandlerConfig(root, generation.packages(), ymlPath);

        return new AllcrudGeneratorYamlConfig(
                pojoNamingStyle, generation.packages(), generation.enabledLayers(), generation.defaultPojoOnRegenerate(),
                Map.copyOf(resourceOverrides), basePathPrefix, exceptionHandler);
    }

    // Holds the fully-resolved outcome of parsing "generation:" - the global enabled set (used
    // both as GenerationRequest#defaultLayersToGenerate and as the fallback whenever a resource
    // doesn't override a given layer's enabled flag), the global package map (5 production
    // layers only - see GLOBAL_TEST_LAYER_ALLOWED_KEYS above for why unitTest/integrationTest
    // never contribute one), and the global pojo.onRegenerate default.
    private record GenerationSection(
            Set<GeneratedLayer> enabledLayers,
            Map<GeneratedLayer, String> packages,
            OnRegenerate defaultPojoOnRegenerate
    ) {
    }

    private static GenerationSection parseGeneration(Map<String, Object> root, Path ymlPath) {
        Map<String, Object> generationNode =
                requireMap(requireNonNull(root.get("generation"), "generation", ymlPath), "generation", ymlPath);
        requireOnlyKeys(generationNode, GENERATION_ALLOWED_KEYS, "generation", ymlPath);

        Map<GeneratedLayer, Boolean> enabledByLayer = new LinkedHashMap<>();
        Map<GeneratedLayer, String> packages = new LinkedHashMap<>();
        OnRegenerate defaultPojoOnRegenerate = OnRegenerate.PRESERVE;

        for (GeneratedLayer layer : GeneratedLayer.values()) {
            String location = "generation." + layer.yamlKey();
            boolean defaultEnabled = !isTestLayer(layer);
            Object layerNodeRaw = generationNode.get(layer.yamlKey());

            if (layerNodeRaw == null) {
                enabledByLayer.put(layer, defaultEnabled);
                continue;
            }

            Map<String, Object> layerNode = requireMap(layerNodeRaw, location, ymlPath);
            requireOnlyKeys(layerNode, globalAllowedKeysFor(layer), location, ymlPath);

            boolean enabled = defaultEnabled;
            if (layerNode.containsKey("enabled")) {
                enabled = requireBoolean(layerNode.get("enabled"), location + ".enabled", ymlPath);
            }
            enabledByLayer.put(layer, enabled);

            if (layerNode.containsKey("package")) {
                packages.put(layer, requireString(layerNode.get("package"), location + ".package", ymlPath));
            }

            if (layer == GeneratedLayer.POJO) {
                defaultPojoOnRegenerate = parseOnRegenerateValue(layerNode.get("onRegenerate"), location, ymlPath);
            }
        }

        for (GeneratedLayer layer : GeneratedLayer.values()) {
            if (isTestLayer(layer)) {
                continue;
            }
            if (enabledByLayer.get(layer) && !packages.containsKey(layer)) {
                throw configError("generation." + layer.yamlKey(), ymlPath,
                        "is enabled but has no \"package\" configured");
            }
        }

        Set<GeneratedLayer> enabledLayers = new LinkedHashSet<>();
        for (Map.Entry<GeneratedLayer, Boolean> entry : enabledByLayer.entrySet()) {
            if (entry.getValue()) {
                enabledLayers.add(entry.getKey());
            }
        }
        validateLayerDependencies(enabledLayers, "generation", ymlPath);

        return new GenerationSection(Set.copyOf(enabledLayers), Map.copyOf(packages), defaultPojoOnRegenerate);
    }

    // resources.<name> - cascades each of the 7 layers' "enabled" independently (resource's own
    // value if present, else generation.<layer>'s resolved default) into a single, fully-
    // resolved Set<GeneratedLayer> for this resource (ResourceOverride#generate is never null
    // for a resource that appears under "resources:" - there's no more "generate: [...]
    // replaces the list" ambiguity to preserve now that every layer toggles independently).
    private static ResourceOverride parseResource(
            Map<String, Object> resourceNode, String location, GenerationSection generation, Path ymlPath) {
        Map<GeneratedLayer, Boolean> resolvedEnabled = new LinkedHashMap<>();
        Map<GeneratedLayer, String> packageOverrides = new LinkedHashMap<>();
        OnRegenerate pojoOnRegenerate = null;

        for (GeneratedLayer layer : GeneratedLayer.values()) {
            String layerLocation = location + "." + layer.yamlKey();
            Object layerNodeRaw = resourceNode.get(layer.yamlKey());
            boolean enabled = generation.enabledLayers().contains(layer);

            if (layerNodeRaw != null) {
                Map<String, Object> layerNode = requireMap(layerNodeRaw, layerLocation, ymlPath);
                requireOnlyKeys(layerNode, resourceAllowedKeysFor(layer), layerLocation, ymlPath);

                if (layerNode.containsKey("enabled")) {
                    enabled = requireBoolean(layerNode.get("enabled"), layerLocation + ".enabled", ymlPath);
                }
                if (layerNode.containsKey("package")) {
                    packageOverrides.put(layer, requireString(layerNode.get("package"), layerLocation + ".package", ymlPath));
                }
                if (layer == GeneratedLayer.POJO && layerNode.containsKey("onRegenerate")) {
                    pojoOnRegenerate = parseOnRegenerateValue(layerNode.get("onRegenerate"), layerLocation, ymlPath);
                }
            }

            resolvedEnabled.put(layer, enabled);

            // Eager, per-resource check: a production layer enabled for THIS resource (whether
            // by its own override or by inheriting the global default) needs a resolvable
            // package - either this resource's own override, or the global one. unitTest/
            // integrationTest are exempt: they always have a dynamic fallback available
            // (the sibling service/controller package), never a missing-package failure mode.
            if (enabled && !isTestLayer(layer)
                    && !packageOverrides.containsKey(layer) && !generation.packages().containsKey(layer)) {
                throw configError(layerLocation, ymlPath,
                        "is enabled for this resource but no \"package\" is configured for it, "
                                + "here or in generation." + layer.yamlKey());
            }
        }

        Set<GeneratedLayer> generate = new LinkedHashSet<>();
        for (Map.Entry<GeneratedLayer, Boolean> entry : resolvedEnabled.entrySet()) {
            if (entry.getValue()) {
                generate.add(entry.getKey());
            }
        }
        validateLayerDependencies(generate, location, ymlPath);

        String basePath = resourceNode.containsKey("basePath")
                ? requireString(resourceNode.get("basePath"), location + ".basePath", ymlPath)
                : null;

        return new ResourceOverride(Set.copyOf(generate), pojoOnRegenerate, basePath, Map.copyOf(packageOverrides));
    }

    private static Set<String> globalAllowedKeysFor(GeneratedLayer layer) {
        if (layer == GeneratedLayer.POJO) {
            return POJO_LAYER_ALLOWED_KEYS;
        }
        return isTestLayer(layer) ? GLOBAL_TEST_LAYER_ALLOWED_KEYS : PRODUCTION_LAYER_ALLOWED_KEYS;
    }

    private static Set<String> resourceAllowedKeysFor(GeneratedLayer layer) {
        if (layer == GeneratedLayer.POJO) {
            return POJO_LAYER_ALLOWED_KEYS;
        }
        return isTestLayer(layer) ? RESOURCE_TEST_LAYER_ALLOWED_KEYS : PRODUCTION_LAYER_ALLOWED_KEYS;
    }

    private static boolean isTestLayer(GeneratedLayer layer) {
        return layer == GeneratedLayer.UNIT_TEST || layer == GeneratedLayer.INTEGRATION_TEST;
    }

    private static Set<String> layerKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            keys.add(layer.yamlKey());
        }
        return keys;
    }

    private static Set<String> withBasePath(Set<String> keys) {
        Set<String> withBasePath = new LinkedHashSet<>(keys);
        withBasePath.add("basePath");
        return withBasePath;
    }

    // Absent "exceptionHandler" section entirely -> enabled:true (opt-out default, see
    // ExceptionHandlerConfig), package falls back to generation.controller.package, className
    // defaults to "GlobalExceptionHandler". generation.controller.package is only mandatory
    // when generation.controller.enabled is true (the default) - if a project has explicitly
    // disabled the Controller layer globally AND doesn't declare "exceptionHandler.package"
    // explicitly, this is what catches the resulting unresolvable package, loudly, instead of
    // a silent null flowing into AllcrudGenerator.
    private static ExceptionHandlerConfig parseExceptionHandlerConfig(
            Map<String, Object> root, Map<GeneratedLayer, String> packages, Path ymlPath) {
        boolean enabled = true;
        String targetPackage = null;
        String className = DEFAULT_EXCEPTION_HANDLER_CLASS_NAME;

        Object node = root.get("exceptionHandler");
        if (node != null) {
            Map<String, Object> exceptionHandlerNode = requireMap(node, "exceptionHandler", ymlPath);
            requireOnlyKeys(exceptionHandlerNode, EXCEPTION_HANDLER_ALLOWED_KEYS, "exceptionHandler", ymlPath);

            if (exceptionHandlerNode.containsKey("enabled")) {
                enabled = requireBoolean(exceptionHandlerNode.get("enabled"), "exceptionHandler.enabled", ymlPath);
            }

            Object packageRaw = exceptionHandlerNode.get("package");
            if (packageRaw != null) {
                targetPackage = requireString(packageRaw, "exceptionHandler.package", ymlPath);
            }

            Object classNameRaw = exceptionHandlerNode.get("className");
            if (classNameRaw != null) {
                className = requireString(classNameRaw, "exceptionHandler.className", ymlPath);
            }
        }

        if (targetPackage == null) {
            targetPackage = packages.get(GeneratedLayer.CONTROLLER);
        }

        if (enabled && targetPackage == null) {
            throw configError("exceptionHandler", ymlPath,
                    "enabled is true (the default) but no package could be resolved for it - "
                            + "either declare \"exceptionHandler.package\" explicitly, or set "
                            + "\"exceptionHandler.enabled: false\" if this project doesn't want a "
                            + "GlobalExceptionHandler generated");
        }

        return new ExceptionHandlerConfig(enabled, enabled ? targetPackage : null, className);
    }

    // Absent "routing" section entirely -> "" (no opinion, e.g. no forced "/api" prefix) -
    // most callers shouldn't have to think about this section at all.
    private static String parseRoutingBasePathPrefix(Map<String, Object> root, Path ymlPath) {
        Object routingNode = root.get("routing");
        if (routingNode == null) {
            return "";
        }
        Map<String, Object> routing = requireMap(routingNode, "routing", ymlPath);
        requireOnlyKeys(routing, ROUTING_ALLOWED_KEYS, "routing", ymlPath);
        Object raw = routing.get("basePathPrefix");
        return raw == null ? "" : requireString(raw, "routing.basePathPrefix", ymlPath);
    }

    private static PojoNamingStyle parsePojoNamingStyle(Map<String, Object> root, Path ymlPath) {
        Object raw = requireNonNull(root.get("pojoNamingStyle"), "pojoNamingStyle", ymlPath);
        String value = requireString(raw, "pojoNamingStyle", ymlPath);
        try {
            return PojoNamingStyle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw configError("pojoNamingStyle", ymlPath, "must be VO or DTO, found: " + value);
        }
    }

    // The layers aren't actually independent - confirmed by grepping every one of the 6
    // non-POJO templates for allcrudPojoClassName/allcrudPojoPackage (the POJO type/import),
    // not assumed:
    //   - converter.mustache: Converter<Entity, POJO, ID>, convertToVO()/convertToEntity()
    //     signatures - CONVERTER requires POJO directly.
    //   - apiController.mustache: CrudController<Entity, POJO, ID>, getConverter() return
    //     type - CONTROLLER requires POJO directly, not only transitively via CONVERTER.
    //   - integrationTest.mustache: imports POJO, super(POJO.class, ID.class, ...),
    //     getConverter() return type - INTEGRATION_TEST requires POJO directly too, but see
    //     the comment further below on why that isn't checked as a separate rule.
    //   - service.mustache/repository.mustache/unitTest.mustache: no POJO reference at all
    //     (grep came back empty) - SERVICE/REPOSITORY/UNIT_TEST have no POJO dependency.
    // Also confirmed (unchanged from before): apiController.mustache hardcodes a constructor
    // dependency on the generated Service and Converter classes, and service.mustache
    // hardcodes one on Repository - CONTROLLER can never compile without SERVICE+CONVERTER,
    // and SERVICE can never compile without REPOSITORY. integrationTest.mustache hardcodes a
    // constructor dependency on the generated Controller class (INTEGRATION_TEST requires
    // CONTROLLER, which itself cascades to SERVICE+CONVERTER+POJO), unitTest.mustache
    // hardcodes one on Service (UNIT_TEST requires SERVICE, which cascades to REPOSITORY).
    // Validated eagerly here (at yml load time, one clear message) rather than left to surface
    // as a confusing downstream javac error.
    private static void validateLayerDependencies(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        if (layers.contains(GeneratedLayer.CONVERTER) && !layers.contains(GeneratedLayer.POJO)) {
            throw configError(location, ymlPath,
                    "CONVERTER requires POJO to also be generated (missing: pojo for this resource)");
        }
        if (layers.contains(GeneratedLayer.CONTROLLER)) {
            List<GeneratedLayer> missing = new java.util.ArrayList<>();
            if (!layers.contains(GeneratedLayer.SERVICE)) {
                missing.add(GeneratedLayer.SERVICE);
            }
            if (!layers.contains(GeneratedLayer.CONVERTER)) {
                missing.add(GeneratedLayer.CONVERTER);
            }
            if (!layers.contains(GeneratedLayer.POJO)) {
                missing.add(GeneratedLayer.POJO);
            }
            if (!missing.isEmpty()) {
                throw configError(location, ymlPath,
                        "CONTROLLER requires " + joinLayerNames(missing) + " to also be generated (missing: "
                                + joinLayerNames(missing) + " for this resource)");
            }
        }
        if (layers.contains(GeneratedLayer.SERVICE) && !layers.contains(GeneratedLayer.REPOSITORY)) {
            throw configError(location, ymlPath,
                    "SERVICE requires REPOSITORY to also be generated (missing: repository for this resource)");
        }
        // No separate POJO check here despite integrationTest.mustache referencing POJO
        // directly: CONTROLLER's own check above already guarantees POJO is present whenever
        // CONTROLLER passes without throwing, and this block already requires CONTROLLER - so
        // by the time execution reaches here, POJO's presence is already proven, not just
        // assumed. A separate check would be dead code, never reachable.
        if (layers.contains(GeneratedLayer.INTEGRATION_TEST) && !layers.contains(GeneratedLayer.CONTROLLER)) {
            throw configError(location, ymlPath,
                    "INTEGRATION_TEST requires CONTROLLER to also be generated (missing: controller for this resource)");
        }
        if (layers.contains(GeneratedLayer.UNIT_TEST) && !layers.contains(GeneratedLayer.SERVICE)) {
            throw configError(location, ymlPath,
                    "UNIT_TEST requires SERVICE to also be generated (missing: service for this resource)");
        }
    }

    private static String joinLayerNames(List<GeneratedLayer> layers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layers.size(); i++) {
            if (i > 0) {
                sb.append(" and ");
            }
            sb.append(layers.get(i).yamlKey());
        }
        return sb.toString();
    }

    // Absent "onRegenerate" -> PRESERVE default. Only ever called for the pojo layer node
    // (global generation.pojo or a resource's pojo override) - POJO_LAYER_ALLOWED_KEYS is the
    // only allowed-keys set that includes "onRegenerate" at all, so a caller passing any other
    // layer's node here would already have failed requireOnlyKeys before reaching this method.
    private static OnRegenerate parseOnRegenerateValue(Object raw, String location, Path ymlPath) {
        if (raw == null) {
            return OnRegenerate.PRESERVE;
        }
        String value = requireString(raw, location + ".onRegenerate", ymlPath);
        try {
            return OnRegenerate.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw configError(location + ".onRegenerate", ymlPath, "must be preserve or overwrite, found: " + value);
        }
    }

    // Whitelists the keys allowed at a given node instead of hunting for specific misplaced
    // keys - this single check catches typos and any other unsupported key uniformly, with one
    // clear error message naming the exact offending key(s) and location.
    private static void requireOnlyKeys(Map<String, Object> node, Set<String> allowedKeys, String location, Path ymlPath) {
        Set<String> unknownKeys = new LinkedHashSet<>(node.keySet());
        unknownKeys.removeAll(allowedKeys);
        if (!unknownKeys.isEmpty()) {
            throw configError(location, ymlPath, "unknown key(s) " + unknownKeys + " - allowed: " + allowedKeys);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object raw, String location, Path ymlPath) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw configError(location, ymlPath, "must be a mapping, found: " + raw);
        }
        return (Map<String, Object>) map;
    }

    private static String requireString(Object raw, String location, Path ymlPath) {
        if (!(raw instanceof String value)) {
            throw configError(location, ymlPath, "must be a string, found: " + raw);
        }
        return value;
    }

    private static boolean requireBoolean(Object raw, String location, Path ymlPath) {
        if (!(raw instanceof Boolean value)) {
            throw configError(location, ymlPath, "must be a boolean, found: " + raw);
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String location, Path ymlPath) {
        if (value == null) {
            throw configError(location, ymlPath, "missing required key");
        }
        return value;
    }

    private static IllegalArgumentException configError(String location, Path ymlPath, String problem) {
        return new IllegalArgumentException(ymlPath + ": \"" + location + "\" " + problem);
    }

}
