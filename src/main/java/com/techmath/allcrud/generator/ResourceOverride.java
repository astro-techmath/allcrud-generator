package com.techmath.allcrud.generator;

import java.util.Map;
import java.util.Set;

// Per-resource exception to GenerationRequest's defaults (allcrud-generator.yml's
// "resources.<name>" block) - all fields nullable, null means "inherit the default for this
// resource".
//
// generate, when non-null, REPLACES the default layer set entirely for that resource (not a
// merge) - matches the yml design (a resource declaring "generate" is explicitly stating its
// whole layer list, not adding to the global one).
//
// basePath: see docs/adr/0005-basepath-absolute-not-concatenated.md
//
// packageOverrides: see docs/adr/0003-packages-global-with-resource-override.md. Deliberately
// available on all 5 layers, not just pojo: the same real-world need (entities split across
// packages per module/domain) applies to Controller/Service/etc, not only the POJO.
public record ResourceOverride(
        Set<GeneratedLayer> generate,
        OnRegenerate pojoOnRegenerate,
        String basePath,
        Map<GeneratedLayer, String> packageOverrides
) {
}
