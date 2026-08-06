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
 * Not registered via META-INF/services (SPI): CodegenConfigLoader#forName falls back to
 * Class.forName(name).newInstance() when no SPI provider matches the given name, so the
 * generation driver just passes this class's fully-qualified name as the "generator name"
 * (see AllcrudGenerator).
 */
public class AllcrudSpringCodegen extends SpringCodegen {

    public static final String ALLCRUD_ENTITY_NAME = "allcrudEntityName";
    public static final String ALLCRUD_POJO_CLASS_NAME = "allcrudPojoClassName";
    public static final String ALLCRUD_ID_TYPE = "allcrudIdType";
    public static final String ALLCRUD_POJO_NAMING_STYLE = "allcrudPojoNamingStyle";
    public static final String ALLCRUD_USE_DTO = "allcrudUseDto";
    public static final String ALLCRUD_BASE_PATH = "allcrudBasePath";
    // Additional properties AllcrudGenerator sets globally for the whole run (see
    // GenerationRequest#basePathPrefix / ResourceOverride#basePath) - read here per-operation
    // to resolve ALLCRUD_BASE_PATH. Not exposed as vendor extensions like x-allcrud-id-type:
    // these come from allcrud-generator.yml, not the OpenAPI spec itself.
    public static final String ALLCRUD_BASE_PATH_PREFIX = "allcrudBasePathPrefix";
    public static final String ALLCRUD_BASE_PATH_OVERRIDES = "allcrudBasePathOverrides";
    public static final String ALLCRUD_ENTITY_PACKAGE = "allcrudEntityPackage";
    // AllcrudGenerator#sourceRoot, passed through so postProcessOperationsWithModels can scan
    // it for <entityName>.java (see resolveEntityPackage) - the Entity itself is never
    // generated, so its package can't be resolved any other way (no "packages.entity" key in
    // allcrud-generator.yml either - rejected: doesn't support entities living in different
    // packages per module/domain, e.g. com.acme.catalog.Product vs com.acme.sales.Order).
    public static final String ALLCRUD_SOURCE_ROOT = "allcrudSourceRoot";
    // Target packages the templates actually read for the other 5 layers - NEVER set as
    // additionalProperties directly (unlike allcrudEntityName/allcrudBasePath's siblings,
    // that's the point): only ever objs.put() per-resource below, in
    // postProcessOperationsWithModels. Setting these as additionalProperties was tried first and
    // was wrong - DefaultGenerator#generateApis calls
    // operation.putAll(config.additionalProperties()) AFTER postProcessOperationsWithModels
    // already ran for that operation (confirmed by reading DefaultGenerator's source), so any
    // per-resource value put here under one of these exact keys got silently clobbered right
    // back to the global default by that putAll. See ALLCRUD_LAYER_PACKAGES below for where the
    // actual global defaults now travel instead.
    public static final String ALLCRUD_POJO_PACKAGE = "allcrudPojoPackage";
    public static final String ALLCRUD_REPOSITORY_PACKAGE = "allcrudRepositoryPackage";
    public static final String ALLCRUD_CONVERTER_PACKAGE = "allcrudConverterPackage";
    public static final String ALLCRUD_SERVICE_PACKAGE = "allcrudServicePackage";
    // Added for integrationTest.mustache, which imports the generated Controller class - until
    // now nothing else ever imported "the Controller" from another layer, so this was
    // deliberately left out (see the old comment on AllcrudGenerator#layerPackages, now stale).
    public static final String ALLCRUD_CONTROLLER_PACKAGE = "allcrudControllerPackage";
    // layer name -> global package (AllcrudGenerator#layerPackages) - safe to set as a plain
    // additionalProperty (unlike the 4 above) because no template reads "allcrudLayerPackages"
    // directly; only resolvePackage below does, as the fallback when a resource has no override.
    public static final String ALLCRUD_LAYER_PACKAGES = "allcrudLayerPackages";
    // resourceName -> layer name -> package (ResourceOverride#packageOverrides, see
    // AllcrudGenerator#packageOverrides) - a per-resource exception to ALLCRUD_LAYER_PACKAGES.
    // Resolved per-resource in postProcessOperationsWithModels (resolvePackage) - without this, a
    // Controller/Service/Converter importing another layer's class would always import the
    // GLOBAL package even when that resource's layer was relocated somewhere else entirely.
    public static final String ALLCRUD_PACKAGE_OVERRIDES = "allcrudPackageOverrides";
    // ModelsMap-level flags (postProcessModels, consumed at model.mustache's top level, outside
    // the per-model {{#models}}{{#model}} loop where vendorExtensions.allcrudIdType lives) -
    // gate the stock template's unconditional "import java.net.URI;"/"import java.util.*;" so
    // generated POJOs only carry them when a property actually needs java.net.URI (format: uri)
    // or java.util's unqualified ArrayList/HashMap/LinkedHashSet/Arrays (container or byte[]
    // vars - see pojo.mustache's fluent add/put methods and Arrays.equals/hashCode calls).
    // Both imports are unconditional in the untouched stock JavaSpring/model.mustache too (verified
    // by diff) - not something this project introduced, but still dead weight in every one of
    // this project's own generated POJOs so far, since {{#imports}} above already adds
    // java.net.URI on its own account whenever a property's type genuinely resolves to it.
    public static final String ALLCRUD_NEEDS_URI_IMPORT = "allcrudNeedsUriImport";
    public static final String ALLCRUD_NEEDS_JAVA_UTIL_IMPORT = "allcrudNeedsJavaUtilImport";

