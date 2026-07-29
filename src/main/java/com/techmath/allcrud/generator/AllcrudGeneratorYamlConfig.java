package com.techmath.allcrud.generator;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
// (onRegenerate only ever nests under "pojo" - enforced by whitelisting allowed keys per node
// rather than looking for that one bad case specifically; a resource's "generate" list
// REPLACES the default entirely rather than merging; every layer name must be one of the 5
// known ones) need precise, project-specific error messages pointing at the exact yml path
// that's wrong - a generic data-binding failure wouldn't give a caller that.
//
// Expected shape:
//
//   pojoNamingStyle: VO   # or DTO
//   packages:
//     pojo: com.acme.dto
//     repository: com.acme.persistence
//     converter: com.acme.persistence
//     service: com.acme.service
//     controller: com.acme.web
//   defaults:
//     generate: [pojo, repository, converter, service, controller]
//     pojo:
//       onRegenerate: preserve   # or overwrite; default preserve if omitted
//   resources:
//     Order:
//       generate: [pojo, controller]
//     Product:
//       pojo:
//         onRegenerate: overwrite
//   routing:
//     basePathPrefix: /v1   # optional, default "" (no opinion, e.g. no forced "/api")
//
// resources.<name>.basePath (a sibling of generate/pojo, not shown above) is a FINAL absolute
// @RequestMapping path for that resource - see ResourceOverride.
public final class AllcrudGeneratorYamlConfig {

    private static final Set<String> ROOT_ALLOWED_KEYS =
            Set.of("pojoNamingStyle", "packages", "defaults", "resources", "routing");
    private static final Set<String> DEFAULTS_ALLOWED_KEYS = Set.of("generate", "pojo");
    private static final Set<String> RESOURCE_ALLOWED_KEYS = Set.of("generate", "pojo", "basePath");
    private static final Set<String> POJO_NODE_ALLOWED_KEYS = Set.of("onRegenerate");
    private static final Set<String> ROUTING_ALLOWED_KEYS = Set.of("basePathPrefix");

    private final PojoNamingStyle pojoNamingStyle;
    private final Map<GeneratedLayer, String> packages;
    private final Set<GeneratedLayer> defaultLayersToGenerate;
    private final OnRegenerate defaultPojoOnRegenerate;
    private final Map<String, ResourceOverride> resourceOverrides;
    private final String basePathPrefix;

    private AllcrudGeneratorYamlConfig(
            PojoNamingStyle pojoNamingStyle,
            Map<GeneratedLayer, String> packages,
            Set<GeneratedLayer> defaultLayersToGenerate,
            OnRegenerate defaultPojoOnRegenerate,
            Map<String, ResourceOverride> resourceOverrides,
            String basePathPrefix
    ) {
        this.pojoNamingStyle = pojoNamingStyle;
        this.packages = packages;
        this.defaultLayersToGenerate = defaultLayersToGenerate;
        this.defaultPojoOnRegenerate = defaultPojoOnRegenerate;
        this.resourceOverrides = resourceOverrides;
        this.basePathPrefix = basePathPrefix;
    }

    public static AllcrudGeneratorYamlConfig load(Path ymlPath) {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(ymlPath)) {
            root = new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (root == null) {
            throw new IllegalArgumentException("Empty or invalid YAML at " + ymlPath);
        }
        return parse(root, ymlPath);
    }

    public GenerationRequest toGenerationRequest(Path specPath, Path sourceRoot) {
        return new GenerationRequest(
                specPath, sourceRoot, pojoNamingStyle, defaultLayersToGenerate, packages,
                defaultPojoOnRegenerate, resourceOverrides, basePathPrefix);
    }

