package com.techmath.allcrud.generator;

import java.util.Set;

// Per-resource exception to GenerationRequest's defaults (allcrud-generator.yml's
// "resources.<name>" block) - all fields nullable, null means "inherit the default for this
// resource".
//
// generate, when non-null, REPLACES the default layer set entirely for that resource (not a
// merge) - matches the yml design (a resource declaring "generate" is explicitly stating its
// whole layer list, not adding to the global one).
//
// basePath, when non-null, is a FINAL absolute @RequestMapping path for this resource - NOT
// concatenated with GenerationRequest#basePathPrefix (that's the entire point of declaring
// one: routing.basePathPrefix + resources.<name>.basePath together would be a surprising,
// hard-to-predict combination; the override always wins outright).
public record ResourceOverride(Set<GeneratedLayer> generate, OnRegenerate pojoOnRegenerate, String basePath) {
}