    private static final String X_ALLCRUD_ID_TYPE = "x-allcrud-id-type";
    private static final String X_ALLCRUD_RESOURCE = "x-allcrud-resource";
    // Document-root vendor extension (OpenAPI#getExtensions(), NOT info#getExtensions()) -
    // root is where document-wide TOOLING directives conventionally live (e.g. Redoc's
    // x-tagGroups), as opposed to info-level extensions which describe the API itself (e.g.
    // Redoc's x-logo). Default false/absent is a deliberate safety choice: turning this on
    // changes what gets generated for every existing spec that doesn't opt in, so it must
    // never be silently assumed - same "explicit beats absent, absent falls back to a safe
    // default" pattern as x-allcrud-resource's own per-path override below, and the same one
    // already used elsewhere in this project (resolveEffectivePackage, generate:[...] per
    // resource, onRegenerate).
    private static final String X_ALLCRUD_AUTO_RESOURCE = "x-allcrud-auto-resource";
    private static final String DTO_STYLE = "DTO";
    private static final String VO_STYLE = "VO";

    // Populated by postProcessOperationsWithModels, consulted by toApiFilename - see
    // toApiFilename's javadoc for why this reuses that data instead of resolving the
    // entity name a second time from a different source.
    private final Map<String, String> entityNameByApiName = new HashMap<>();
    // Entity package resolution (resolveEntityPackage) requires walking the whole sourceRoot
    // tree - cheap for the handful of resources a real project has, but cached per entity name
    // anyway since postProcessOperationsWithModels can run more than once for the same
    // resource's tag group across a single generate() invocation.
    private final Map<String, String> entityPackageByEntityName = new HashMap<>();
    // Entity names (CodegenModel#schemaName) confirmed as real Allcrud resources - populated
    // only when postProcessOperationsWithModels actually resolves a tag (explicit or inferred
    // x-allcrud-resource, with an "id"), read back by AllcrudGenerator#relocate to decide
    // which staged REPOSITORY/CONVERTER/SERVICE/CONTROLLER/UNIT_TEST/INTEGRATION_TEST files
    // are real output vs. openapi-generator's own per-tag scaffolding for tags that were
    // never marked as a resource at all (see confirmedResourceNames()). POJO is deliberately
    // NOT gated by this - its generation has always been schema-driven, not path/tag-driven,
    // independent of x-allcrud-resource entirely (a schema can be a legitimate request/
    // response body of a non-CRUD endpoint and still need its VO/DTO generated).
    private final Set<String> confirmedResourceNames = new LinkedHashSet<>();