    private static AllcrudGeneratorYamlConfig parse(Map<String, Object> root, Path ymlPath) {
        requireOnlyKeys(root, ROOT_ALLOWED_KEYS, "<root>", ymlPath);

        PojoNamingStyle pojoNamingStyle = parsePojoNamingStyle(root, ymlPath);
        Map<GeneratedLayer, String> packages = parsePackages(root, ymlPath);

        Map<String, Object> defaultsNode = requireMap(root.get("defaults"), "defaults", ymlPath);
        requireOnlyKeys(defaultsNode, DEFAULTS_ALLOWED_KEYS, "defaults", ymlPath);
        Set<GeneratedLayer> defaultLayersToGenerate =
                parseGenerateList(requireNonNull(defaultsNode.get("generate"), "defaults.generate", ymlPath),
                        "defaults.generate", ymlPath);
        validateLayerDependencies(defaultLayersToGenerate, "defaults.generate", ymlPath);
        OnRegenerate defaultPojoOnRegenerate = parsePojoOnRegenerate(defaultsNode.get("pojo"), "defaults.pojo", ymlPath);

        Map<String, ResourceOverride> resourceOverrides = new LinkedHashMap<>();
        Object resourcesNode = root.get("resources");
        if (resourcesNode != null) {
            Map<String, Object> resources = requireMap(resourcesNode, "resources", ymlPath);
            for (Map.Entry<String, Object> entry : resources.entrySet()) {
                String resourceName = entry.getKey();
                String location = "resources." + resourceName;
                Map<String, Object> resourceNode = requireMap(entry.getValue(), location, ymlPath);
                requireOnlyKeys(resourceNode, RESOURCE_ALLOWED_KEYS, location, ymlPath);

                Set<GeneratedLayer> generate = resourceNode.containsKey("generate")
                        ? parseGenerateList(resourceNode.get("generate"), location + ".generate", ymlPath)
                        : null;
                if (generate != null) {
                    validateLayerDependencies(generate, location + ".generate", ymlPath);
                }
                OnRegenerate pojoOnRegenerate = resourceNode.containsKey("pojo")
                        ? parsePojoOnRegenerate(resourceNode.get("pojo"), location + ".pojo", ymlPath)
                        : null;
                String basePath = resourceNode.containsKey("basePath")
                        ? requireString(resourceNode.get("basePath"), location + ".basePath", ymlPath)
                        : null;

                resourceOverrides.put(resourceName, new ResourceOverride(generate, pojoOnRegenerate, basePath));
            }
        }

        String basePathPrefix = parseRoutingBasePathPrefix(root, ymlPath);

        return new AllcrudGeneratorYamlConfig(
                pojoNamingStyle, packages, defaultLayersToGenerate, defaultPojoOnRegenerate,
                Map.copyOf(resourceOverrides), basePathPrefix);
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

    private static Map<GeneratedLayer, String> parsePackages(Map<String, Object> root, Path ymlPath) {
        Map<String, Object> packagesNode = requireMap(requireNonNull(root.get("packages"), "packages", ymlPath),
                "packages", ymlPath);

        Map<GeneratedLayer, String> packages = new LinkedHashMap<>();
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            String key = layer.name().toLowerCase(Locale.ROOT);
            Object value = packagesNode.get(key);
            if (value == null) {
                throw configError("packages", ymlPath, "missing required entry \"" + key + "\"");
            }
            packages.put(layer, requireString(value, "packages." + key, ymlPath));
        }

        Set<String> unknownKeys = new LinkedHashSet<>(packagesNode.keySet());
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            unknownKeys.remove(layer.name().toLowerCase(Locale.ROOT));
        }
        if (!unknownKeys.isEmpty()) {
            throw configError("packages", ymlPath, "unknown key(s): " + unknownKeys);
        }

        return Map.copyOf(packages);
    }

    private static Set<GeneratedLayer> parseGenerateList(Object raw, String location, Path ymlPath) {
        if (!(raw instanceof List<?> list)) {
            throw configError(location, ymlPath, "must be a list, found: " + raw);
        }
        Set<GeneratedLayer> layers = new LinkedHashSet<>();
        for (Object item : list) {
            String name = requireString(item, location, ymlPath);
            layers.add(parseLayerName(name, location, ymlPath));
        }
        return layers;
    }

    // The 5 layers aren't actually independent: apiController.mustache hardcodes a
    // constructor dependency on the generated Service and Converter classes, and
    // service.mustache hardcodes one on Repository - CONTROLLER can never compile without
    // SERVICE+CONVERTER, and SERVICE can never compile without REPOSITORY. Caught this for
    // real via a "generate: [pojo, controller]" test fixture that failed compileJava, not a
    // hypothetical. POJO has no such coupling. Validated eagerly here (at yml load time, one
    // clear message) rather than left to surface as a confusing downstream javac error.
    private static void validateLayerDependencies(Set<GeneratedLayer> layers, String location, Path ymlPath) {
        if (layers.contains(GeneratedLayer.CONTROLLER)) {
            List<GeneratedLayer> missing = new java.util.ArrayList<>();
            if (!layers.contains(GeneratedLayer.SERVICE)) {
                missing.add(GeneratedLayer.SERVICE);
            }
            if (!layers.contains(GeneratedLayer.CONVERTER)) {
                missing.add(GeneratedLayer.CONVERTER);
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
    }

    private static String joinLayerNames(List<GeneratedLayer> layers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layers.size(); i++) {
            if (i > 0) {
                sb.append(" and ");
            }
            sb.append(layers.get(i).name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static GeneratedLayer parseLayerName(String name, String location, Path ymlPath) {
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            if (layer.name().equalsIgnoreCase(name)) {
                return layer;
            }
        }
        throw configError(location, ymlPath, "unknown layer \"" + name + "\" - expected one of "
                + "pojo, repository, converter, service, controller");
    }

    // Absent "pojo" node -> PRESERVE default. Present but without "onRegenerate" -> also
    // PRESERVE (an empty/malformed pojo node isn't an error by itself, only unknown keys are -
    // see requireOnlyKeys below).
    private static OnRegenerate parsePojoOnRegenerate(Object pojoNode, String location, Path ymlPath) {
        if (pojoNode == null) {
            return OnRegenerate.PRESERVE;
        }
        Map<String, Object> node = requireMap(pojoNode, location, ymlPath);
        requireOnlyKeys(node, POJO_NODE_ALLOWED_KEYS, location, ymlPath);
        Object raw = node.get("onRegenerate");
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
    // keys (e.g. "onRegenerate" nested directly under "defaults" instead of "defaults.pojo") -
    // this single check catches that case AND typos AND any other unsupported key uniformly,
    // with one clear error message naming the exact offending key(s) and location.
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
