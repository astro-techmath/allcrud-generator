package com.techmath.allcrud.generator;

import com.techmath.allcrud.generator.codegen.AllcrudSpringCodegen;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.GlobalSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// See docs/notes/AllcrudGenerator.md#allcrudgenerator-is-not-yet-a-production-entrypoint
public final class AllcrudGenerator {

    // Bundled with this generator's own custom templates (pojo/model/repository/
    // converter/service) - not something a caller supplies, unlike specPath/outputDir.
    //
    // See docs/notes/AllcrudGenerator.md#template-resolution-filesystem--classpath-via-generatortemplatecontentlocator
    // for why "templates" (not "src/main/resources/templates") is the string that resolves.
    private static final String TEMPLATE_DIR = "templates";

    private AllcrudGenerator() {
    }

    public static void generate(GenerationRequest request) {
        generateGlobalExceptionHandler(request);

        // See docs/notes/AllcrudGenerator.md#staging--move--package-rewrite-idempotent-scaffolding-design
        Path stagingDir = IoExceptions.createTempDirectory("allcrud-generator-staging");

        try {
            CodegenConfigurator configurator = new CodegenConfigurator()
                    // Custom CodegenConfig (see AllcrudSpringCodegen), not the stock "spring"
                    // generator - see docs/notes/AllcrudSpringCodegen.md#codegenconfigloaderforname-fallback
                    // for why a fully-qualified class name works here directly.
                    .setGeneratorName("com.techmath.allcrud.generator.codegen.AllcrudSpringCodegen")
                    .setInputSpec(request.specPath().toString())
                    .setTemplateDir(TEMPLATE_DIR)
                    .setOutputDir(stagingDir.toString())
                    // See docs/notes/AllcrudGenerator.md#openapinullable-disabled--no-jackson-databind-nullable-dependency
                    .addAdditionalProperty("openApiNullable", false)
                    .addAdditionalProperty("allcrudPojoNamingStyle", request.pojoNamingStyle().name())
                    // See docs/notes/AllcrudGenerator.md#allcrudbasepathprefixoverrides-set-at-codegen-time-not-in-relocate
                    .addAdditionalProperty("allcrudBasePathPrefix", request.basePathPrefix())
                    .addAdditionalProperty("allcrudBasePathOverrides", basePathOverrides(request))
                    // See docs/notes/AllcrudGenerator.md#cross-layer-imports-need-per-layer-packages
                    // and docs/notes/AllcrudGenerator.md#putall--defaultgeneratorgenerateapis-canonical--see-also-allcrudspringcodegen
                    .addAdditionalProperty("allcrudSourceRoot", request.sourceRoot().toString())
                    .addAdditionalProperty("allcrudLayerPackages", layerPackages(request))
                    // See docs/adr/0003-packages-global-with-resource-override.md
                    // resourceName -> layer name -> package, read by
                    // AllcrudSpringCodegen#postProcessOperationsWithModels alongside allcrudLayerPackages.
                    .addAdditionalProperty("allcrudPackageOverrides", packageOverrides(request));

            ClientOptInput clientOptInput = configurator.toClientOptInput();

            // See docs/notes/AllcrudGenerator.md#registerapilayertemplates-always-registered-regardless-of-layerstogenerate
            registerApiLayerTemplates(clientOptInput);

            // See docs/notes/AllcrudGenerator.md#stock-supportingfiles-dont-map-to-this-projects-layer-model
            // and docs/notes/AllcrudGenerator.md#globalsettings-presence-vs-value--csv-dual-purpose-apismodels
            // for why apis/models are set to "" here, not left unset or set to "true".
            GlobalSettings.setProperty(CodegenConstants.APIS, "");
            GlobalSettings.setProperty(CodegenConstants.MODELS, "");

            new DefaultGenerator().opts(clientOptInput).generate();

            // See docs/notes/AllcrudGenerator.md#clientoptinputgetconfig-is-deprecated-with-no-replacement-openapi-generator-7230
            @SuppressWarnings("deprecation")
            AllcrudSpringCodegen codegen = (AllcrudSpringCodegen) clientOptInput.getConfig();

            relocate(stagingDir, request, codegen.confirmedResourceNames());
        } finally {
            IoExceptions.deleteRecursively(stagingDir);
        }
    }