    public AllcrudSpringCodegen() {
        super();
        // api.mustache (the stock "{{classname}}Api" interface, e.g. ProductsApi) is never
        // implemented by our generated Controller (which extends CrudController directly
        // instead of the stock delegate pattern - see apiController.mustache) - it's dead
        // code. It also broke once toApiFilename below started returning the bare entity
        // name ("Product.java") while this file's own content still declared the stock,
        // differently-named interface inside it (a file/class name mismatch, confirmed via
        // javac to be a hard compile error) - so this isn't just cleanup, it's a fix.
        // apiDelegate.mustache isn't in this map by default (delegate pattern is opt-in),
        // so there's nothing to remove for it.
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
     * Confirmed by reading DefaultGenerator's source (not assumed): config.preprocessOpenAPI
     * (openAPI) runs BEFORE config.setOpenAPI(openAPI), but they're passed the exact same
     * OpenAPI instance - so extensions written onto a PathItem here are visible later, once
     * this.openAPI is set, to fromOperation's own path-level vendor extension backfill (see
     * that method below) with zero changes needed there.
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
            if (hasPathParam(collectionPath)) {
                continue;
            }
            PathItem collectionPathItem = collectionEntry.getValue();
            String schemaName = resolveListItemSchemaName(openAPI, collectionPathItem.getGet());
            if (schemaName == null) {
                continue;
            }

