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
// See docs/adr/0012-manual-yaml-parsing-no-databinding.md and
// docs/adr/0009-yml-v2-breaking-format-change.md
//
// Expected shape:
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

    // See docs/notes/AllcrudGeneratorYamlConfig.md#yaml-key-name-constants--deduplication-was-verified-not-assumed-s1192
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PACKAGE = "package";
    private static final String KEY_ON_REGENERATE = "onRegenerate";
    private static final String KEY_POJO_NAMING_STYLE = "pojoNamingStyle";
    private static final String KEY_ROUTING = "routing";
    private static final String KEY_GENERATION = "generation";
    private static final String KEY_RESOURCES = "resources";
    private static final String KEY_EXCEPTION_HANDLER = "exceptionHandler";
    private static final String KEY_BASE_PATH = "basePath";

    private static final Set<String> ROOT_ALLOWED_KEYS =
            Set.of(KEY_POJO_NAMING_STYLE, KEY_ROUTING, KEY_EXCEPTION_HANDLER, KEY_GENERATION, KEY_RESOURCES);

    private static final Set<String> GENERATION_ALLOWED_KEYS = layerKeys();
    private static final Set<String> RESOURCE_ALLOWED_KEYS = withBasePath(layerKeys());

    // "package" is deliberately absent here (unlike POJO_LAYER_KEYS/PRODUCTION_LAYER_KEYS
    // below) - see the class-level comment's generation.unitTest/integrationTest entries for
    // why a GLOBAL fixed package for these two layers would be wrong.
    private static final Set<String> GLOBAL_TEST_LAYER_ALLOWED_KEYS = Set.of(KEY_ENABLED);
    // Per-resource, unitTest/integrationTest DO accept "package" - a pointwise override, not a
    // global default, same mechanism as every other layer's per-resource package override.
    private static final Set<String> RESOURCE_TEST_LAYER_ALLOWED_KEYS = Set.of(KEY_ENABLED, KEY_PACKAGE);
    private static final Set<String> PRODUCTION_LAYER_ALLOWED_KEYS = Set.of(KEY_ENABLED, KEY_PACKAGE);
    private static final Set<String> POJO_LAYER_ALLOWED_KEYS = Set.of(KEY_ENABLED, KEY_PACKAGE, KEY_ON_REGENERATE);

    private static final Set<String> ROUTING_ALLOWED_KEYS = Set.of("basePathPrefix");
    private static final Set<String> EXCEPTION_HANDLER_ALLOWED_KEYS = Set.of(KEY_ENABLED, KEY_PACKAGE, "className");
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
        Object resourcesNode = root.get(KEY_RESOURCES);
        if (resourcesNode != null) {
            Map<String, Object> resources = requireMap(resourcesNode, KEY_RESOURCES, ymlPath);
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
                requireMap(requireNonNull(root.get(KEY_GENERATION), KEY_GENERATION, ymlPath), KEY_GENERATION, ymlPath);
        requireOnlyKeys(generationNode, GENERATION_ALLOWED_KEYS, KEY_GENERATION, ymlPath);

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
            if (layerNode.containsKey(KEY_ENABLED)) {
                enabled = requireBoolean(layerNode.get(KEY_ENABLED), location + ".enabled", ymlPath);
            }
            enabledByLayer.put(layer, enabled);

            if (layerNode.containsKey(KEY_PACKAGE)) {
                packages.put(layer, requireString(layerNode.get(KEY_PACKAGE), location + ".package", ymlPath));
            }

            if (layer == GeneratedLayer.POJO) {
                defaultPojoOnRegenerate = parseOnRegenerateValue(layerNode.get(KEY_ON_REGENERATE), location, ymlPath);
            }
        }

        validateGenerationLayerPackages(enabledByLayer, packages, ymlPath);

        Set<GeneratedLayer> enabledLayers = resolveEnabledLayers(enabledByLayer);
        validateLayerDependencies(enabledLayers, KEY_GENERATION, ymlPath);

        return new GenerationSection(Set.copyOf(enabledLayers), Map.copyOf(packages), defaultPojoOnRegenerate);
    }

    private static void validateGenerationLayerPackages(
            Map<GeneratedLayer, Boolean> enabledByLayer, Map<GeneratedLayer, String> packages, Path ymlPath) {
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            if (isTestLayer(layer)) {
                continue;
            }
            if (enabledByLayer.get(layer).booleanValue() && !packages.containsKey(layer)) {
                throw configError("generation." + layer.yamlKey(), ymlPath,
                        "is enabled but has no \"package\" configured");
            }
        }
    }

    // Shared by parseGeneration and parseResource - both reduce a Map<GeneratedLayer, Boolean>
    // down to the set of layers whose value is true, identical logic either way.
    private static Set<GeneratedLayer> resolveEnabledLayers(Map<GeneratedLayer, Boolean> enabledByLayer) {
        Set<GeneratedLayer> enabledLayers = new LinkedHashSet<>();
        for (Map.Entry<GeneratedLayer, Boolean> entry : enabledByLayer.entrySet()) {
            if (entry.getValue().booleanValue()) {
                enabledLayers.add(entry.getKey());
            }
        }
        return enabledLayers;
    }

    // See docs/adr/0004-layer-dependency-chain.md - cascades each of the 7 layers' "enabled"
    // independently (resource's own value if present, else generation.<layer>'s resolved default).
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

                if (layerNode.containsKey(KEY_ENABLED)) {
                    enabled = requireBoolean(layerNode.get(KEY_ENABLED), layerLocation + ".enabled", ymlPath);
                }
                if (layerNode.containsKey(KEY_PACKAGE)) {
                    packageOverrides.put(layer, requireString(layerNode.get(KEY_PACKAGE), layerLocation + ".package", ymlPath));
                }
                if (layer == GeneratedLayer.POJO && layerNode.containsKey(KEY_ON_REGENERATE)) {
                    pojoOnRegenerate = parseOnRegenerateValue(layerNode.get(KEY_ON_REGENERATE), layerLocation, ymlPath);
                }
            }

            resolvedEnabled.put(layer, enabled);

            requireResourceLayerHasPackage(layer, layerLocation, enabled, packageOverrides, generation, ymlPath);
        }

        Set<GeneratedLayer> generate = resolveEnabledLayers(resolvedEnabled);
        validateLayerDependencies(generate, location, ymlPath);

        String basePath = resourceNode.containsKey(KEY_BASE_PATH)
                ? requireString(resourceNode.get(KEY_BASE_PATH), location + ".basePath", ymlPath)
                : null;

        return new ResourceOverride(Set.copyOf(generate), pojoOnRegenerate, basePath, Map.copyOf(packageOverrides));
    }

    // See docs/notes/AllcrudGeneratorYamlConfig.md#requireresourcelayerhaspackage--why-unittestintegrationtest-are-exempt
    private static void requireResourceLayerHasPackage(
            GeneratedLayer layer, String layerLocation, boolean enabled,
            Map<GeneratedLayer, String> packageOverrides, GenerationSection generation, Path ymlPath) {
        if (enabled && !isTestLayer(layer)
                && !packageOverrides.containsKey(layer) && !generation.packages().containsKey(layer)) {
            throw configError(layerLocation, ymlPath,
                    "is enabled for this resource but no \"package\" is configured for it, "
                            + "here or in generation." + layer.yamlKey());
        }
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
        withBasePath.add(KEY_BASE_PATH);
        return withBasePath;
    }

    // See docs/adr/0007-exceptionhandler-opt-out-default.md. Package falls back to
    // generation.controller.package, className defaults to "GlobalExceptionHandler" - fails
    // loudly if neither an explicit "exceptionHandler.package" nor that fallback resolves,
    // instead of a silent null flowing into AllcrudGenerator.
    private static ExceptionHandlerConfig parseExceptionHandlerConfig(
            Map<String, Object> root, Map<GeneratedLayer, String> packages, Path ymlPath) {
        boolean enabled = true;
        String targetPackage = null;
        String className = DEFAULT_EXCEPTION_HANDLER_CLASS_NAME;

        Object node = root.get(KEY_EXCEPTION_HANDLER);
        if (node != null) {
            Map<String, Object> exceptionHandlerNode = requireMap(node, KEY_EXCEPTION_HANDLER, ymlPath);
            requireOnlyKeys(exceptionHandlerNode, EXCEPTION_HANDLER_ALLOWED_KEYS, KEY_EXCEPTION_HANDLER, ymlPath);

            if (exceptionHandlerNode.containsKey(KEY_ENABLED)) {
                enabled = requireBoolean(exceptionHandlerNode.get(KEY_ENABLED), "exceptionHandler.enabled", ymlPath);
            }

            Object packageRaw = exceptionHandlerNode.get(KEY_PACKAGE);
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
            throw configError(KEY_EXCEPTION_HANDLER, ymlPath,
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
        Object routingNode = root.get(KEY_ROUTING);
        if (routingNode == null) {
            return "";
        }
        Map<String, Object> routing = requireMap(routingNode, KEY_ROUTING, ymlPath);
        requireOnlyKeys(routing, ROUTING_ALLOWED_KEYS, KEY_ROUTING, ymlPath);
        Object raw = routing.get("basePathPrefix");
        return raw == null ? "" : requireString(raw, "routing.basePathPrefix", ymlPath);
    }

    private static PojoNamingStyle parsePojoNamingStyle(Map<String, Object> root, Path ymlPath) {
        Object raw = requireNonNull(root.get(KEY_POJO_NAMING_STYLE), KEY_POJO_NAMING_STYLE, ymlPath);
        String value = requireString(raw, KEY_POJO_NAMING_STYLE, ymlPath);
        try {
            return PojoNamingStyle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw configError(KEY_POJO_NAMING_STYLE, ymlPath, "must be VO or DTO, found: " + value);
        }
    }

    // See docs/adr/0004-layer-dependency-chain.md.
    // 5 independent, sequential checks (each reads only the immutable "layers" set, no shared
    // mutable state between them) - split out of what used to be one large method to bring its
    // Cognitive Complexity back under the project's limit. Order preserved exactly: first
    // violated check still wins, same as before.
    private static void validateLayerDependencies(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        requireConverterHasPojo(layers, location, ymlPath);
        requireControllerHasDependencies(layers, location, ymlPath);
        requireServiceHasRepository(layers, location, ymlPath);
        requireIntegrationTestHasController(layers, location, ymlPath);
        requireUnitTestHasService(layers, location, ymlPath);
    }

    private static void requireConverterHasPojo(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        if (layers.contains(GeneratedLayer.CONVERTER) && !layers.contains(GeneratedLayer.POJO)) {
            throw configError(location, ymlPath,
                    "CONVERTER requires POJO to also be generated (missing: pojo for this resource)");
        }
    }

    private static void requireControllerHasDependencies(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        if (!layers.contains(GeneratedLayer.CONTROLLER)) {
            return;
        }
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

    private static void requireServiceHasRepository(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        if (layers.contains(GeneratedLayer.SERVICE) && !layers.contains(GeneratedLayer.REPOSITORY)) {
            throw configError(location, ymlPath,
                    "SERVICE requires REPOSITORY to also be generated (missing: repository for this resource)");
        }
    }

    // See docs/adr/0004-layer-dependency-chain.md - no separate POJO check here; CONTROLLER's
    // own check above already guarantees it transitively.
    private static void requireIntegrationTestHasController(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        if (layers.contains(GeneratedLayer.INTEGRATION_TEST) && !layers.contains(GeneratedLayer.CONTROLLER)) {
            throw configError(location, ymlPath,
                    "INTEGRATION_TEST requires CONTROLLER to also be generated (missing: controller for this resource)");
        }
    }

    private static void requireUnitTestHasService(Set<GeneratedLayer> layers, String location, Path ymlPath) {
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

    // See docs/notes/AllcrudGeneratorYamlConfig.md#parseonregeneratevalue--only-ever-called-for-the-pojo-layer-node
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

    // See docs/notes/AllcrudGeneratorYamlConfig.md#requireonlykeys--whitelist-not-a-search-for-specific-misplaced-keys
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
