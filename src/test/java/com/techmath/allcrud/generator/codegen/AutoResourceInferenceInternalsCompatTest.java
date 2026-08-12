package com.techmath.allcrud.generator.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenProperty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Direct unit coverage of the pure-function helpers behind x-allcrud-auto-resource inference
// (resolveListItemSchemaName/firstSuccessResponseSchema/referencesSchema/usesUri) - all of these
// operate purely on swagger-core model objects (Operation/Schema/ApiResponses), with zero
// dependency on a real OpenAPI document, a spec file, or the generation pipeline, so hand-built
// objects exercise their branches directly. AutoResourceInferenceCompatTest (parent package)
// already proves the positive, real-spec end-to-end paths - these fill in the negative/edge
// branches that don't have a natural real-spec trigger (a GET with no responses at all, a
// response schema that isn't an array, etc), same "the empty case is still real behavior worth
// locking down" spirit as EntityResolutionCompatTest.
class AutoResourceInferenceInternalsCompatTest {

    private final AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();

    @Test
    void preprocessOpenApiDoesNothingWhenPathsIsNull() {
        OpenAPI openAPI = new OpenAPI();
        // SpringCodegen#preprocessOpenAPI (called first, via super) needs a non-null Info -
        // unrelated to what this test actually covers (inferAllcrudResources' own paths==null
        // guard), just satisfying the base class' own precondition.
        openAPI.setInfo(new io.swagger.v3.oas.models.info.Info());
        openAPI.addExtension("x-allcrud-auto-resource", Boolean.TRUE);
        openAPI.setPaths(null);

        // Must not throw despite paths being null - inferAllcrudResources' own early return.
        codegen.preprocessOpenAPI(openAPI);

        assertNull(openAPI.getPaths(), "The early return must not have reinitialized paths into an empty object");
    }

    @Test
    void resolveListItemSchemaNameReturnsNullWhenNoGetOperation() throws Exception {
        assertNull(invokeResolveListItemSchemaName(new OpenAPI(), null));
    }

    @Test
    void resolveListItemSchemaNameReturnsNullWhenResponseIsNotAnArray() throws Exception {
        Operation getOperation = new Operation();
        Schema<Object> objectSchema = new Schema<>();
        objectSchema.set$ref("#/components/schemas/Product");
        getOperation.setResponses(responsesWithSchema("200", objectSchema));

        assertNull(invokeResolveListItemSchemaName(new OpenAPI(), getOperation));
    }

    @Test
    void firstSuccessResponseSchemaReturnsNullWhenResponsesIsNull() throws Exception {
        Operation operation = new Operation();
        operation.setResponses(null);

        assertNull(invokeFirstSuccessResponseSchema(new OpenAPI(), operation));
    }

    @Test
    void firstSuccessResponseSchemaReturnsNullWhenNo2xxResponseExists() throws Exception {
        Operation operation = new Operation();
        Schema<Object> schema = new Schema<>();
        schema.set$ref("#/components/schemas/Product");
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("404", new ApiResponse());
        operation.setResponses(responses);

        assertNull(invokeFirstSuccessResponseSchema(new OpenAPI(), operation));
    }

    @Test
    void referencesSchemaReturnsFalseWhenNoOperationReferencesIt() throws Exception {
        io.swagger.v3.oas.models.PathItem itemPathItem = new io.swagger.v3.oas.models.PathItem();
        Operation getOperation = new Operation();
        getOperation.setResponses(responsesWithSchema("200", refSchema("Unrelated")));
        itemPathItem.setGet(getOperation);
        // put/patch/delete deliberately absent (null) - exercises the null-operation continue
        // branch in referencesSchema's candidate loop too.

        assertFalse(invokeReferencesSchema(new OpenAPI(), itemPathItem, "Product"));
    }

    @Test
    void referencesSchemaReturnsFalseWhenRequestBodyRefIsUnresolvable() throws Exception {
        // AutoResourceInferenceCompatTest#itemPathMatchedOnlyViaRequestBodySchemaIsStillInferred
        // only covers requestBody != null resolving to a real, matching schema - never the case
        // where ModelUtils.getReferencedRequestBody itself can't resolve the $ref (a dangling
        // reference in the spec). Confirmed empirically (not assumed) that this returns null
        // rather than throwing, which is what lets referencesSchema's requestSchema ternary and
        // its schemaName.equals(null) check both fall through to false safely.
        io.swagger.v3.oas.models.PathItem itemPathItem = new io.swagger.v3.oas.models.PathItem();
        Operation putOperation = new Operation();
        io.swagger.v3.oas.models.parameters.RequestBody danglingRef = new io.swagger.v3.oas.models.parameters.RequestBody();
        danglingRef.set$ref("#/components/requestBodies/DoesNotExist");
        putOperation.setRequestBody(danglingRef);
        itemPathItem.setPut(putOperation);

        assertFalse(invokeReferencesSchema(new OpenAPI(), itemPathItem, "Product"));
    }

    @Test
    void schemaRefNameReturnsNullForInlineSchemaWithNoRef() throws Exception {
        // Every other referencesSchema/schemaRefName test's schemas are $ref'd - schema != null
        // but schema.get$ref() == null (an inline, non-component schema) never got exercised on
        // its own, only the schema == null short-circuit and the has-a-ref path.
        Schema<Object> inlineSchema = new Schema<>();
        inlineSchema.setType("string");

        assertNull(invokeSchemaRefName(inlineSchema));
    }

    @Test
    void usesUriReturnsFalseForNonUriProperty() throws Exception {
        CodegenProperty property = new CodegenProperty();
        property.dataType = "String";
        property.items = null;

        assertFalse(invokeUsesUri(property));
    }

