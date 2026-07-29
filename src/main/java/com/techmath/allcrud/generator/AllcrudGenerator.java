package com.techmath.allcrud.generator;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

// Public generation entrypoint - not a production entrypoint (CLI, Gradle plugin) by
// itself yet, that's a separate, not-yet-designed decision. This is the generic,
// parameterized form of what used to be the test-only ProductExampleGeneration: same
// CodegenConfigurator/DefaultGenerator invocation, same AllcrudSpringCodegen and
// custom templates, no design decisions reopened.
public final class AllcrudGenerator {

    // Bundled with this generator's own custom templates (pojo/model/repository/
    // converter/service) - not something a caller supplies, unlike specPath/outputDir.
    //
    // "templates" (not "src/main/resources/templates"): CodegenConfigurator#setTemplateDir
    // is checked both as a filesystem path AND, transparently, as a classpath resource root
    // by openapi-generator's GeneratorTemplateContentLocator (no "classpath:" prefix needed -
    // it just retries the same string via ClassLoader#getResource). "src/main/resources"
    // is where the templates live in this module's source tree, but Gradle's processResources
    // strips that prefix when packaging - the jar has them at "templates/*.mustache", so that's
    // the string that must resolve. The filesystem-relative form only ever worked by accident,
    // because our own tests happen to run with the CWD at this repo's root; any real caller
    // (the Gradle plugin included) runs with a different CWD and the filesystem check silently
    // fails over to this classpath check - see AllcrudGeneratorPluginFunctionalTest.
    private static final String TEMPLATE_DIR = "templates";

    private AllcrudGenerator() {
    }

    public static void generate(GenerationRequest request) {
        CodegenConfigurator configurator = new CodegenConfigurator()
                // Custom CodegenConfig (see AllcrudSpringCodegen), not the stock "spring"
                // generator: resolves allcrudEntityName/allcrudPojoClassName/allcrudIdType
                // from the actual OpenAPI model instead of leaving them hardcoded in the
                // templates. CodegenConfigLoader#forName falls back to
                // Class.forName(name).newInstance() when the name isn't a registered SPI
                // generator, so a fully-qualified class name works here directly.
                .setGeneratorName("com.techmath.allcrud.generator.codegen.AllcrudSpringCodegen")
                .setInputSpec(request.specPath().toString())
                .setTemplateDir(TEMPLATE_DIR)
                .setOutputDir(request.outputDir().toString())
                // We don't use the JsonNullable-based absent-vs-null distinction, and
                // org.openapitools:jackson-databind-nullable isn't a dependency here -
                // disabling this avoids an unresolved import in the generated VO/DTO.
                .addAdditionalProperty("openApiNullable", false)
                .addAdditionalProperty("allcrudPojoNamingStyle", request.pojoNamingStyle().name());

        ClientOptInput clientOptInput = configurator.toClientOptInput();

        if (request.generateServiceLayer()) {
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
