package com.techmath.allcrud.generator.codegen;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Operation;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.languages.SpringCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

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
    // Global (not per-resource) target packages for the other 4 layers - plain
    // additionalProperties, no per-operation resolution needed here: DefaultGenerator merges
    // config.additionalProperties() into every operation/model bundle automatically
    // (confirmed empirically - operation.putAll(config.additionalProperties()) in
    // DefaultGenerator#generateApis), the same mechanism that already makes {{javaxPackage}}
    // work in these templates without any custom code in this class for it.
    public static final String ALLCRUD_POJO_PACKAGE = "allcrudPojoPackage";
    public static final String ALLCRUD_REPOSITORY_PACKAGE = "allcrudRepositoryPackage";
    public static final String ALLCRUD_CONVERTER_PACKAGE = "allcrudConverterPackage";
    public static final String ALLCRUD_SERVICE_PACKAGE = "allcrudServicePackage";

    private static final String X_ALLCRUD_ID_TYPE = "x-allcrud-id-type";
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

        for (ModelMap modelMap : objs.getModels()) {
            CodegenModel model = modelMap.getModel();
            CodegenProperty idProperty = findIdProperty(model);
            if (idProperty != null) {
                model.vendorExtensions.put(ALLCRUD_ID_TYPE, idProperty.dataType);
            }
        }

        return objs;
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
        for (CodegenOperation operation : operations.getOperation()) {
            Object override = operation.vendorExtensions.get(X_ALLCRUD_ID_TYPE);
            if (override != null) {
                idTypeOverride = override.toString();
                break;
            }
        }

        for (CodegenOperation operation : operations.getOperation()) {
            CodegenModel resourceModel = findResourceModel(operation, modelsByClassname);
            if (resourceModel == null) {
                continue;
            }

            CodegenProperty idProperty = findIdProperty(resourceModel);
            if (idProperty == null && idTypeOverride == null) {
                continue;
            }

            objs.put(ALLCRUD_ENTITY_NAME, resourceModel.schemaName);
            objs.put(ALLCRUD_POJO_CLASS_NAME, resourceModel.classname);
            objs.put(ALLCRUD_ID_TYPE, idTypeOverride != null ? idTypeOverride : idProperty.dataType);
            objs.put(ALLCRUD_BASE_PATH, resolveBasePath(resourceModel.schemaName));
            objs.put(ALLCRUD_ENTITY_PACKAGE, resolveEntityPackage(resourceModel.schemaName));
            entityNameByApiName.put(operations.getClassname(), resourceModel.schemaName);
            break;
        }

        return objs;
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

        List<Path> matches = new ArrayList<>();
        if (Files.isDirectory(sourceRoot)) {
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals(targetFileName))
                        .forEach(matches::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

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
        String content;
        try {
            content = Files.readString(javaFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
