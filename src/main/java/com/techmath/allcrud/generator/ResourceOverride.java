package com.techmath.allcrud.generator;

import java.util.Set;

// Per-resource exception to GenerationRequest's defaults (allcrud-generator.yml's
// "resources.<name>" block) - both fields nullable, null means "inherit the default for this
// resource". generate, when non-null, REPLACES the default layer set entirely for that
// resource (not a merge) - matches the yml design (a resource declaring "generate" is
// explicitly stating its whole layer list, not adding to the global one).
public record ResourceOverride(Set<GeneratedLayer> generate, OnRegenerate pojoOnRegenerate) {
}
