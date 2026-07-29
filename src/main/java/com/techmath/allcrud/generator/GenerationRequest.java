package com.techmath.allcrud.generator;

import java.nio.file.Path;
import java.util.Objects;

// generateServiceLayer: registers service.mustache/repository.mustache/converter.mustache
// as additional per-tag api template files (see AllcrudGenerator#registerApiLayerTemplates).
// The stock "spring" generator has none of these layers, so this is off by default only
// insofar as callers opt in explicitly - there is no implicit default here.
public record GenerationRequest(
        Path specPath,
        Path outputDir,
        PojoNamingStyle pojoNamingStyle,
        boolean generateServiceLayer
) {
    public GenerationRequest {
        Objects.requireNonNull(specPath, "specPath");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(pojoNamingStyle, "pojoNamingStyle");
    }
}
