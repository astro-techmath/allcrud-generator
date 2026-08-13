package com.techmath.allcrud.generator.codegen;

import com.techmath.allcrud.generator.IoExceptions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.languages.SpringCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Custom CodegenConfig for the "spring" generator, used instead of the stock one so
 * apiController.mustache/service.mustache/repository.mustache can reference the actual
 * resource entity name, its generated POJO (VO/DTO) class name, and its ID type - instead
 * of those being hardcoded string literals in the templates.
 *
 * Not registered via META-INF/services (SPI) - see
 * docs/notes/AllcrudSpringCodegen.md#codegenconfigloaderforname-fallback for why a
 * fully-qualified class name works as the "generator name" here (see AllcrudGenerator).
 */
public class AllcrudSpringCodegen extends SpringCodegen {

    public static final String ALLCRUD_ENTITY_NAME = "allcrudEntityName";
    public static final String ALLCRUD_POJO_CLASS_NAME = "allcrudPojoClassName";
    public static final String ALLCRUD_ID_TYPE = "allcrudIdType";
    public static final String ALLCRUD_POJO_NAMING_STYLE = "allcrudPojoNamingStyle";
    public static final String ALLCRUD_USE_DTO = "allcrudUseDto";
    public static final String ALLCRUD_BASE_PATH = "allcrudBasePath";
    // See docs/notes/AllcrudSpringCodegen.md#allcrud_base_path_prefixoverrides--read-per-operation-from-global-additionalproperties
    public static final String ALLCRUD_BASE_PATH_PREFIX = "allcrudBasePathPrefix";
    public static final String ALLCRUD_BASE_PATH_OVERRIDES = "allcrudBasePathOverrides";
    public static final String ALLCRUD_ENTITY_PACKAGE = "allcrudEntityPackage";
    // See docs/notes/AllcrudSpringCodegen.md#allcrud_source_root--threaded-through-since-the-entity-is-never-generated
    public static final String ALLCRUD_SOURCE_ROOT = "allcrudSourceRoot";
    // Target packages the templates actually read for the other 5 layers (see
    // docs/adr/0003-packages-global-with-resource-override.md for the global-vs-override
    // decision itself) - NEVER set as additionalProperties directly, only ever objs.put()
    // per-resource below, in postProcessOperationsWithModels. See
    // docs/notes/AllcrudGenerator.md#putall--defaultgeneratorgenerateapis-canonical--see-also-allcrudspringcodegen
    // for why. ALLCRUD_LAYER_PACKAGES below is where the actual global defaults travel instead.
    public static final String ALLCRUD_POJO_PACKAGE = "allcrudPojoPackage";
    public static final String ALLCRUD_REPOSITORY_PACKAGE = "allcrudRepositoryPackage";
    public static final String ALLCRUD_CONVERTER_PACKAGE = "allcrudConverterPackage";
    public static final String ALLCRUD_SERVICE_PACKAGE = "allcrudServicePackage";
    // See docs/notes/AllcrudSpringCodegen.md#allcrud_controller_package--added-for-integrationtestmustaches-import
    public static final String ALLCRUD_CONTROLLER_PACKAGE = "allcrudControllerPackage";
    // See docs/notes/AllcrudSpringCodegen.md#allcrud_layer_packages--safe-as-a-plain-additionalproperty
    public static final String ALLCRUD_LAYER_PACKAGES = "allcrudLayerPackages";
    // See docs/notes/AllcrudSpringCodegen.md#allcrud_package_overrides--per-resource-exception-to-allcrud_layer_packages
    public static final String ALLCRUD_PACKAGE_OVERRIDES = "allcrudPackageOverrides";
    // See docs/notes/AllcrudSpringCodegen.md#javanet-uri--javautil-import-gating-in-generated-pojos
    public static final String ALLCRUD_NEEDS_URI_IMPORT = "allcrudNeedsUriImport";
    public static final String ALLCRUD_NEEDS_JAVA_UTIL_IMPORT = "allcrudNeedsJavaUtilImport";

