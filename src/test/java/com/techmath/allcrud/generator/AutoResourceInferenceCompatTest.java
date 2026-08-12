package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// x-allcrud-auto-resource: true (document root) turns on automatic resource inference
// (AllcrudSpringCodegen#inferAllcrudResources) - a path pair matching the CrudController shape
// (collection GET returning an array of a named schema + item path "{collection}/{param}"
// referencing that same schema) is treated as an allcrud resource without needing
// x-allcrud-resource: true on every path by hand. x-allcrud-resource: false stays a per-path
// opt-out, explicit always wins over inferred.
//
// Also covers the gate this feature exposed as a real gap (AllcrudSpringCodegen#
// confirmedResourceNames / AllcrudGenerator#relocateOne): before it, x-allcrud-resource never
// actually controlled whether Repository/Converter/Service/Controller got generated - any tag
// whose schema had an "id" got them regardless of the marker, so "false" was a no-op. Now only
// a confirmed (explicit or inferred) resource gets those 4 layers; POJO stays ungated (schema-
// driven, not path-driven).
class AutoResourceInferenceCompatTest {

    private static final Path AUTO_RESOURCE_SPEC = Path.of("src/test/resources/fixtures/auto-resource.yaml");
    private static final Path AUTO_RESOURCE_NO_ID_SPEC = Path.of("src/test/resources/fixtures/auto-resource-noid.yaml");
    private static final Path AUTO_RESOURCE_REQUEST_BODY_SPEC =
            Path.of("src/test/resources/fixtures/auto-resource-requestbody.yaml");

    @Test
    void inferredResourceGeneratesAllFourLayersWithNoExplicitMarker() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-auto-resource-inferred");
        EntityFixtures.copyInto(sourceRoot, "Widget");

        AllcrudGenerator.generate(new GenerationRequest(
                AUTO_RESOURCE_SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.values()),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/WidgetController.java")),
                "Widget has no x-allcrud-resource anywhere in the spec - only the inferred "
                        + "collection(/widgets)+item(/widgets/{id}) path pair - and must still get a Controller");
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/WidgetService.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/WidgetRepository.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/WidgetConverter.java")));

        String controller = Files.readString(sourceRoot.resolve("org/openapitools/api/WidgetController.java"));
        assertTrue(controller.contains("extends CrudController<Widget, WidgetVO, Long>"),
                "Inferred resource must resolve the same allcrudEntityName/allcrudIdType as an "
                        + "explicit x-allcrud-resource: true would, found: " + controller);
    }

    @Test
    void extraPathSegmentAfterIdDoesNotConfuseInference() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-auto-resource-no-false-positive");
        EntityFixtures.copyInto(sourceRoot, "Widget");

        // The spec also has /widgets/{id}/export (GET, references Widget) - one path-param
        // segment too many to be "the" item path ("/widgets/{id}" is), and must not itself be
        // mistaken for one or otherwise disrupt Widget's own detection.
        AllcrudGenerator.generate(new GenerationRequest(
                AUTO_RESOURCE_SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.values()),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/WidgetController.java")));
    }

    @Test
    void explicitFalseOptsOutEvenWhenShapeMatchesInference() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-auto-resource-opt-out");
        EntityFixtures.copyInto(sourceRoot, "Widget");

        AllcrudGenerator.generate(new GenerationRequest(
                AUTO_RESOURCE_SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.values()),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        // Gizmo's paths match the exact same collection+item shape as Widget's and would be
        // inferred too, but both carry x-allcrud-resource: false explicitly - explicit must
        // win over inferred, so none of the 4 gated layers should exist for it.
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/GizmoController.java")),
                "x-allcrud-resource: false must opt this resource out even though its paths "
                        + "match the inference shape");
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/GizmoService.java")));
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/GizmoRepository.java")));
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/GizmoConverter.java")));

        // POJO is deliberately not gated by x-allcrud-resource (schema-driven, not
        // path-driven) - it's still generated even for an opted-out resource.
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/model/GizmoVO.java")));
    }

    @Test
    void inferredResourceWithNoIdPropertyStillFailsFast() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-auto-resource-no-id");

        // openapi-generator's own per-tag api generation wraps whatever
        // postProcessOperationsWithModels throws in its own RuntimeException (same wrapping
        // proven empirically for the plain "no id" case, see the fail-fast's own tests) - the
        // IllegalStateException we actually threw is the root cause, not the top-level type.
        // Constructed outside the lambda passed to assertThrows below - same reasoning as
        // AllcrudGeneratorInternalsCompatTest's equivalent fix: keeps the lambda down to the
        // single invocation actually under test.
        GenerationRequest request = new GenerationRequest(
                AUTO_RESOURCE_NO_ID_SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.values()),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER);

        RuntimeException wrapper = assertThrows(RuntimeException.class, () -> AllcrudGenerator.generate(request));

        Throwable rootCause = wrapper;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        // Gadget's /gadgets + /gadgets/{id} match the inference shape exactly, but its schema
        // has no "id" property - inference must still feed the existing id-presence fail-fast
        // (AllcrudSpringCodegen#postProcessOperationsWithModels), same as an explicit
        // x-allcrud-resource: true would.
        assertTrue(rootCause instanceof IllegalStateException, "Expected root cause to be the fail-fast's "
                + "IllegalStateException, was: " + rootCause.getClass().getName());
        assertTrue(rootCause.getMessage().contains("Resource \"Gadget\""),
                "Expected the fail-fast to name Gadget, was: " + rootCause.getMessage());
        assertTrue(rootCause.getMessage().contains("no \"id\" property"),
                "Expected the fail-fast to explain the missing id, was: " + rootCause.getMessage());
    }

    @Test
    void itemPathMatchedOnlyViaRequestBodySchemaIsStillInferred() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-auto-resource-requestbody");
        EntityFixtures.copyInto(sourceRoot, "Sprocket");

        // /sprockets/{id}'s only operation is PUT, whose 204 response has no body at all - the
        // ONLY thing linking it to Sprocket is its requestBody schema, proving
        // referencesSchema's requestBody branch (not just the response-schema one every other
        // test in this class exercises) actually confirms the match.
        AllcrudGenerator.generate(new GenerationRequest(
                AUTO_RESOURCE_REQUEST_BODY_SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.values()),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/SprocketController.java")),
                "Sprocket's item path only references its schema via the PUT's requestBody, "
                        + "not any response - inference must still confirm it as a resource");
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/SprocketService.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/SprocketRepository.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/SprocketConverter.java")));
    }

}
