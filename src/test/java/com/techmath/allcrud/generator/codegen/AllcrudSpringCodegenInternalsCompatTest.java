package com.techmath.allcrud.generator.codegen;

import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Direct unit coverage of AllcrudSpringCodegen's private resolveBasePath/resolvePackage/
// findResourceModel - reached via reflection (same technique as
// AllcrudGeneratorInternalsCompatTest in the parent package) since additionalProperties() is the
// only piece of real Codegen framework state these 3 methods actually read, and it's a plain
// public Map any CodegenConfig exposes - no OpenAPI document or full generation pipeline needed
// to exercise their own branching logic in isolation.
class AllcrudSpringCodegenInternalsCompatTest {

    @Test
    void resolveBasePathReturnsOverrideWhenPresent() throws Exception {
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_BASE_PATH_OVERRIDES,
                Map.of("Product", "/custom/products"));

        assertEquals("/custom/products", invokeResolveBasePath(codegen, "Product"));
    }

    @Test
    void resolveBasePathPrependsPrefixWhenNoOverride() throws Exception {
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_BASE_PATH_PREFIX, "/v1");

        assertEquals("/v1/product", invokeResolveBasePath(codegen, "Product"));
    }

    @Test
    void resolveBasePathDefaultsToNoPrefixWhenPrefixNeverSet() throws Exception {
        // additionalProperties() has neither ALLCRUD_BASE_PATH_OVERRIDES nor
        // ALLCRUD_BASE_PATH_PREFIX at all - AllcrudGenerator always sets the latter (even to ""),
        // so this only happens if AllcrudSpringCodegen is driven directly, bypassing
        // AllcrudGenerator entirely. Still real, reachable behavior worth locking down.
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();

        assertEquals("/product", invokeResolveBasePath(codegen, "Product"));
    }

    @Test
    void resolvePackageReturnsPerResourceOverrideWhenPresent() throws Exception {
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_PACKAGE_OVERRIDES,
                Map.of("Product", Map.of("POJO", "com.acme.catalog.dto")));
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_LAYER_PACKAGES,
                Map.of("POJO", "org.openapitools.model"));

        assertEquals("com.acme.catalog.dto", invokeResolvePackage(codegen, "Product", "POJO"));
    }

    @Test
    void resolvePackageFallsBackToGlobalWhenNoOverride() throws Exception {
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_LAYER_PACKAGES,
                Map.of("POJO", "org.openapitools.model"));

        assertEquals("org.openapitools.model", invokeResolvePackage(codegen, "Product", "POJO"));
    }

    @Test
    void resolvePackageReturnsNullWhenNeitherOverrideNorGlobalConfigured() throws Exception {
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        // Neither ALLCRUD_PACKAGE_OVERRIDES nor ALLCRUD_LAYER_PACKAGES set at all - this is what
        // AllcrudGenerator's own "No target package configured" fail-fast (see
        // AllcrudGeneratorInternalsCompatTest) ultimately protects against downstream.

        assertNull(invokeResolvePackage(codegen, "Product", "POJO"));
    }

    @Test
    void findResourceModelResolvesViaReturnTypeWhenItMatches() throws Exception {
        CodegenModel productModel = new CodegenModel();
        productModel.classname = "Product";
        Map<String, CodegenModel> modelsByClassname = Map.of("Product", productModel);

        CodegenOperation operation = new CodegenOperation();
        operation.returnBaseType = "Product";

        assertEquals(productModel, invokeFindResourceModel(operation, modelsByClassname));
    }

    @Test
    void findResourceModelFallsBackToBodyParamWhenReturnTypeDoesNotMatch() throws Exception {
        CodegenModel productModel = new CodegenModel();
        productModel.classname = "Product";
        // A plain HashMap, not Map.of(): operation.returnBaseType is null below (a real, valid
        // CodegenOperation shape - e.g. a response with no body), and findResourceModel's own
        // first lookup queries modelsByClassname with that null key before falling back to
        // bodyParam - Map.of()'s immutable maps reject a null key outright (NullPointerException),
        // unlike the real map this method is always handed in production.
        Map<String, CodegenModel> modelsByClassname = new HashMap<>();
        modelsByClassname.put("Product", productModel);

        CodegenOperation operation = new CodegenOperation();
        operation.returnBaseType = null;
        operation.bodyParam = new CodegenParameter();
        operation.bodyParam.baseType = "Product";

        assertEquals(productModel, invokeFindResourceModel(operation, modelsByClassname));
    }

    @Test
    void findResourceModelReturnsNullWhenNeitherReturnTypeNorBodyParamMatch() throws Exception {
        CodegenOperation operation = new CodegenOperation();
        operation.returnBaseType = "Unrelated";

        assertNull(invokeFindResourceModel(operation, Map.of()));
    }

    @Test
    void resolveEntityPackageCachesResultAcrossCallsForTheSameEntityName() throws Exception {
        Path sourceRoot = Files.createTempDirectory("allcrud-entity-package-cache");
        Path entityFile = sourceRoot.resolve("com/acme/entity/Product.java");
        Files.createDirectories(entityFile.getParent());
        Files.writeString(entityFile, "package com.acme.entity;\npublic class Product {}\n");

        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_SOURCE_ROOT, sourceRoot.toString());

        assertEquals("com.acme.entity", invokeResolveEntityPackage(codegen, "Product"));

        // Delete the entity file entirely before the second call - if resolveEntityPackage
        // actually re-scanned the filesystem instead of using entityPackageByEntityName's
        // cached value, this second call would now fail with "not found under...", not return
        // the same package again.
        Files.delete(entityFile);

        assertEquals("com.acme.entity", invokeResolveEntityPackage(codegen, "Product"));
    }

    @Test
    void resolveEntityPackageThrowsWhenSourceRootPropertyNeverSet() throws Exception {
        // AllcrudGenerator always sets ALLCRUD_SOURCE_ROOT before generation runs - this only
        // happens if AllcrudSpringCodegen is driven directly, bypassing AllcrudGenerator, same
        // "reachable, not a mocked failure" character as resolveBasePath's own no-prefix-set
        // test above.
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeResolveEntityPackage(codegen, "Product"));

        assertTrue(ex.getMessage().contains(AllcrudSpringCodegen.ALLCRUD_SOURCE_ROOT + " additional property not set"));
    }

    @Test
    void mainLoopSkipsOperationsThatDontResolveToAModelBeforeFindingOneThatDoes() throws Exception {
        // Two operations in one tag - the FIRST doesn't resolve to any model at all (no
        // returnBaseType, no bodyParam - e.g. a DELETE with a 204/no-content response), the
        // SECOND does. postProcessOperationsWithModels' own main loop breaks on the first
        // operation that successfully resolves - every real spec used elsewhere in this suite
        // happens to have its list-returning GET first, which always resolves immediately, so
        // this "skip a non-matching operation, then find one that matches" path never got
        // exercised. Calling postProcessOperationsWithModels directly (a public override, no
        // reflection needed) with a hand-built OperationsMap is what actually lets the FIRST
        // operation be the non-matching one, which no real fixture spec's operation ordering
        // produces.
        Path sourceRoot = Files.createTempDirectory("allcrud-main-loop-skip");
        Path entityFile = sourceRoot.resolve("com/acme/entity/Widget.java");
        Files.createDirectories(entityFile.getParent());
        Files.writeString(entityFile, "package com.acme.entity;\npublic class Widget {}\n");

        io.swagger.v3.oas.models.tags.Tag tag = new io.swagger.v3.oas.models.tags.Tag();
        tag.setName("widget");

        CodegenOperation nonMatching = new CodegenOperation();
        nonMatching.returnBaseType = null;
        nonMatching.tags = new ArrayList<>(List.of(tag));

        CodegenOperation matching = new CodegenOperation();
        matching.returnBaseType = "Widget";
        matching.vendorExtensions.put("x-allcrud-resource", Boolean.TRUE);
        matching.tags = new ArrayList<>(List.of(tag));

        OperationMap operationMap = new OperationMap();
        operationMap.setOperation(List.of(nonMatching, matching));
        operationMap.setClassname("WidgetApi");

        OperationsMap operationsMap = new OperationsMap();
        operationsMap.setOperation(operationMap);
        operationsMap.setImports(new ArrayList<>());

        CodegenProperty idProperty = new CodegenProperty();
        idProperty.baseName = "id";
        idProperty.dataType = "Long";

        CodegenModel widgetModel = new CodegenModel();
        widgetModel.schemaName = "Widget";
        widgetModel.classname = "Widget";
        widgetModel.allVars = new ArrayList<>(List.of(idProperty));

        ModelMap modelMap = new ModelMap();
        modelMap.setModel(widgetModel);

        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_SOURCE_ROOT, sourceRoot.toString());
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_LAYER_PACKAGES,
                Map.of("POJO", "org.openapitools.model", "REPOSITORY", "org.openapitools.api",
                        "CONVERTER", "org.openapitools.api", "SERVICE", "org.openapitools.api",
                        "CONTROLLER", "org.openapitools.api"));

        codegen.postProcessOperationsWithModels(operationsMap, List.of(modelMap));

        assertTrue(codegen.confirmedResourceNames().contains("Widget"),
                "Expected the second (matching) operation to still resolve Widget as a confirmed "
                        + "resource despite the first operation not matching anything");
    }

    private String invokeResolveEntityPackage(AllcrudSpringCodegen codegen, String entityName) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod("resolveEntityPackage", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(codegen, entityName);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw (RuntimeException) e.getCause();
        }
    }

    private String invokeResolveBasePath(AllcrudSpringCodegen codegen, String entityName) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod("resolveBasePath", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(codegen, entityName);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private String invokeResolvePackage(AllcrudSpringCodegen codegen, String entityName, String layerName) throws Exception {
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod("resolvePackage", String.class, String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(codegen, entityName, layerName);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private CodegenModel invokeFindResourceModel(CodegenOperation operation, Map<String, CodegenModel> modelsByClassname)
            throws Exception {
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        Method method = AllcrudSpringCodegen.class.getDeclaredMethod(
                "findResourceModel", CodegenOperation.class, Map.class);
        method.setAccessible(true);
        try {
            return (CodegenModel) method.invoke(codegen, operation, modelsByClassname);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

}
