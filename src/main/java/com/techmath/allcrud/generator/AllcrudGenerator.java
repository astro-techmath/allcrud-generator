package com.techmath.allcrud.generator;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.openapitools.codegen.config.GlobalSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
        generateGlobalExceptionHandler(request);

        // Staging + move + package rewrite + overwrite policy (idempotent-scaffolding rework):
        // openapi-generator always runs into a scratch directory first, never directly into
        // the caller's sourceRoot. Each staged file gets its "package ...;" line (always line
        // 1 - verified empirically, see relocate()) rewritten to its configured target
        // package, then is written under sourceRoot at the path that package implies - unless
        // the target already exists and the per-layer overwrite policy says to leave it alone
        // (see shouldOverwrite()). yml-driven configuration (allcrud-generator.yml) is not
        // wired up yet - callers still pass layersToGenerate/packages/pojoOnRegenerate
        // directly, hardcoded on their end.
        Path stagingDir;
        try {
            stagingDir = Files.createTempDirectory("allcrud-generator-staging");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
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
                    .setOutputDir(stagingDir.toString())
                    // We don't use the JsonNullable-based absent-vs-null distinction, and
                    // org.openapitools:jackson-databind-nullable isn't a dependency here -
                    // disabling this avoids an unresolved import in the generated VO/DTO.
                    .addAdditionalProperty("openApiNullable", false)
                    .addAdditionalProperty("allcrudPojoNamingStyle", request.pojoNamingStyle().name())
                    // Baked into the generated @RequestMapping literal by AllcrudSpringCodegen
                    // (see ALLCRUD_BASE_PATH_PREFIX/ALLCRUD_BASE_PATH_OVERRIDES there) - has to
                    // happen at codegen time, not in relocate() below, since it's part of the
                    // Java source text itself, not a file-placement decision.
                    .addAdditionalProperty("allcrudBasePathPrefix", request.basePathPrefix())
                    .addAdditionalProperty("allcrudBasePathOverrides", basePathOverrides(request))
                    // Cross-layer imports (Controller importing the Entity/POJO/Service/
                    // Converter it depends on, Service importing Repository, etc.) used to
                    // either hardcode "org.openapitools.*" or assume "same package, no import
                    // needed" - both wrong once packages.<layer> in allcrud-generator.yml can
                    // put each layer in a different package.
                    //
                    // allcrudPojoPackage/RepositoryPackage/ConverterPackage/ServicePackage are
                    // deliberately NOT set here as additionalProperties (unlike a first attempt
                    // at this): DefaultGenerator#generateApis calls
                    // operation.putAll(config.additionalProperties()) AFTER
                    // postProcessOperationsWithModels already ran for that same operation
                    // (confirmed by reading DefaultGenerator's source, not assumed) - any
                    // per-resource value AllcrudSpringCodegen#postProcessOperationsWithModels
                    // puts under one of those 4 exact keys would be silently clobbered right
                    // back to the global default by that putAll, since it blindly overwrites any
                    // key present in additionalProperties(). allcrudEntityPackage/allcrudBasePath
                    // don't hit this because they're NEVER set as additionalProperties at all -
                    // only ever objs.put() per-resource, so putAll has nothing to clobber for
                    // those key names. The fix here is the same: the 4 global defaults travel
                    // under a DIFFERENT key (allcrudLayerPackages, a plain layer-name -> package
                    // map, read only by AllcrudSpringCodegen, never by a template directly) so
                    // putAll can't collide with the per-resource-resolved
                    // allcrudPojoPackage/etc. keys the templates actually read.
                    .addAdditionalProperty("allcrudSourceRoot", request.sourceRoot().toString())
                    .addAdditionalProperty("allcrudLayerPackages", layerPackages(request))
                    // Per-resource exceptions (ResourceOverride#packageOverrides) to the global
                    // layer packages just above - resourceName -> layer name -> package. Read by
                    // AllcrudSpringCodegen#postProcessOperationsWithModels alongside
                    // allcrudLayerPackages to resolve each of the 4 per-resource, so a
                    // Controller/Service/Converter importing another layer's class always
                    // imports the package that layer ACTUALLY landed in after relocate() below -
                    // not the global default, which would be wrong the instant a resource
                    // overrides one of its layers' packages.
                    .addAdditionalProperty("allcrudPackageOverrides", packageOverrides(request));

            ClientOptInput clientOptInput = configurator.toClientOptInput();

            // Always registered, regardless of layersToGenerate: staging generates all 5
            // layers unconditionally (openapi-generator's own pipeline, not fought here) -
            // relocateOne() is what filters which ones actually reach sourceRoot.
            registerApiLayerTemplates(clientOptInput);

            // The stock "spring" generator's supporting files (Application main class,
            // SpringDocConfiguration, ApiUtil, HomeController) don't map to the VO/Repository/
            // Converter/Service/Controller layer model this project generates - a real consumer
            // already has its own Application class. None of our 5 custom templates reference
            // them (verified).
            //
            // DefaultGenerator#configureGeneratorProperties reads these 4 GlobalSettings flags
            // (apis/models/supportingFiles/webhooks) by PRESENCE, not value: if NONE are set, it
            // defaults all 4 to generate=true; the instant ANY one is set, the other unset ones
            // default to generate=false individually. So setting SUPPORTING_FILES="false" alone
            // does NOT disable it (a set property is "on" regardless of its string value for this
            // particular check) - it silently disables MODELS/APIS instead, since they were left
            // unset. The fix is the reverse: explicitly mark apis/models as present and leave
            // supportingFiles unset, so it falls through to the false default in the same branch.
            //
            // The value must be "" (empty), NOT "true": APIS/MODELS are dual-purpose - presence
            // decides the boolean above, but DefaultGenerator#getPropertyAsSet (used elsewhere to
            // filter which specific models/apis to generate, e.g. "-Dmodels=Product,Order") parses
            // this SAME string as a CSV set. "true" would be parsed as "only generate a model/api
            // literally named true", filtering out everything and silently producing zero files -
            // hit this for real while verifying against 7.23.0's source, not a hypothetical.
            GlobalSettings.setProperty(CodegenConstants.APIS, "");
            GlobalSettings.setProperty(CodegenConstants.MODELS, "");

            new DefaultGenerator().opts(clientOptInput).generate();

            relocate(stagingDir, request);
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    // The one PROJECT-level (not per-resource) artifact this generator produces - runs once,
    // independent of the OpenAPI spec entirely (no staging dir, no openapi-generator pipeline,
    // no per-resource classify-by-suffix relocate: there's no resource name or spec content
    // involved, just package + className from allcrud-generator.yml's "exceptionHandler" block).
    // "Generate once, never overwrite", no configurable exception - see ExceptionHandlerConfig
    // for why. A plain Java text block, not a .mustache template: the content has zero
    // variability beyond package/className substitution, so routing it through the
    // openapi-generator template/staging machinery built for the 5 per-resource layers would be
    // needless coupling for no benefit - and would require re-enabling supportingFiles, which
    // this project deliberately disabled elsewhere in this method (see the GlobalSettings
    // comment below) because none of the stock supporting files map to this project's layer
    // model.
    private static void generateGlobalExceptionHandler(GenerationRequest request) {
        ExceptionHandlerConfig config = request.exceptionHandler();
        if (!config.generate()) {
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

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // layer name (GeneratedLayer#name(), e.g. "SERVICE") -> global package - only the 4 layers
    // AllcrudSpringCodegen resolves per-resource need to travel this way (see the comment above
    // this method's call site); CONTROLLER is deliberately excluded, nothing ever imports "the
    // Controller" from another layer, so it has no per-resource resolution to feed.
    private static Map<String, String> layerPackages(GenerationRequest request) {
        Map<String, String> byLayerName = new LinkedHashMap<>();
        byLayerName.put(GeneratedLayer.POJO.name(), request.packages().get(GeneratedLayer.POJO));
        byLayerName.put(GeneratedLayer.REPOSITORY.name(), request.packages().get(GeneratedLayer.REPOSITORY));
        byLayerName.put(GeneratedLayer.CONVERTER.name(), request.packages().get(GeneratedLayer.CONVERTER));
        byLayerName.put(GeneratedLayer.SERVICE.name(), request.packages().get(GeneratedLayer.SERVICE));
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
    private static void relocate(Path stagingDir, GenerationRequest request) {
        try (Stream<Path> files = Files.walk(stagingDir)) {
            files.filter(Files::isRegularFile).forEach(source -> relocateOne(source, request));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void relocateOne(Path source, GenerationRequest request) {
        String fileName = source.getFileName().toString();
        StagedFile staged = classify(fileName, request.pojoNamingStyle());
        GeneratedLayer layer = staged.layer();

        ResourceOverride override = request.resourceOverrides().get(staged.resourceName());
        Set<GeneratedLayer> effectiveLayers = (override != null && override.generate() != null)
                ? override.generate()
                : request.defaultLayersToGenerate();

        if (!effectiveLayers.contains(layer)) {
            return;
        }

        String overridePackage = override != null && override.packageOverrides() != null
                ? override.packageOverrides().get(layer)
                : null;
        String targetPackage = overridePackage != null ? overridePackage : request.packages().get(layer);
        if (targetPackage == null) {
            throw new IllegalStateException(
                    "No target package configured for layer " + layer + " (file " + fileName + ")");
        }

        Path target = request.sourceRoot()
                .resolve(targetPackage.replace('.', '/'))
                .resolve(fileName);

        OnRegenerate effectivePojoOnRegenerate = (override != null && override.pojoOnRegenerate() != null)
                ? override.pojoOnRegenerate()
                : request.defaultPojoOnRegenerate();

        if (Files.exists(target) && !shouldOverwrite(layer, effectivePojoOnRegenerate)) {
            return;
        }

        String content;
        try {
            content = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String rewritten = rewritePackageStatement(content, targetPackage, fileName);

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, rewritten, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The fixed table: Repository/Converter/Service/Controller never overwrite an existing
    // file, no configuration knob - they can carry hand-written business logic (@Query,
    // custom methods) that a blind overwrite would destroy. Only POJO's policy is
    // configurable (GenerationRequest#pojoOnRegenerate), because it's expected to stay a
    // thin mirror of the OpenAPI schema, safe to keep in sync with the contract by default...
    // except this project defaults pojoOnRegenerate to PRESERVE too (see callers) - overwrite
    // is strictly opt-in, never assumed.
    private static boolean shouldOverwrite(GeneratedLayer layer, OnRegenerate pojoOnRegenerate) {
        if (layer != GeneratedLayer.POJO) {
            return false;
        }
        return pojoOnRegenerate == OnRegenerate.OVERWRITE;
    }

    // Suffix-based classification of our own template output filenames (ProductVO.java,
    // ProductController.java, etc) into (resourceName, layer) - resourceName is the suffix
    // stripped ("Product"), needed to resolve GenerationRequest#resourceOverrides. Order
    // doesn't matter - the 5 suffixes are mutually exclusive by construction (see
    // apiController/converter/pojo/repository/service.mustache output filenames). Throws on
    // anything else: with supporting files disabled, every staged file is expected to match
    // one of these - an unrecognized file means either a template was added without updating
    // this classifier, or a stale assumption, and failing loudly beats silently misplacing
    // (or losing) a file.
    private static StagedFile classify(String fileName, PojoNamingStyle pojoNamingStyle) {
        String simpleName = fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
        for (GeneratedLayer layer : GeneratedLayer.values()) {
            String suffix = layer == GeneratedLayer.POJO ? pojoNamingStyle.name() : capitalize(layer.name());
            if (simpleName.endsWith(suffix)) {
                String resourceName = simpleName.substring(0, simpleName.length() - suffix.length());
                return new StagedFile(resourceName, layer);
            }
        }
        throw new IllegalStateException("Unrecognized generated file, no known layer suffix matched: " + fileName);
    }

    private static String capitalize(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private record StagedFile(String resourceName, GeneratedLayer layer) {
    }

    // Verified empirically (not assumed) that the package statement is always the literal
    // first line of every one of our 5 templates' output, with nothing (license header,
    // blank line) before it - see the AllcrudGeneratorPluginFunctionalTest smoke test output
    // this was checked against. Fails loudly if that ever stops being true rather than
    // silently corrupting the file.
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

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
