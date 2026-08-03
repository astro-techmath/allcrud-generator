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
// Only the 5 production layers (POJO/REPOSITORY/CONVERTER/SERVICE/CONTROLLER) ever land here.
//
// testSourceRoot: the Java TEST source root (e.g. a project's src/test/java) - where
// UNIT_TEST/INTEGRATION_TEST land instead, always, no configuration knob anywhere (yml or
// plugin) to put them under sourceRoot or any other directory. A distinct root, not a
// "packages"-style resolvable location, because tests belong in a different Gradle/Maven
// source set entirely, not just a different package.
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
// overrides elsewhere). Only ever has entries for the 5 production layers - AllcrudGenerator
// fails fast if one of THOSE is requested with no configured package. UNIT_TEST/INTEGRATION_TEST
// deliberately have no entry here, ever: their default package is computed dynamically per
// resource from the sibling layer's OWN resolved package (SERVICE/CONTROLLER respectively),
// not a fixed global default - see AllcrudGenerator#resolveEffectivePackage.
//
// resourceOverrides: keyed by resource name (e.g. "Product", matching allcrudEntityName) -
// see ResourceOverride. A resource absent from this map uses the defaults entirely.
//
// basePathPrefix: allcrud-generator.yml's "routing.basePathPrefix" - global default, "" (no
// opinion, e.g. no forced "/api") if the caller doesn't set one. Each resource's
// @RequestMapping path is "{basePathPrefix}/{entityName, lowercased}" unless
// ResourceOverride#basePath gives it a final absolute path instead (see AllcrudSpringCodegen#
// resolveBasePath) - replaces the old Spring "${openapi.<title>.base-path:<default>}" property
// placeholder, which silently resolved to an empty path (mapping the whole controller to the
// app root) whenever nobody configured that property, with no compile-time signal anything
// was wrong.
//
// exceptionHandler: allcrud-generator.yml's "exceptionHandler" block (ExceptionHandlerConfig) -
// the one PROJECT-level (not per-resource) artifact this generator produces, see
// AllcrudGenerator#generateGlobalExceptionHandler.
public record GenerationRequest(
        Path specPath,
        Path sourceRoot,
        Path testSourceRoot,
        PojoNamingStyle pojoNamingStyle,
        Set<GeneratedLayer> defaultLayersToGenerate,
        Map<GeneratedLayer, String> packages,
        OnRegenerate defaultPojoOnRegenerate,
        Map<String, ResourceOverride> resourceOverrides,
        String basePathPrefix,
        ExceptionHandlerConfig exceptionHandler
) {
    public GenerationRequest {
        Objects.requireNonNull(specPath, "specPath");
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(testSourceRoot, "testSourceRoot");
        Objects.requireNonNull(pojoNamingStyle, "pojoNamingStyle");
        Objects.requireNonNull(defaultLayersToGenerate, "defaultLayersToGenerate");
        Objects.requireNonNull(packages, "packages");
        Objects.requireNonNull(defaultPojoOnRegenerate, "defaultPojoOnRegenerate");
        Objects.requireNonNull(resourceOverrides, "resourceOverrides");
        Objects.requireNonNull(basePathPrefix, "basePathPrefix");
        Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        defaultLayersToGenerate = Set.copyOf(defaultLayersToGenerate);
        packages = Map.copyOf(packages);
        resourceOverrides = Map.copyOf(resourceOverrides);
    }
}