    @Test
    void usesUriReturnsTrueForUriContainerItemType() throws Exception {
        CodegenProperty property = new CodegenProperty();
        property.dataType = "List";
        property.items = new CodegenProperty();
        property.items.dataType = "URI";

        assertTrue(invokeUsesUri(property));
    }

    @Test
    void usesUriReturnsTrueForDirectUriProperty() throws Exception {
        // Not a container - the property's OWN dataType is "URI" directly, the branch
        // usesUriReturnsTrueForUriContainerItemType above doesn't reach (it short-circuits on
        // the container check before ever inspecting property.items).
        CodegenProperty property = new CodegenProperty();
        property.dataType = "URI";

        assertTrue(invokeUsesUri(property));
    }

    @Test
    void inferenceSkipsCollectionPathWithNoMatchingGetResponse() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new io.swagger.v3.oas.models.info.Info());
        openAPI.addExtension("x-allcrud-auto-resource", Boolean.TRUE);

        io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
        io.swagger.v3.oas.models.PathItem collectionPathItem = new io.swagger.v3.oas.models.PathItem();
        // No GET at all on the collection path - resolveListItemSchemaName returns null,
        // inferAllcrudResources' own "continue" (not resolveListItemSchemaName's own null
        // return, already covered directly) is what's exercised by going through
        // preprocessOpenAPI here instead of calling resolveListItemSchemaName in isolation.
        paths.addPathItem("/widgets", collectionPathItem);
        openAPI.setPaths(paths);

        // Must not throw, and must not mark the path as a resource - proving the loop iteration
        // completes normally instead of NPE-ing past a null schemaName.
        codegen.preprocessOpenAPI(openAPI);

        assertNull(collectionPathItem.getExtensions(),
                "The collection path must NOT be marked as a resource - it has no matching GET response");
    }

    @Test
    void inferenceSkipsItemPathThatDoesNotReferenceTheCollectionSchema() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new io.swagger.v3.oas.models.info.Info());
        openAPI.addExtension("x-allcrud-auto-resource", Boolean.TRUE);

        io.swagger.v3.oas.models.PathItem collectionPathItem = new io.swagger.v3.oas.models.PathItem();
        Operation collectionGet = new Operation();
        io.swagger.v3.oas.models.media.ArraySchema arraySchema = new io.swagger.v3.oas.models.media.ArraySchema();
        arraySchema.setItems(refSchema("Widget"));
        collectionGet.setResponses(responsesWithSchema("200", arraySchema));
        collectionPathItem.setGet(collectionGet);

        io.swagger.v3.oas.models.PathItem itemPathItem = new io.swagger.v3.oas.models.PathItem();
        Operation itemGet = new Operation();
        itemGet.setResponses(responsesWithSchema("200", refSchema("Unrelated")));
        itemPathItem.setGet(itemGet);

        io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
        paths.addPathItem("/widgets", collectionPathItem);
        paths.addPathItem("/widgets/{id}", itemPathItem);
        openAPI.setPaths(paths);

        codegen.preprocessOpenAPI(openAPI);

        assertNull(itemPathItem.getExtensions(),
                "The item path must NOT be marked as a resource - its schema doesn't match the collection's");
    }

    private ApiResponses responsesWithSchema(String statusCode, Schema<?> schema) {
        ApiResponse response = new ApiResponse();
        io.swagger.v3.oas.models.media.Content content = new io.swagger.v3.oas.models.media.Content();
        io.swagger.v3.oas.models.media.MediaType mediaType = new io.swagger.v3.oas.models.media.MediaType();
        mediaType.setSchema(schema);
        content.addMediaType("application/json", mediaType);
        response.setContent(content);
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse(statusCode, response);
        return responses;
    }

    private Schema<Object> refSchema(String name) {
        Schema<Object> schema = new Schema<>();
        schema.set$ref("#/components/schemas/" + name);
        return schema;
    }

    private String invokeResolveListItemSchemaName(OpenAPI openAPI, Operation getOperation) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod(
                "resolveListItemSchemaName", OpenAPI.class, Operation.class);
        method.setAccessible(true);
        return unwrap(() -> (String) method.invoke(codegen, openAPI, getOperation));
    }

    private Schema<?> invokeFirstSuccessResponseSchema(OpenAPI openAPI, Operation operation) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod(
                "firstSuccessResponseSchema", OpenAPI.class, Operation.class);
        method.setAccessible(true);
        return unwrap(() -> (Schema<?>) method.invoke(codegen, openAPI, operation));
    }

    private boolean invokeReferencesSchema(OpenAPI openAPI, io.swagger.v3.oas.models.PathItem itemPathItem, String schemaName)
            throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod(
                "referencesSchema", OpenAPI.class, io.swagger.v3.oas.models.PathItem.class, String.class);
        method.setAccessible(true);
        return unwrap(() -> (Boolean) method.invoke(codegen, openAPI, itemPathItem, schemaName));
    }

    private String invokeSchemaRefName(Schema<?> schema) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod("schemaRefName", Schema.class);
        method.setAccessible(true);
        return unwrap(() -> (String) method.invoke(codegen, schema));
    }

    private boolean invokeUsesUri(CodegenProperty property) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod("usesUri", CodegenProperty.class);
        method.setAccessible(true);
        return unwrap(() -> (Boolean) method.invoke(codegen, property));
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private <T> T unwrap(ThrowingSupplier<T> invocation) throws Exception {
        try {
            return invocation.get();
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

}