    private static final String X_ALLCRUD_ID_TYPE = "x-allcrud-id-type";
    private static final String X_ALLCRUD_RESOURCE = "x-allcrud-resource";
    // See docs/notes/AllcrudSpringCodegen.md#x-allcrud-auto-resource--document-root-vendor-extension-defaults-false
    private static final String X_ALLCRUD_AUTO_RESOURCE = "x-allcrud-auto-resource";
    private static final String DTO_STYLE = "DTO";
    private static final String VO_STYLE = "VO";

    // Populated by postProcessOperationsWithModels, consulted by toApiFilename - see
    // toApiFilename's javadoc for why this reuses that data instead of resolving the
    // entity name a second time from a different source.
    private final Map<String, String> entityNameByApiName = new HashMap<>();
    // See docs/notes/AllcrudSpringCodegen.md#entitypackagebyentityname--why-its-cached
    private final Map<String, String> entityPackageByEntityName = new HashMap<>();
    // See docs/notes/AllcrudSpringCodegen.md#confirmedresourcenames--real-output-vs-openapi-generator-scaffolding
    private final Set<String> confirmedResourceNames = new LinkedHashSet<>();

    public AllcrudSpringCodegen() {
        super();
        // See docs/notes/AllcrudSpringCodegen.md#apimustache-stock-template-is-dead-code--and-was-a-real-bug-not-just-cleanup
        apiTemplateFiles.remove("api.mustache");
    }

