package com.techmath.allcrud.generator.codegen;

import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
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
    void postProcessModelsAccumulatesUriAndJavaUtilImportFlagsAcrossVars() {
        // needsUriImport/needsJavaUtilImport are single accumulators across every property of
        // every model postProcessModels sees (declared once, outside the models loop) - every
        // real spec fixture used elsewhere in this suite either has no URI/container/byte[]
        // property at all, or has one in isolation, so several combinations of
        // "was the accumulator already true when this property was evaluated" never got
        // exercised. Property order here is deliberate: plain (both flags false,false) is
        // first, then isByteArray (needsJavaUtilImport still false when evaluated - covers its
        // own false-then-true transition), then a non-URI container (needsJavaUtilImport
        // already true - covers the short-circuit branch of that OR chain, and covers usesUri's
        // own "items present but not URI" case), then a direct URI scalar (needsUriImport's own
        // false-then-true transition), then one more plain property (needsUriImport already
        // true - covers ITS short-circuit branch too).
        CodegenProperty plainBefore = new CodegenProperty();
        plainBefore.dataType = "String";

        CodegenProperty byteArrayProperty = new CodegenProperty();
        byteArrayProperty.dataType = "byte[]";
        byteArrayProperty.isByteArray = true;

        CodegenProperty nonUriContainer = new CodegenProperty();
        nonUriContainer.dataType = "List";
        nonUriContainer.isContainer = true;
        nonUriContainer.items = new CodegenProperty();
        nonUriContainer.items.dataType = "String";

        CodegenProperty uriScalar = new CodegenProperty();
        uriScalar.dataType = "URI";

        CodegenProperty plainAfter = new CodegenProperty();
        plainAfter.dataType = "String";

        CodegenModel model = new CodegenModel();
        model.allVars = new ArrayList<>();
        model.vars = List.of(plainBefore, byteArrayProperty, nonUriContainer, uriScalar, plainAfter);

        ModelMap modelMap = new ModelMap();
        modelMap.setModel(model);

        ModelsMap modelsMap = new ModelsMap();
        modelsMap.setModels(List.of(modelMap));

        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        ModelsMap result = codegen.postProcessModels(modelsMap);

        assertEquals(Boolean.TRUE, result.get(AllcrudSpringCodegen.ALLCRUD_NEEDS_URI_IMPORT));
        assertEquals(Boolean.TRUE, result.get(AllcrudSpringCodegen.ALLCRUD_NEEDS_JAVA_UTIL_IMPORT));
    }

    @Test
    void postProcessModelsSetsJavaUtilImportFlagFromContainerPropertyAlone() {
        // needsJavaUtilImport's OR chain is "accumulator || isContainer || isByteArray" - the
        // test above always reaches isContainer with the accumulator already true (from an
        // earlier isByteArray property), so isContainer's own true outcome, evaluated while the
        // accumulator is still false, never got exercised on its own. A fresh
        // postProcessModels call (fresh accumulator) with a container property first is the
        // only way to isolate it, since the accumulator persists across every property once set.
        CodegenProperty nonUriContainer = new CodegenProperty();
        nonUriContainer.dataType = "List";
        nonUriContainer.isContainer = true;

        CodegenModel model = new CodegenModel();
        model.allVars = new ArrayList<>();
        model.vars = List.of(nonUriContainer);

        ModelMap modelMap = new ModelMap();
        modelMap.setModel(model);

        ModelsMap modelsMap = new ModelsMap();
        modelsMap.setModels(List.of(modelMap));

        ModelsMap result = new AllcrudSpringCodegen().postProcessModels(modelsMap);

        assertEquals(Boolean.TRUE, result.get(AllcrudSpringCodegen.ALLCRUD_NEEDS_JAVA_UTIL_IMPORT));
    }

    @Test
    void processOptsDefaultsToVoStyleWhenPojoNamingStylePropertyNeverSet() {
        // AllcrudGenerator always sets ALLCRUD_POJO_NAMING_STYLE to either "VO" or "DTO" before
        // calling processOpts() - every real generation run exercises the non-null ternary
        // branch either way, never the null-defaults-to-VO_STYLE one. Same "only reachable by
        // driving AllcrudSpringCodegen directly" character as resolveBasePathDefaultsToNoPrefixWhenPrefixNeverSet
        // above.
        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();

        codegen.processOpts();

        assertEquals(Boolean.FALSE, codegen.additionalProperties().get(AllcrudSpringCodegen.ALLCRUD_USE_DTO));
    }

    @Test
    void resolveEntityPackageThrowsWhenSourceRootIsNotADirectory() throws Exception {
        // Every other resolveEntityPackage test points ALLCRUD_SOURCE_ROOT at a real directory -
        // Files.isDirectory(sourceRoot)'s false branch (a path that doesn't exist, or exists but
        // isn't a directory) never got exercised, only its true branch. Falls through to the same
        // "not found under" message as the missing-file case, via a different code path
        // (List.of() short-circuit instead of an empty filtered stream).
        Path notADirectory = Files.createTempFile("allcrud-source-root-not-a-directory", ".txt");

        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_SOURCE_ROOT, notADirectory.toString());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeResolveEntityPackage(codegen, "Product"));

        assertTrue(ex.getMessage().contains("not found under"),
                "Expected the not-found message, was: " + ex.getMessage());
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

    @Test
    void postProcessOperationsWithModelsResolvesIdTypeFromOverrideWhenModelHasNoIdProperty() {
        // IdTypeOverrideCompatTest's Widget fixture has a real "id" property AND an override -
        // the override there just wins over the schema-derived type, never testing the override
        // as the ONLY source of the ID type. idProperty==null && idTypeOverride==null is covered
        // by every plain no-id fail-fast test; idProperty==null && idTypeOverride!=null (the
        // override alone compensating for a genuinely id-less model, so the fail-fast must NOT
        // fire) was never exercised on its own.
        Path sourceRoot = createTempDirWithEntity("Gadget");

        io.swagger.v3.oas.models.tags.Tag tag = new io.swagger.v3.oas.models.tags.Tag();
        tag.setName("gadget");

        CodegenOperation operation = new CodegenOperation();
        operation.returnBaseType = "Gadget";
        operation.tags = new ArrayList<>(List.of(tag));
        operation.vendorExtensions.put("x-allcrud-resource", Boolean.TRUE);
        operation.vendorExtensions.put("x-allcrud-id-type", "java.util.UUID");

        OperationMap operationMap = new OperationMap();
        operationMap.setOperation(List.of(operation));
        operationMap.setClassname("GadgetApi");

        OperationsMap operationsMap = new OperationsMap();
        operationsMap.setOperation(operationMap);
        operationsMap.setImports(new ArrayList<>());

        CodegenModel gadgetModel = new CodegenModel();
        gadgetModel.schemaName = "Gadget";
        gadgetModel.classname = "Gadget";
        gadgetModel.allVars = new ArrayList<>();

        ModelMap modelMap = new ModelMap();
        modelMap.setModel(gadgetModel);

        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_SOURCE_ROOT, sourceRoot.toString());
        codegen.additionalProperties().put(AllcrudSpringCodegen.ALLCRUD_LAYER_PACKAGES,
                Map.of("POJO", "org.openapitools.model", "REPOSITORY", "org.openapitools.api",
                        "CONVERTER", "org.openapitools.api", "SERVICE", "org.openapitools.api",
                        "CONTROLLER", "org.openapitools.api"));

        OperationsMap result = codegen.postProcessOperationsWithModels(operationsMap, List.of(modelMap));

        assertEquals("java.util.UUID", result.get(AllcrudSpringCodegen.ALLCRUD_ID_TYPE));
        assertTrue(codegen.confirmedResourceNames().contains("Gadget"));
    }

    @Test
    void postProcessOperationsWithModelsDoesNotThrowWhenMarkedResourceNeverResolvesToAnyModel() {
        // The no-id fail-fast's guard is "!resolved && isAllcrudResource &&
        // firstResourceModelWithoutId != null" - every existing no-id test has
        // firstResourceModelWithoutId end up non-null (that's what makes the fail-fast fire).
        // Its own false outcome - a tag explicitly marked x-allcrud-resource but whose operation
        // never resolves to ANY model at all (no matching returnBaseType or bodyParam - e.g. a
        // DELETE with a 204/no-content response), so the main loop's own "continue" at
        // findResourceModel==null fires before firstResourceModelWithoutId is ever touched -
        // never got exercised: isAllcrudResource is set by a separate loop over the same
        // operations, independent of whether any of them actually resolves to a model.
        io.swagger.v3.oas.models.tags.Tag tag = new io.swagger.v3.oas.models.tags.Tag();
        tag.setName("widget");

        CodegenOperation operation = new CodegenOperation();
        operation.returnBaseType = null;
        operation.tags = new ArrayList<>(List.of(tag));
        operation.vendorExtensions.put("x-allcrud-resource", Boolean.TRUE);

        OperationMap operationMap = new OperationMap();
        operationMap.setOperation(List.of(operation));
        operationMap.setClassname("WidgetApi");

        OperationsMap operationsMap = new OperationsMap();
        operationsMap.setOperation(operationMap);
        operationsMap.setImports(new ArrayList<>());

        AllcrudSpringCodegen codegen = new AllcrudSpringCodegen();

        OperationsMap result = codegen.postProcessOperationsWithModels(operationsMap, List.of());

        assertTrue(codegen.confirmedResourceNames().isEmpty(),
                "No operation resolved to a model at all - nothing should be confirmed");
        assertNull(result.get(AllcrudSpringCodegen.ALLCRUD_ID_TYPE));
    }

    @Test
    void isAllcrudResourceReturnsTrueForStringValueRegardlessOfCase() throws Exception {
        // markInferredResource always sets Boolean.TRUE, and a plain "x-allcrud-resource: true"
        // in YAML is parsed as a real Boolean by SnakeYAML - the String equalsIgnoreCase fallback
        // (for a quoted "x-allcrud-resource: \"true\"") never got exercised by any real fixture.
        CodegenOperation operation = new CodegenOperation();
        operation.vendorExtensions.put("x-allcrud-resource", "TRUE");

        Method method = AllcrudSpringCodegen.class.getDeclaredMethod("isAllcrudResource", CodegenOperation.class);
        method.setAccessible(true);

        assertEquals(Boolean.TRUE, method.invoke(new AllcrudSpringCodegen(), operation));
    }

    private Path createTempDirWithEntity(String entityName) {
        try {
            Path sourceRoot = Files.createTempDirectory("allcrud-codegen-internals-" + entityName.toLowerCase());
            Path entityFile = sourceRoot.resolve("org/openapitools/entity/" + entityName + ".java");
            Files.createDirectories(entityFile.getParent());
            Files.writeString(entityFile, "package org.openapitools.entity;\npublic class " + entityName + " {}\n");
            return sourceRoot;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