    // See docs/notes/AllcrudGenerator.md#generateglobalexceptionhandler-is-a-plain-text-block-not-a-mustache-template
    private static void generateGlobalExceptionHandler(GenerationRequest request) {
        ExceptionHandlerConfig config = request.exceptionHandler();
        if (!config.enabled()) {
            return;
        }

        Path target = request.sourceRoot()
                .resolve(config.targetPackage().replace('.', '/'))
                .resolve(config.className() + ".java");

        if (Files.exists(target)) {
            return;
        }

        String content = """
                package %s;

                import com.techmath.allcrud.exception.handler.AbstractGlobalExceptionHandler;
                import org.springframework.web.bind.annotation.ControllerAdvice;

                // Generated by allcrud-generator - customize the implementation below.
                @ControllerAdvice
                public class %s extends AbstractGlobalExceptionHandler {
                }
                """.formatted(config.targetPackage(), config.className());

        IoExceptions.writeFile(target, content);
    }

    // See docs/notes/AllcrudGenerator.md#layerpackages-map--controller-inclusion-history
    private static Map<String, String> layerPackages(GenerationRequest request) {
        Map<String, String> byLayerName = new LinkedHashMap<>();
        byLayerName.put(GeneratedLayer.POJO.name(), request.packages().get(GeneratedLayer.POJO));
        byLayerName.put(GeneratedLayer.REPOSITORY.name(), request.packages().get(GeneratedLayer.REPOSITORY));
        byLayerName.put(GeneratedLayer.CONVERTER.name(), request.packages().get(GeneratedLayer.CONVERTER));
        byLayerName.put(GeneratedLayer.SERVICE.name(), request.packages().get(GeneratedLayer.SERVICE));
        byLayerName.put(GeneratedLayer.CONTROLLER.name(), request.packages().get(GeneratedLayer.CONTROLLER));
        return byLayerName;
    }