    /**
     * x-allcrud-auto-resource: true (document root) turns on automatic resource inference:
     * every path pair matching the CrudController shape gets x-allcrud-resource: true
     * injected as if a human had written it, UNLESS that path already carries an explicit
     * x-allcrud-resource (true or false) - explicit always wins over inferred, same as every
     * other override in this project. Absent/false changes nothing (today's behavior:
     * every resource needs x-allcrud-resource: true by hand).
     *
     * Runs in preprocessOpenAPI, not postProcessOperationsWithModels, and works off the raw
     * OpenAPI model (PathItem/Operation), not CodegenOperation - deliberately, not just
     * "earlier because it has to be" (contrast with allcrudEntityName's resolution, forced
     * later because operation.getTags() is null this early). Correlating collection+item
     * paths by literal path string here is actually MORE robust than doing it later by tag:
     * tag-based grouping only puts a resource's operations together because nothing in this
     * project's specs sets an explicit "tags:" - if a spec ever did, collection and item
     * operations could land in different tags and a per-tag scan would miss the pair
     * entirely. Path-string correlation has no such dependency.
     *
     * See docs/notes/AllcrudSpringCodegen.md#preprocessopenapi-runs-before-setopenapi-same-openapi-instance
     * for why extensions written here are visible later to fromOperation's backfill.
     *
     * Deliberately does NOT check for an "id" property on the candidate schema (unlike
     * findIdProperty, used later) - a path pair matching this exact shape is either a real
     * CRUD resource (falls through to the existing id-presence fail-fast in
     * postProcessOperationsWithModels if it turns out to have no "id", which is the correct,
     * loud outcome) or such a strange coincidence that failing loud is still the right
     * outcome. Checking "id" here too would mean re-walking allOf/inheritance on a raw
     * Schema, duplicating - less reliably - what findIdProperty already does correctly on
     * the fully-resolved CodegenModel later.
     */
    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        inferAllcrudResources(openAPI);
    }

    private void inferAllcrudResources(OpenAPI openAPI) {
        if (!Boolean.TRUE.equals(rootExtension(openAPI, X_ALLCRUD_AUTO_RESOURCE))) {
            return;
        }
        Map<String, PathItem> paths = openAPI.getPaths();
        if (paths == null) {
            return;
        }

        for (Entry<String, PathItem> collectionEntry : paths.entrySet()) {
            String collectionPath = collectionEntry.getKey();
            PathItem collectionPathItem = collectionEntry.getValue();
            // hasPathParam short-circuits the ternary, same as the original guard - never calls
            // resolveListItemSchemaName for a path with a param, matching prior behavior exactly.
            String schemaName = hasPathParam(collectionPath)
                    ? null
                    : resolveListItemSchemaName(openAPI, collectionPathItem.getGet());
            if (schemaName == null) {
                continue;
            }

            for (Entry<String, PathItem> itemEntry : paths.entrySet()) {
                String itemPath = itemEntry.getKey();
                PathItem itemPathItem = itemEntry.getValue();
                // && short-circuits, same as the original 2 guards - never calls referencesSchema
                // unless isItemPathOf already matched, matching prior behavior exactly.
                boolean matchesCollectionSchema = isItemPathOf(collectionPath, itemPath)
                        && referencesSchema(openAPI, itemPathItem, schemaName);
                if (!matchesCollectionSchema) {
                    continue;
                }

                markInferredResource(collectionPathItem);
                markInferredResource(itemPathItem);
            }
        }
    }

    private Object rootExtension(OpenAPI openAPI, String key) {
        return openAPI.getExtensions() != null ? openAPI.getExtensions().get(key) : null;
    }

    private boolean hasPathParam(String path) {
        return path.contains("{");
    }

    // See docs/notes/AllcrudSpringCodegen.md#isitempathof--path-matching-rule-for-auto-resource-inference
    private boolean isItemPathOf(String collectionPath, String candidatePath) {
        if (candidatePath.length() <= collectionPath.length() || !candidatePath.startsWith(collectionPath)) {
            return false;
        }
        String suffix = candidatePath.substring(collectionPath.length());
        return suffix.matches("/\\{[^/{}]+}");
    }

    // See docs/notes/AllcrudSpringCodegen.md#resolvelistitemschemaname--post-not-required-approved-design-decision
    private String resolveListItemSchemaName(OpenAPI openAPI, Operation getOperation) {
        if (getOperation == null) {
            return null;
        }
        Schema<?> responseSchema = firstSuccessResponseSchema(openAPI, getOperation);
        if (!ModelUtils.isArraySchema(responseSchema)) {
            return null;
        }
        return schemaRefName(ModelUtils.getSchemaItems(responseSchema));
    }

    // Item path signal: at least one of GET/PUT/PATCH/DELETE on this path references the same
    // named schema directly (not array-wrapped) - either as its request body or its success
    // response.
    private boolean referencesSchema(OpenAPI openAPI, PathItem itemPathItem, String schemaName) {
        // See docs/notes/AllcrudSpringCodegen.md#referencesschema--array-instead-of-listof-for-candidates
        Operation[] candidates = {
                itemPathItem.getGet(), itemPathItem.getPut(), itemPathItem.getPatch(), itemPathItem.getDelete()};
        for (Operation operation : candidates) {
            if (operation == null) {
                continue;
            }
            if (schemaName.equals(schemaRefName(firstSuccessResponseSchema(openAPI, operation)))) {
                return true;
            }
            if (operation.getRequestBody() != null) {
                RequestBody requestBody = ModelUtils.getReferencedRequestBody(openAPI, operation.getRequestBody());
                Schema<?> requestSchema = requestBody != null ? ModelUtils.getSchemaFromRequestBody(requestBody) : null;
                if (schemaName.equals(schemaRefName(requestSchema))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Schema<?> firstSuccessResponseSchema(OpenAPI openAPI, Operation operation) {
        if (operation.getResponses() == null) {
            return null;
        }
        for (Entry<String, ApiResponse> entry : operation.getResponses().entrySet()) {
            if (entry.getKey().startsWith("2")) {
                return ModelUtils.getSchemaFromResponse(openAPI, entry.getValue());
            }
        }
        return null;
    }

    // See docs/notes/AllcrudSpringCodegen.md#schemarefname--only-refd-schemas-count
    private String schemaRefName(Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return null;
        }
        return ModelUtils.getSimpleRef(schema.get$ref());
    }

    private void markInferredResource(PathItem pathItem) {
        Map<String, Object> extensions = pathItem.getExtensions();
        if (extensions == null) {
            extensions = new LinkedHashMap<>();
            pathItem.setExtensions(extensions);
        }
        extensions.putIfAbsent(X_ALLCRUD_RESOURCE, Boolean.TRUE);
    }

    /**
     * Reads the single allcrudPojoNamingStyle additional property ("VO", default, or "DTO")
     * and derives from it both modelNameSuffix (so generated POJOs are named ProductVO or
     * ProductDTO) and allcrudUseDto (a plain boolean, since Mustache sections can't do
     * string equality - see model.mustache/pojo.mustache for how it's consumed). This is
     * the one knob the caller sets; setModelNameSuffix doesn't need to be called separately.
     *
     * AbstractEntityDTO extends AbstractEntityVO (confirmed in the core: empty marker
     * interface, functionally identical) - which is why this switch only needed to touch
     * model.mustache/pojo.mustache. apiController.mustache/service.mustache/
     * repository.mustache/converter.mustache already reference the POJO purely via
     * allcrudPojoClassName (resolved from the actual generated CodegenModel#classname, see
     * postProcessOperationsWithModels below), so they pick up "ProductDTO" automatically
     * without needing to know this switch exists.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        Object rawStyle = additionalProperties().get(ALLCRUD_POJO_NAMING_STYLE);
        String namingStyle = rawStyle != null ? rawStyle.toString() : VO_STYLE;
        boolean useDto = DTO_STYLE.equalsIgnoreCase(namingStyle);

        setModelNameSuffix(useDto ? DTO_STYLE : VO_STYLE);
        additionalProperties().put(ALLCRUD_USE_DTO, useDto);
    }

    /**
     * Resolves each model's own ID type into its vendorExtensions map (ALLCRUD_ID_TYPE), so
     * pojo.mustache doesn't have to hardcode "Long" - see
     * docs/notes/AllcrudSpringCodegen.md#pojomustache-hardcoded-id-type-long--real-bug-not-a-hypothetical
     * for the bug this fixes and why vendorExtensions is how it's threaded through.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        objs = super.postProcessModels(objs);

        boolean needsUriImport = false;
        boolean needsJavaUtilImport = false;

        for (ModelMap modelMap : objs.getModels()) {
            CodegenModel model = modelMap.getModel();
            CodegenProperty idProperty = findIdProperty(model);
            if (idProperty != null) {
                model.vendorExtensions.put(ALLCRUD_ID_TYPE, idProperty.dataType);
            }
            for (CodegenProperty property : model.vars) {
                needsUriImport = needsUriImport || usesUri(property);
                needsJavaUtilImport = needsJavaUtilImport || property.isContainer || property.isByteArray;
            }
        }

        objs.put(ALLCRUD_NEEDS_URI_IMPORT, needsUriImport);
        objs.put(ALLCRUD_NEEDS_JAVA_UTIL_IMPORT, needsJavaUtilImport);

        return objs;
    }

    private boolean usesUri(CodegenProperty property) {
        if ("URI".equals(property.dataType)) {
            return true;
        }
        return property.items != null && "URI".equals(property.items.dataType);
    }

    /**
     * See docs/notes/AllcrudSpringCodegen.md#fromoperation-only-copies-vendor-extensions-from-the-operation-level-never-path
     * for why this override exists and backfills path-level vendor extensions.
     */
    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);

        PathItem pathItem = this.openAPI.getPaths().get(path);
        if (pathItem != null && pathItem.getExtensions() != null) {
            for (Map.Entry<String, Object> pathExtension : pathItem.getExtensions().entrySet()) {
                op.vendorExtensions.putIfAbsent(pathExtension.getKey(), pathExtension.getValue());
            }
        }

        return op;
    }

    /**
     * Resolves, once per tag/operations-group, the bare entity name ("Product"), the
     * generated POJO class name ("ProductVO" today, "ProductDTO" once the VO/DTO switch
     * exists), and the ID type ("Long") - by finding, among this tag's operations, the one
     * whose response/request model has a property named "id", and reading that model's
     * schemaName/classname and that property's dataType. This is the same "walk the actual
     * OpenAPI model instead of guessing from a string suffix" approach used to avoid the
     * fragile "strip VO from ProductPageVO" problem identified earlier: a model without an
     * "id" property (like the paginated ProductPageVO wrapper) is never picked.
     *
     * x-allcrud-id-type (read from CodegenOperation#vendorExtensions, backfilled from the
     * path by fromOperation above) is checked first, as a rare manual override for cases
     * where the "id" property isn't present or isn't a reliable signal - it is not the main
     * mechanism.
     */
    // java:S3776 (Cognitive Complexity) and java:S135 (multiple break/continue in the main loop
    // below) both flag this method - already refused twice, on purpose, not an oversight:
    // the main loop has 2 continue + 2 break intertwined with real side effects (resolved,
    // confirmedResourceNames, several objs.put calls, entityNameByApiName) built up across
    // iterations. Collapsing it to a single exit point would mean extracting a per-operation
    // helper returning some multi-value result/sentinel just to thread those side effects back
    // out in the right order - real behavior-change risk in the method this project's test
    // suite spent the most effort hardening (see EntityResolutionCompatTest,
    // AllcrudSpringCodegenInternalsCompatTest's postProcessOperationsWithModels* tests). The
    // metric improvement isn't worth that risk.
    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) { // NOSONAR java:S3776
        objs = super.postProcessOperationsWithModels(objs, allModels);

        Map<String, CodegenModel> modelsByClassname = toModelsByClassname(allModels);
        OperationMap operations = objs.getOperations();

        String idTypeOverride = null;
        boolean isAllcrudResource = false;
        for (CodegenOperation operation : operations.getOperation()) {
            Object override = operation.vendorExtensions.get(X_ALLCRUD_ID_TYPE);
            if (override != null) {
                idTypeOverride = override.toString();
            }
            if (isAllcrudResource(operation)) {
                isAllcrudResource = true;
            }
        }

        CodegenModel firstResourceModelWithoutId = null;
        boolean resolved = false;

        // 2 continue + 2 break, deliberately - see the NOSONAR java:S3776 comment on this
        // method's own declaration above for why.
        for (CodegenOperation operation : operations.getOperation()) { // NOSONAR java:S135
            CodegenModel resourceModel = findResourceModel(operation, modelsByClassname);
            if (resourceModel == null) {
                continue;
            }

            CodegenProperty idProperty = findIdProperty(resourceModel);
            if (idProperty == null && idTypeOverride == null) {
                if (firstResourceModelWithoutId == null) {
                    firstResourceModelWithoutId = resourceModel;
                }
                continue;
            }

            // See docs/notes/AllcrudSpringCodegen.md#postprocessoperationswithmodels--early-break-on-non-resource-tag
            if (!isAllcrudResource) {
                break;
            }

            resolved = true;
            confirmedResourceNames.add(resourceModel.schemaName);
            objs.put(ALLCRUD_ENTITY_NAME, resourceModel.schemaName);
            objs.put(ALLCRUD_POJO_CLASS_NAME, resourceModel.classname);
            objs.put(ALLCRUD_ID_TYPE, idTypeOverride != null ? idTypeOverride : idProperty.dataType);
            objs.put(ALLCRUD_BASE_PATH, resolveBasePath(resourceModel.schemaName));
            objs.put(ALLCRUD_ENTITY_PACKAGE, resolveEntityPackage(resourceModel.schemaName));
            objs.put(ALLCRUD_POJO_PACKAGE, resolvePackage(resourceModel.schemaName, "POJO"));
            objs.put(ALLCRUD_REPOSITORY_PACKAGE, resolvePackage(resourceModel.schemaName, "REPOSITORY"));
            objs.put(ALLCRUD_CONVERTER_PACKAGE, resolvePackage(resourceModel.schemaName, "CONVERTER"));
            objs.put(ALLCRUD_SERVICE_PACKAGE, resolvePackage(resourceModel.schemaName, "SERVICE"));
            objs.put(ALLCRUD_CONTROLLER_PACKAGE, resolvePackage(resourceModel.schemaName, "CONTROLLER"));
            entityNameByApiName.put(operations.getClassname(), resourceModel.schemaName);
            break;
        }

        // See docs/adr/0002-x-allcrud-resource-gate-and-inference.md for why this fails fast,
        // and docs/notes/AllcrudSpringCodegen.md#fail-fast-on-missing-id--why-silent-fallthrough-was-worse
        // for what silently fell through before this check existed.
        if (!resolved && isAllcrudResource && firstResourceModelWithoutId != null) {
            throw new IllegalStateException(
                    "Resource \"" + firstResourceModelWithoutId.schemaName + "\" is marked "
                            + "x-allcrud-resource but has no \"id\" property. CrudController requires "
                            + "an ID type. Add an \"id\" property to the schema, or remove "
                            + "x-allcrud-resource if this resource shouldn't be generated.");
        }

        return objs;
    }

    // See docs/notes/AllcrudSpringCodegen.md#confirmedresourcenames--read-by-allcrudgeneratorrelocate
    public Set<String> confirmedResourceNames() {
        return Set.copyOf(confirmedResourceNames);
    }

    // See docs/notes/AllcrudSpringCodegen.md#isallcrudresource--why-its-checked-per-operation-not-assumed-true
    private boolean isAllcrudResource(CodegenOperation operation) {
        Object value = operation.vendorExtensions.get(X_ALLCRUD_RESOURCE);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * See docs/notes/AllcrudSpringCodegen.md#toapifilename--cache-reuse-depends-on-defaultgenerators-real-execution-order
     * for why this overrides the default file name and why reusing entityNameByApiName
     * (instead of re-resolving the entity name) is safe given DefaultGenerator's real
     * execution order.
     */
    @Override
    public String toApiFilename(String name) {
        String entityName = entityNameByApiName.get(toApiName(name));
        return entityName != null ? entityName : super.toApiFilename(name);
    }

    private Map<String, CodegenModel> toModelsByClassname(List<ModelMap> allModels) {
        Map<String, CodegenModel> modelsByClassname = new HashMap<>();
        for (ModelMap modelMap : allModels) {
            CodegenModel model = modelMap.getModel();
            modelsByClassname.put(model.classname, model);
        }
        return modelsByClassname;
    }

    private CodegenModel findResourceModel(CodegenOperation operation, Map<String, CodegenModel> modelsByClassname) {
        CodegenModel fromReturnType = modelsByClassname.get(operation.returnBaseType);
        if (fromReturnType != null) {
            return fromReturnType;
        }
        if (operation.bodyParam != null) {
            return modelsByClassname.get(operation.bodyParam.baseType);
        }
        return null;
    }

    // See docs/notes/AllcrudSpringCodegen.md#resolvebasepath--the-actual-formula
    @SuppressWarnings("unchecked")
    private String resolveBasePath(String entityName) {
        Object overridesValue = additionalProperties().get(ALLCRUD_BASE_PATH_OVERRIDES);
        if (overridesValue instanceof Map<?, ?> overrides) {
            Object override = ((Map<String, Object>) overrides).get(entityName);
            if (override != null) {
                return override.toString();
            }
        }
        Object prefixValue = additionalProperties().get(ALLCRUD_BASE_PATH_PREFIX);
        String prefix = prefixValue != null ? prefixValue.toString() : "";
        return prefix + "/" + entityName.toLowerCase(Locale.ROOT);
    }

    // See docs/notes/AllcrudSpringCodegen.md#resolvepackage--override-wins-outright-never-merges
    @SuppressWarnings("unchecked")
    private String resolvePackage(String entityName, String layerName) {
        Object overridesValue = additionalProperties().get(ALLCRUD_PACKAGE_OVERRIDES);
        if (overridesValue instanceof Map<?, ?> overridesByResource) {
            Object layerOverrides = ((Map<String, Object>) overridesByResource).get(entityName);
            if (layerOverrides instanceof Map<?, ?> layerOverridesMap) {
                Object override = ((Map<String, Object>) layerOverridesMap).get(layerName);
                if (override != null) {
                    return override.toString();
                }
            }
        }
        Object layerPackagesValue = additionalProperties().get(ALLCRUD_LAYER_PACKAGES);
        if (layerPackagesValue instanceof Map<?, ?> layerPackages) {
            Object globalValue = ((Map<String, Object>) layerPackages).get(layerName);
            return globalValue != null ? globalValue.toString() : null;
        }
        return null;
    }

    // See docs/adr/0006-entity-out-of-scope-v1.md - its package can only be discovered by
    // looking at what the consumer actually wrote: scan sourceRoot for a file literally named
    // "<entityName>.java" and parse its "package ...;" declaration - line 1, same technique
    // already proven safe for rewriting that line in generated files
    // (AllcrudGenerator#rewritePackageStatement), applied here in reverse (reading instead of
    // writing).
    //
    // See docs/notes/AllcrudSpringCodegen.md#why-reflection-was-rejected-for-resolving-the-entitys-package.
    //
    // Fails loudly, not silently, on both failure modes: zero matches (the consumer hasn't
    // written the entity yet) and more than one match (ambiguous - which one is "the" entity
    // package?) - picking the first match silently in the ambiguous case would be exactly the
    // kind of silent-wrong-behavior this project has repeatedly decided against elsewhere
    // (GlobalSettings presence-vs-value, the old @RequestMapping property placeholder).
    private String resolveEntityPackage(String entityName) {
        String cached = entityPackageByEntityName.get(entityName);
        if (cached != null) {
            return cached;
        }

        Object sourceRootValue = additionalProperties().get(ALLCRUD_SOURCE_ROOT);
        if (sourceRootValue == null) {
            throw new IllegalStateException(ALLCRUD_SOURCE_ROOT + " additional property not set - "
                    + "AllcrudGenerator must pass it so the entity's package can be resolved");
        }
        Path sourceRoot = Path.of(sourceRootValue.toString());
        String targetFileName = entityName + ".java";

        List<Path> matches = Files.isDirectory(sourceRoot)
                ? IoExceptions.listRegularFilesRecursively(sourceRoot).stream()
                        .filter(path -> path.getFileName().toString().equals(targetFileName))
                        .toList()
                : List.of();

        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    targetFileName + " not found under " + sourceRoot
                            + " - create the entity before running the generator");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Found more than one " + targetFileName + " under " + sourceRoot + ": " + matches
                            + " - ambiguous, which one is the entity for this resource?");
        }

        String entityPackage = readPackageStatement(matches.get(0));
        entityPackageByEntityName.put(entityName, entityPackage);
        return entityPackage;
    }

    private String readPackageStatement(Path javaFile) {
        String content = IoExceptions.readFile(javaFile);
        int newlineIndex = content.indexOf('\n');
        String firstLine = (newlineIndex == -1 ? content : content.substring(0, newlineIndex)).trim();
        if (!firstLine.startsWith("package ") || !firstLine.endsWith(";")) {
            throw new IllegalStateException(
                    "Expected first line of " + javaFile + " to be a package statement, found: " + firstLine);
        }
        return firstLine.substring("package ".length(), firstLine.length() - 1).trim();
    }

    private CodegenProperty findIdProperty(CodegenModel model) {
        for (CodegenProperty property : model.allVars) {
            if ("id".equals(property.baseName)) {
                return property;
            }
        }
        return null;
    }

}