            for (Entry<String, PathItem> itemEntry : paths.entrySet()) {
                String itemPath = itemEntry.getKey();
                if (!isItemPathOf(collectionPath, itemPath)) {
                    continue;
                }
                PathItem itemPathItem = itemEntry.getValue();
                if (!referencesSchema(openAPI, itemPathItem, schemaName)) {
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

    // "/products" -> "/products/{id}" matches (exactly one more segment, and that segment is
    // a path parameter). "/products" -> "/products/search" does NOT match (extra segment is a
    // literal, not "{...}") - this is what keeps a genuinely non-CRUD sibling endpoint like a
    // search/export/report route from being misdetected as this resource's item path.
    private boolean isItemPathOf(String collectionPath, String candidatePath) {
        if (candidatePath.length() <= collectionPath.length() || !candidatePath.startsWith(collectionPath)) {
            return false;
        }
        String suffix = candidatePath.substring(collectionPath.length());
        return suffix.matches("/\\{[^/{}]+}");
    }

    // Collection path signal: a GET whose success response is an array of a named ($ref'd)
    // schema. POST is deliberately NOT required here (approved design decision) - a
    // read-only-via-API resource (managed through another channel, e.g. an admin tool) still
    // benefits from generated Repository/Service/Converter/Controller for the operations that
    // do exist in the spec.
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
        // List.of(...) rejects null elements outright - most item paths only implement a
        // subset of GET/PUT/PATCH/DELETE, so this array (which allows null) is walked by
        // index instead of collected into a List first.
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

    // Only a $ref'd (named component) schema counts - an inline schema has no stable name to
    // correlate a collection path against an item path with, consistent with how every other
    // schema resolution in this class (findResourceModel, findIdProperty) only ever deals in
    // named component schemas.
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
     * pojo.mustache hardcodes the ID type as the literal "Long" in its
     * "implements AbstractEntityVO&lt;Long&gt;"/"AbstractEntityDTO&lt;Long&gt;" clause - a
     * known limitation noted when that template was written (pojo.mustache renders in a
     * per-model CodegenModel context, which never sees allcrudIdType, resolved only in
     * postProcessOperationsWithModels's OperationsMap context). It went unnoticed because
     * every resource tested so far happened to use Long. Adding a second resource with a
     * different ID type (Order, Integer) surfaced it for real: OrderVO compiled with
     * "implements AbstractEntityVO&lt;Long&gt;" while its getId() correctly returned
     * Integer - a hard compile error, not a leak between resources in this class.
     *
     * This resolves the ID type per model directly (each model already has its own "id"
     * property in allVars, no cross-referencing against operations needed, unlike
     * postProcessOperationsWithModels above) and stashes it in the model's own
     * vendorExtensions map - CodegenModel isn't a Map like OperationsMap, so it can't take
     * an arbitrary top-level put(); vendorExtensions is the established extension point
     * for this (pojo.mustache already reads vendorExtensions.x-class-extra-annotation the
     * same dotted way).
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
     * The stock fromOperation only copies vendor extensions declared on the OpenAPI
     * *operation* itself (operation.getExtensions()) into CodegenOperation#vendorExtensions.
     * Extensions declared on the *path* (e.g. x-allcrud-resource, x-allcrud-id-type in this
     * project's convention) never reach there on their own - confirmed empirically, not
     * assumed. This override backfills them, with operation-level extensions always taking
     * precedence over path-level ones on conflict (putIfAbsent: the operation's own values,
     * copied by super.fromOperation already, are never overwritten).
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
    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
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

        for (CodegenOperation operation : operations.getOperation()) {
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

            // x-allcrud-resource (explicit or inferred - see preprocessOpenAPI) is the actual
            // gate on generation, not just a label: a tag whose schema happens to have an
            // "id" but was never marked/inferred as a resource must NOT get a Controller/
            // Service/Repository/Converter generated for it - until this check, ANY path with
            // an id-bearing schema silently got full CRUD scaffolding regardless of
            // x-allcrud-resource, making x-allcrud-resource: false a no-op and the marker
            // itself decorative outside the id-missing error below. Stopping here (not
            // populating objs, not adding to confirmedResourceNames) means
            // AllcrudGenerator#relocate will discard this tag's staged Controller/Service/
            // Repository/Converter/*Test files entirely - openapi-generator still renders
            // them into the throwaway staging dir (harmless), they just never reach
            // sourceRoot. POJO is unaffected (see confirmedResourceNames' own comment).
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

        // A path marked x-allcrud-resource: true is a hard promise to generate a
        // CrudController/CrudService/EntityRepository/Converter for it - all four are
        // generic over an ID type, so a resource with no "id" property (and no
        // x-allcrud-id-type override) can't be satisfied. Before this check, the loop above
        // just fell through with objs never populated for this tag: postProcessOperationsWithModels
        // returned normally, so nothing downstream (relocate(), the templates) ever saw an
        // error - the templates rendered with allcrudEntityName/allcrudIdType etc. simply
        // absent, producing syntactically invalid Java (empty generics, "import .;", a
        // generic "Controller"/"Service"/"Repository"/"Converter" class name collision across
        // every resource in this situation) that only surfaced much later, in the consumer's
        // own javac - confirmed empirically, not hypothetical. Failing here, as soon as the
        // resolution that would need "id" comes up empty, is the earliest point a CodegenModel
        // (with allVars fully resolved - $ref/allOf/inheritance flattened) exists at all.
        if (!resolved && isAllcrudResource && firstResourceModelWithoutId != null) {
            throw new IllegalStateException(
                    "Resource \"" + firstResourceModelWithoutId.schemaName + "\" is marked "
                            + "x-allcrud-resource but has no \"id\" property. CrudController requires "
                            + "an ID type. Add an \"id\" property to the schema, or remove "
                            + "x-allcrud-resource if this resource shouldn't be generated.");
        }

        return objs;
    }

    // Read by AllcrudGenerator#relocate after DefaultGenerator#generate() completes (same
    // CodegenConfig instance, obtained via ClientOptInput#getConfig()) to decide which staged
    // REPOSITORY/CONVERTER/SERVICE/CONTROLLER/UNIT_TEST/INTEGRATION_TEST files are real,
    // confirmed-resource output versus openapi-generator's own per-tag scaffolding for tags
    // nobody marked/inferred as an allcrud resource - see the comment on
    // postProcessOperationsWithModels' "if (!isAllcrudResource) break;" above.
    public Set<String> confirmedResourceNames() {
        return Set.copyOf(confirmedResourceNames);
    }

    // Path-level vendor extension (x-allcrud-resource) backfilled onto the operation by
    // fromOperation above - checked here, not assumed true for every tag, because
    // postProcessOperationsWithModels runs for every tag in the spec regardless of that marker
    // (nothing upstream filters non-resource paths out of codegen). Without this check, the
    // no-"id" fail-fast below would wrongly reject any incidental model-with-no-id that happens
    // to share a tag with unrelated, non-CRUD operations.
    private boolean isAllcrudResource(CodegenOperation operation) {
        Object value = operation.vendorExtensions.get(X_ALLCRUD_RESOURCE);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * The generated file name otherwise defaults to this.toApiName(tag) + suffix (e.g.
     * "ProductsApiController.java" - "ProductsApi" derived from the OpenAPI tag, which
     * SpringCodegen auto-derives from the path and pluralizes, plus its own "Api" suffix
     * convention). That mismatched the class name inside the file once apiController.
     * mustache/service.mustache/repository.mustache/converter.mustache were changed to use
     * allcrudEntityName ("Product") as the class name prefix instead - and javac rejects a
     * public class whose name doesn't match its file name, confirmed empirically, not
     * assumed.
     *
     * This does NOT re-resolve the entity name from the OpenAPI model a second time (which
     * would duplicate the "find the id property" logic in postProcessOperationsWithModels
     * above under a different, lower-level form, since CodegenModel doesn't exist yet at
     * preprocessOpenAPI time - confirmed empirically that operation.getTags() is still null
     * that early). Instead it reuses the SAME resolution already cached above: for a given
     * tag, postProcessOperationsWithModels always runs (via DefaultGenerator#processOperations)
     * before toApiFilename is called for that same tag (confirmed in DefaultGenerator's
     * generateApis), and AbstractJavaCodegen#toApiFilename(name) is itself just
     * toApiName(name) - the exact same transform used as this cache's key
     * (operations.getClassname() = config.toApiName(tag), set before
     * postProcessOperationsWithModels runs).
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

    // resources.<entityName>.basePath (ALLCRUD_BASE_PATH_OVERRIDES) is a FINAL absolute path,
    // not concatenated with the prefix - that's the whole point of an override. Without one,
    // the path is always "{basePathPrefix}/{entityName, lowercased}". basePathPrefix defaults
    // to "" (no opinion, e.g. no forced "/api") when the caller doesn't set it at all.
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

    // resources.<entityName>.<layer>.package (ALLCRUD_PACKAGE_OVERRIDES) wins outright over the
    // layer's global package (ALLCRUD_LAYER_PACKAGES) when present - same "override replaces,
    // never merges/concatenates" rule as resolveBasePath above.
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

    // The Entity is never generated (out of scope, hand-written by the consumer) and has no
    // "packages.entity" yml key (rejected - doesn't support per-module/domain entity
    // packages), so its package can only be discovered by looking at what the consumer
    // actually wrote: scan sourceRoot for a file literally named "<entityName>.java" and
    // parse its "package ...;" declaration - line 1, same technique already proven safe for
    // rewriting that line in generated files (AllcrudGenerator#rewritePackageStatement),
    // applied here in reverse (reading instead of writing).
    //
    // Reflection over the compiled class was considered and rejected: this runs BEFORE
    // compileJava (that's the entire point of the javaSourceDir/srcDir wiring - generate
    // source first, compile everything together after), so the Entity is never compiled yet
    // at this point in a fresh build. Only the source text is available.
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