    private static Map<String, String> basePathOverrides(GenerationRequest request) {
        Map<String, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceOverride> entry : request.resourceOverrides().entrySet()) {
            String basePath = entry.getValue().basePath();
            if (basePath != null) {
                overrides.put(entry.getKey(), basePath);
            }
        }
        return overrides;
    }

    // resourceName -> layer name (GeneratedLayer#name(), e.g. "SERVICE") -> package. Nested Map
    // shape mirrors basePathOverrides' flat one but one level deeper, since a resource can
    // override more than one layer's package independently.
    private static Map<String, Map<String, String>> packageOverrides(GenerationRequest request) {
        Map<String, Map<String, String>> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceOverride> entry : request.resourceOverrides().entrySet()) {
            Map<GeneratedLayer, String> layerOverrides = entry.getValue().packageOverrides();
            if (layerOverrides == null || layerOverrides.isEmpty()) {
                continue;
            }
            Map<String, String> byLayerName = new LinkedHashMap<>();
            for (Map.Entry<GeneratedLayer, String> layerEntry : layerOverrides.entrySet()) {
                byLayerName.put(layerEntry.getKey().name(), layerEntry.getValue());
            }
            overrides.put(entry.getKey(), byLayerName);
        }
        return overrides;
    }

    // Classifies each staged file by its filename suffix (Controller/Service/Repository/
    // Converter, or the configured pojo suffix - VO/DTO), rewrites its package statement to
    // the layer's configured target package, and writes it under sourceRoot at the path that
    // package implies - unless the per-layer overwrite policy says to leave an existing file
    // alone (see relocateOne).
    private static void relocate(Path stagingDir, GenerationRequest request, Set<String> confirmedResourceNames) {
        for (Path source : IoExceptions.listRegularFilesRecursively(stagingDir)) {
            relocateOne(source, request, confirmedResourceNames);
        }
    }

    // See docs/notes/AllcrudGenerator.md#resolveeffectivepackage--recursive-fallback-for-test-layers
    private static String resolveEffectivePackage(GeneratedLayer layer, String resourceName, GenerationRequest request) {
        ResourceOverride override = request.resourceOverrides().get(resourceName);
        String explicit = override != null && override.packageOverrides() != null
                ? override.packageOverrides().get(layer)
                : null;
        if (explicit != null) {
            return explicit;
        }
        if (layer == GeneratedLayer.UNIT_TEST) {
            return resolveEffectivePackage(GeneratedLayer.SERVICE, resourceName, request);
        }
        if (layer == GeneratedLayer.INTEGRATION_TEST) {
            return resolveEffectivePackage(GeneratedLayer.CONTROLLER, resourceName, request);
        }
        return request.packages().get(layer);
    }

    private static boolean isTestLayer(GeneratedLayer layer) {
        return layer == GeneratedLayer.UNIT_TEST || layer == GeneratedLayer.INTEGRATION_TEST;
    }

    private static void relocateOne(Path source, GenerationRequest request, Set<String> confirmedResourceNames) {
        String fileName = source.getFileName().toString();
        StagedFile staged = classify(fileName, request.pojoNamingStyle());
        GeneratedLayer layer = staged.layer();

        // See docs/adr/0008-pojo-schema-driven-not-resource-driven.md - a tag that never
        // resolved to a confirmed resource gets discarded here instead of reaching sourceRoot;
        // openapi-generator still rendered it into the staging dir, which is thrown away regardless.
        if (layer != GeneratedLayer.POJO && !confirmedResourceNames.contains(staged.resourceName())) {
            return;
        }

        ResourceOverride override = request.resourceOverrides().get(staged.resourceName());
        Set<GeneratedLayer> effectiveLayers = (override != null && override.generate() != null)
                ? override.generate()
                : request.defaultLayersToGenerate();

        if (!effectiveLayers.contains(layer)) {
            return;
        }

        String targetPackage = resolveEffectivePackage(layer, staged.resourceName(), request);
        if (targetPackage == null) {
            throw new IllegalStateException(
                    "No target package configured for layer " + layer + " (file " + fileName + ")");
        }

        Path baseDir = isTestLayer(layer) ? request.testSourceRoot() : request.sourceRoot();
        Path target = baseDir
                .resolve(targetPackage.replace('.', '/'))
                .resolve(fileName);

        OnRegenerate effectivePojoOnRegenerate = (override != null && override.pojoOnRegenerate() != null)
                ? override.pojoOnRegenerate()
                : request.defaultPojoOnRegenerate();

        if (Files.exists(target) && !shouldOverwrite(layer, effectivePojoOnRegenerate)) {
            return;
        }

        String content = IoExceptions.readFile(source);
        String rewritten = rewritePackageStatement(content, targetPackage, fileName);
        IoExceptions.writeFile(target, rewritten);
    }

    // See docs/adr/0001-generate-once-never-overwrite.md
    private static boolean shouldOverwrite(GeneratedLayer layer, OnRegenerate pojoOnRegenerate) {
        if (layer != GeneratedLayer.POJO) {
            return false;
        }
        return pojoOnRegenerate == OnRegenerate.OVERWRITE;
    }

    // See docs/notes/AllcrudGenerator.md#classify-suffix-non-collision--verified-by-hand-not-assumed
    private static StagedFile classify(String fileName, PojoNamingStyle pojoNamingStyle) {
        String simpleName = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            String suffix = layerSuffix(layer, pojoNamingStyle);
            if (simpleName.endsWith(suffix)) {
                String resourceName = simpleName.substring(0, simpleName.length() - suffix.length());
                return new StagedFile(resourceName, layer);
            }
        }
        throw new IllegalStateException("Unrecognized generated file, no known layer suffix matched: " + fileName);
    }

    // See docs/notes/AllcrudGenerator.md#layersuffix--why-unit_testintegration_test-arent-mechanically-derived
    private static String layerSuffix(GeneratedLayer layer, PojoNamingStyle pojoNamingStyle) {
        return switch (layer) {
            case POJO -> pojoNamingStyle.name();
            case UNIT_TEST -> "ServiceTest";
            case INTEGRATION_TEST -> "ControllerIT";
            default -> capitalize(layer.name());
        };
    }

    private static String capitalize(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private record StagedFile(String resourceName, GeneratedLayer layer) {
    }

    // See docs/notes/AllcrudGenerator.md#rewritepackagestatement--package-line-is-always-line-1-verified-empirically
    private static String rewritePackageStatement(String content, String targetPackage, String fileName) {
        int newlineIndex = content.indexOf('\n');
        String firstLine = newlineIndex == -1 ? content : content.substring(0, newlineIndex);
        if (!firstLine.startsWith("package ")) {
            throw new IllegalStateException(
                    "Expected first line of " + fileName + " to be a package statement, found: " + firstLine);
        }
        String rest = newlineIndex == -1 ? "" : content.substring(newlineIndex);
        return "package " + targetPackage + ";" + rest;
    }

    // See docs/notes/AllcrudGenerator.md#clientoptinputgetconfig-is-deprecated-with-no-replacement-openapi-generator-7230
    @SuppressWarnings("deprecation")
    private static void registerApiLayerTemplates(ClientOptInput clientOptInput) {
        // See docs/notes/AllcrudGenerator.md#registerapilayertemplates--servicerepositoryconvertermustache-are-brand-new-templates
        clientOptInput.getConfig().apiTemplateFiles().put("service.mustache", "Service.java");
        clientOptInput.getConfig().apiTemplateFiles().put("repository.mustache", "Repository.java");
        clientOptInput.getConfig().apiTemplateFiles().put("converter.mustache", "Converter.java");
        // Output suffix must match layerSuffix(UNIT_TEST, ...) above.
        clientOptInput.getConfig().apiTemplateFiles().put("unitTest.mustache", "ServiceTest.java");
        // Output suffix must match layerSuffix(INTEGRATION_TEST, ...) above.
        clientOptInput.getConfig().apiTemplateFiles().put("integrationTest.mustache", "ControllerIT.java");
    }

}
