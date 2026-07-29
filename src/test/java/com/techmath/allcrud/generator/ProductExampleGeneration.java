package com.techmath.allcrud.generator;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

// TEST-SUPPORT ONLY - not the generator's production entrypoint.
// Shares the CodegenConfigurator/DefaultGenerator invocation duplicated across this
// module's compat tests (see GeneratedProductVoCompatTest,
// GeneratedControllerAndServiceCompatTest). The shape of a real production entrypoint
// (public API, CLI, Gradle plugin, whatever) is a separate, not-yet-discussed design
// decision - do not grow this class into one as a side effect of test cleanup.
final class ProductExampleGeneration {

    private static final String SPEC = "src/main/resources/specs/product-example.yaml";
    private static final String TEMPLATE_DIR = "src/main/resources/templates";

    private ProductExampleGeneration() {
    }

    static void generate(String outputDir) {
        generate(outputDir, false, null);
    }

    static void generate(String outputDir, boolean withApiLayerTemplates) {
        generate(outputDir, withApiLayerTemplates, null);
    }

    // pojoNamingStyle: "VO" or "DTO", passed through to AllcrudSpringCodegen as the
    // allcrudPojoNamingStyle additional property; null lets it default (VO). modelNameSuffix
    // is NOT set here - AllcrudSpringCodegen#processOpts derives it from that same property,
    // so this is the one knob callers need.
    static void generate(String outputDir, boolean withApiLayerTemplates, String pojoNamingStyle) {
        CodegenConfigurator configurator = new CodegenConfigurator()
                // Custom CodegenConfig (see AllcrudSpringCodegen), not the stock "spring"
                // generator: resolves allcrudEntityName/allcrudPojoClassName/allcrudIdType
                // from the actual OpenAPI model instead of leaving them hardcoded in the
                // templates. CodegenConfigLoader#forName falls back to
                // Class.forName(name).newInstance() when the name isn't a registered SPI
                // generator, so a fully-qualified class name works here directly.
                .setGeneratorName("com.techmath.allcrud.generator.codegen.AllcrudSpringCodegen")
                .setInputSpec(SPEC)
                .setTemplateDir(TEMPLATE_DIR)
                .setOutputDir(outputDir)
                // We don't use the JsonNullable-based absent-vs-null distinction, and
                // org.openapitools:jackson-databind-nullable isn't a dependency here -
                // disabling this avoids an unresolved import in the generated VO/DTO.
                .addAdditionalProperty("openApiNullable", false);

        if (pojoNamingStyle != null) {
            configurator.addAdditionalProperty("allcrudPojoNamingStyle", pojoNamingStyle);
        }

        ClientOptInput clientOptInput = configurator.toClientOptInput();

        if (withApiLayerTemplates) {
            registerApiLayerTemplates(clientOptInput);
        }

        new DefaultGenerator().opts(clientOptInput).generate();
    }

    // ClientOptInput#getConfig() is deprecated in openapi-generator 7.23.0 with no
    // replacement exposed yet for mutating apiTemplateFiles() programmatically - it's
    // still the only supported way to register a new per-tag template file outside of
    // writing a custom CodegenConfig subclass.
    @SuppressWarnings("deprecation")
    private static void registerApiLayerTemplates(ClientOptInput clientOptInput) {
        // service.mustache/repository.mustache/converter.mustache are brand-new templates
        // (the stock "spring" generator has none of these layers) - registering them here
        // as per-tag api template files is the supported extension point for this
        // (CodegenConfig#apiTemplateFiles()).
        clientOptInput.getConfig().apiTemplateFiles().put("service.mustache", "Service.java");
        clientOptInput.getConfig().apiTemplateFiles().put("repository.mustache", "Repository.java");
        clientOptInput.getConfig().apiTemplateFiles().put("converter.mustache", "Converter.java");
    }

}
