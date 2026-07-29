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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final String X_ALLCRUD_ID_TYPE = "x-allcrud-id-type";
    private static final String DTO_STYLE = "DTO";
    private static final String VO_STYLE = "VO";

    // Populated by postProcessOperationsWithModels, consulted by toApiFilename - see
    // toApiFilename's javadoc for why this reuses that data instead of resolving the
    // entity name a second time from a different source.
    private final Map<String, String> entityNameByApiName = new HashMap<>();

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

    private CodegenProperty findIdProperty(CodegenModel model) {
        for (CodegenProperty property : model.allVars) {
            if ("id".equals(property.baseName)) {
                return property;
            }
        }
        return null;
    }

}
