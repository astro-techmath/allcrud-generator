package com.techmath.allcrud.generator;

// Stable vocabulary for the 7 layers this project generates - matches the
// allcrud-generator.yml naming (packages.pojo/repository/converter/service/controller,
// resources.<name>.generate, etc). "POJO" not "VO"/"DTO": the layer name must stay stable
// regardless of which PojoNamingStyle is selected (allcrudPojoClassName is the class-name
// concept this already mirrors internally).
//
// yamlKey: the allcrud-generator.yml token for this layer - NOT simply name().toLowerCase(),
// because UNIT_TEST/INTEGRATION_TEST are multi-word Java enum constants (conventionally
// SCREAMING_SNAKE_CASE) but the yml uses camelCase ("unitTest"/"integrationTest", matching the
// rest of the yml's style) - name().toLowerCase() would produce "unit_test" instead. Every
// yml-key lookup (parsePackages' fixed 5, parseLayerName for "generate" lists,
// parseResourcePackageOverrides) goes through this instead of the enum name directly, so Java
// naming conventions and yml naming conventions stay decoupled on purpose.
public enum GeneratedLayer {
    POJO("pojo"),
    REPOSITORY("repository"),
    CONVERTER("converter"),
    SERVICE("service"),
    CONTROLLER("controller"),
    // Per-resource only - never a key in the global "packages:" block (no fallback default to
    // set there; the default is computed dynamically from the sibling layer's OWN resolved
    // package for that resource, see AllcrudGenerator#resolveEffectivePackage) and never a
    // layer relocate() places under GenerationRequest#sourceRoot (they go under
    // GenerationRequest#testSourceRoot instead - fixed, not configurable).
    UNIT_TEST("unitTest"),
    INTEGRATION_TEST("integrationTest");

    private final String yamlKey;

    GeneratedLayer(String yamlKey) {
        this.yamlKey = yamlKey;
    }

    public String yamlKey() {
        return yamlKey;
    }
}
