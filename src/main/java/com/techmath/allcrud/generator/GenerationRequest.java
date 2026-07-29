package com.techmath.allcrud.generator;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// sourceRoot: the Java source root (e.g. a project's src/main/java) that packages resolve
// under - NOT an openapi-generator output directory. Was named "outputDir" before the
// idempotent-scaffolding rework: AllcrudGenerator now always runs the actual codegen into an
// ephemeral staging directory first, then relocates each file under sourceRoot according to
// its configured target package (see "packages" below) - callers never see the staging dir.
//
// defaultLayersToGenerate / defaultPojoOnRegenerate: global fallback values (allcrud-
// generator.yml's "defaults" block) - AllcrudGenerator resolves the actual per-file decision
// per-resource via resourceOverrides, falling back to these when a resource has no override
// (or the override leaves a field null). Replaces the old flat "generateServiceLayer"
// boolean/"pojoOnRegenerate" single value from before allcrud-generator.yml's per-resource
// "resources.<name>" exceptions existed - same duplicated-granularity problem already fixed
// once for allcrudIdType, now fixed here by making the request itself resource-aware instead
// of applying one flat decision to every resource in the spec.
//
// packages: target package per GeneratedLayer, global (not per-resource - allcrud-generator.yml
// only supports one package per layer for the whole project, regardless of per-resource
// overrides elsewhere). Every layer any resource could resolve to generating must have an
// entry - AllcrudGenerator fails fast on a requested layer with no configured package, rather
// than silently placing it somewhere unexpected.
//
// resourceOverrides: keyed by resource name (e.g. "Product", matching allcrudEntityName) -
// see ResourceOverride. A resource absent from this map uses the defaults entirely.
public record GenerationRequest(
        Path specPath,
        Path sourceRoot,
        PojoNamingStyle pojoNamingStyle,
        Set<GeneratedLayer> defaultLayersToGenerate,
        Map<GeneratedLayer, String> packages,
        OnRegenerate defaultPojoOnRegenerate,
        Map<String, ResourceOverride> resourceOverrides
) {
    public GenerationRequest {
        Objects.requireNonNull(specPath, "specPath");
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(pojoNamingStyle, "pojoNamingStyle");
        Objects.requireNonNull(defaultLayersToGenerate, "defaultLayersToGenerate");
        Objects.requireNonNull(packages, "packages");
        Objects.requireNonNull(defaultPojoOnRegenerate, "defaultPojoOnRegenerate");
        Objects.requireNonNull(resourceOverrides, "resourceOverrides");
        defaultLayersToGenerate = Set.copyOf(defaultLayersToGenerate);
        packages = Map.copyOf(packages);
        resourceOverrides = Map.copyOf(resourceOverrides);
    }
}
