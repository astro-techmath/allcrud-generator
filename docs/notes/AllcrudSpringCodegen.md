# Technical notes — AllcrudSpringCodegen

Forensic findings about *external tooling behavior* (openapi-generator,
Mustache, Gradle, javac), confirmed empirically, not decisions about this
project's own architecture. Architecture decisions live in `docs/adr/`;
these notes are referenced from inline pointers in the source instead of
being inlined themselves, because they document *how a third-party
dependency actually behaves* rather than *what this code does*.

For the `putAll`/`DefaultGenerator#generateApis` finding (why
`allcrudPojoPackage`/`allcrudRepositoryPackage`/`allcrudConverterPackage`/
`allcrudServicePackage` are never set as `additionalProperties`), see the
canonical note in `docs/notes/AllcrudGenerator.md` — this class is the
consumer of the workaround (`allcrudLayerPackages`), not where the finding
was made.

## CodegenConfigLoader#forName fallback

`AllcrudSpringCodegen` is not registered via `META-INF/services` (SPI):
`CodegenConfigLoader#forName` falls back to
`Class.forName(name).newInstance()` when no SPI provider matches the given
name, so the generation driver (`AllcrudGenerator`) just passes this class's
fully-qualified name as the "generator name" instead of a short SPI alias
like `"spring"`.

## api.mustache stock template is dead code — and was a real bug, not just cleanup

`api.mustache` (the stock `"{{classname}}Api"` interface, e.g.
`ProductsApi`) is never implemented by the generated Controller (which
extends `CrudController` directly instead of the stock delegate pattern —
see `apiController.mustache`).

It's removed from `apiTemplateFiles` in the constructor not just as
cleanup: it also broke once `toApiFilename` started returning the bare
entity name (`"Product.java"`) while `api.mustache`'s own content still
declared the stock, differently-named interface inside it — a file/class
name mismatch, confirmed via `javac` to be a hard compile error.

`apiDelegate.mustache` isn't in `apiTemplateFiles` by default (the delegate
pattern is opt-in), so there's nothing to remove for it.

## fromOperation only copies vendor extensions from the OPERATION level, never PATH

The stock `fromOperation` only copies vendor extensions declared on the
OpenAPI *operation* itself (`operation.getExtensions()`) into
`CodegenOperation#vendorExtensions`. Extensions declared on the *path*
(e.g. `x-allcrud-resource`, `x-allcrud-id-type` in this project's
convention) never reach there on their own — confirmed empirically, not
assumed. The override in this class backfills them, with operation-level
extensions always taking precedence over path-level ones on conflict
(`putIfAbsent`: the operation's own values, copied by `super.fromOperation`
already, are never overwritten).

## Fail-fast on missing "id" — why silent fallthrough was worse

Before the explicit check that throws `IllegalStateException` when a
resource has no `"id"` property (and no `x-allcrud-id-type` override), the
resolution loop in `postProcessOperationsWithModels` just fell through with
`objs` never populated for that tag: the method returned normally, so
nothing downstream (`relocate()`, the templates) ever saw an error — the
templates rendered with `allcrudEntityName`/`allcrudIdType` etc. simply
absent, producing syntactically invalid Java (empty generics, `import .;`,
a generic `Controller`/`Service`/`Repository`/`Converter` class name
collision across every resource in this situation) that only surfaced much
later, in the consumer's own `javac` — confirmed empirically, not
hypothetical.

Failing here, as soon as the resolution that would need `"id"` comes up
empty, is the earliest point a `CodegenModel` (with `allVars` fully
resolved — `$ref`/`allOf`/inheritance flattened) exists at all.

## toApiFilename — cache reuse depends on DefaultGenerator's real execution order

The generated file name otherwise defaults to `this.toApiName(tag) + suffix`
(e.g. `"ProductsApiController.java"` — `"ProductsApi"` derived from the
OpenAPI tag, which SpringCodegen auto-derives from the path and pluralizes,
plus its own `"Api"` suffix convention). That mismatched the class name
inside the file once `apiController.mustache`/`service.mustache`/
`repository.mustache`/`converter.mustache` were changed to use
`allcrudEntityName` (`"Product"`) as the class name prefix instead — and
`javac` rejects a public class whose name doesn't match its file name,
confirmed empirically, not assumed.

`toApiFilename` does NOT re-resolve the entity name from the OpenAPI model a
second time (which would duplicate the "find the id property" logic in
`postProcessOperationsWithModels` under a different, lower-level form,
since `CodegenModel` doesn't exist yet at `preprocessOpenAPI` time —
confirmed empirically that `operation.getTags()` is still null that early).
Instead it reuses the SAME resolution already cached in
`entityNameByApiName`: for a given tag, `postProcessOperationsWithModels`
always runs (via `DefaultGenerator#processOperations`) before
`toApiFilename` is called for that same tag (confirmed in
`DefaultGenerator`'s `generateApis`), and
`AbstractJavaCodegen#toApiFilename(name)` is itself just `toApiName(name)`
— the exact same transform used as this cache's key
(`operations.getClassname() = config.toApiName(tag)`, set before
`postProcessOperationsWithModels` runs).

## Why reflection was rejected for resolving the Entity's package

Reflection over the compiled Entity class was considered and rejected:
`resolveEntityPackage` runs BEFORE `compileJava` (that's the entire point of
the `javaSourceDir`/`srcDir` wiring — generate source first, compile
everything together after), so the Entity is never compiled yet at this
point in a fresh build. Only the source text is available, hence scanning
`sourceRoot` for a file literally named `"<entityName>.java"` and parsing
its `package ...;` declaration instead.

## preprocessOpenAPI runs before setOpenAPI, same OpenAPI instance

Confirmed by reading `DefaultGenerator`'s source (not assumed):
`config.preprocessOpenAPI(openAPI)` runs BEFORE `config.setOpenAPI(openAPI)`,
but both are passed the exact same `OpenAPI` instance. So extensions written
onto a `PathItem` during `preprocessOpenAPI` (see `inferAllcrudResources`)
are still visible later, once `this.openAPI` is set, to `fromOperation`'s
own path-level vendor extension backfill — with zero changes needed there to
make that work.

## pojo.mustache hardcoded ID type ("Long") — real bug, not a hypothetical

`pojo.mustache` hardcodes the ID type as the literal `"Long"` in its
`implements AbstractEntityVO<Long>`/`AbstractEntityDTO<Long>` clause — a
known limitation from when that template was written (`pojo.mustache`
renders in a per-model `CodegenModel` context, which never sees
`allcrudIdType`, resolved only in `postProcessOperationsWithModels`'s
`OperationsMap` context).

It went unnoticed because every resource tested so far happened to use
`Long`. Adding a second resource with a different ID type (`Order`,
`Integer`) surfaced it for real: `OrderVO` compiled with
`implements AbstractEntityVO<Long>` while its `getId()` correctly returned
`Integer` — a hard compile error, not a leak between resources in this
class.

The fix resolves the ID type per model directly (each model already has its
own `"id"` property in `allVars`, no cross-referencing against operations
needed, unlike `postProcessOperationsWithModels`) and stashes it in the
model's own `vendorExtensions` map — `CodegenModel` isn't a `Map` like
`OperationsMap`, so it can't take an arbitrary top-level `put()`;
`vendorExtensions` is the established extension point for this
(`pojo.mustache` already reads `vendorExtensions.x-class-extra-annotation`
the same dotted way).

## ALLCRUD_BASE_PATH_PREFIX/OVERRIDES — read per-operation from global additionalProperties

`AllcrudGenerator` sets these globally for the whole run (see
`GenerationRequest#basePathPrefix` / `ResourceOverride#basePath`) - read
here per-operation to resolve `ALLCRUD_BASE_PATH`. Not exposed as vendor
extensions like `x-allcrud-id-type`: these come from `allcrud-generator.yml`,
not the OpenAPI spec itself.

## ALLCRUD_SOURCE_ROOT — threaded through since the Entity is never generated

Passed through from `AllcrudGenerator#sourceRoot` so
`postProcessOperationsWithModels` can scan it for `<entityName>.java` (see
`resolveEntityPackage`) - the Entity itself is never generated, so its
package can't be resolved any other way (no `"packages.entity"` key in
`allcrud-generator.yml` either - rejected: doesn't support entities living
in different packages per module/domain, e.g. `com.acme.catalog.Product` vs
`com.acme.sales.Order`).

## ALLCRUD_CONTROLLER_PACKAGE — added for integrationTest.mustache's import

Added for `integrationTest.mustache`, which imports the generated
Controller class - until then nothing else ever imported "the Controller"
from another layer, so this was deliberately left out (see the old comment
on `AllcrudGenerator#layerPackages`, now stale).

## ALLCRUD_LAYER_PACKAGES — safe as a plain additionalProperty

Layer name -> global package (`AllcrudGenerator#layerPackages`) - safe to
set as a plain `additionalProperty` (unlike the 4 per-layer package keys)
because no template reads `"allcrudLayerPackages"` directly; only
`resolvePackage` does, as the fallback when a resource has no override.

## ALLCRUD_PACKAGE_OVERRIDES — per-resource exception to ALLCRUD_LAYER_PACKAGES

resourceName -> layer name -> package
(`ResourceOverride#packageOverrides`, see `AllcrudGenerator#packageOverrides`)
- a per-resource exception to `ALLCRUD_LAYER_PACKAGES`. Resolved
per-resource in `postProcessOperationsWithModels` (`resolvePackage`) -
without this, a Controller/Service/Converter importing another layer's
class would always import the GLOBAL package even when that resource's
layer was relocated somewhere else entirely.

## java.net.URI / java.util import gating in generated POJOs

`ModelsMap`-level flags (`postProcessModels`, consumed at
`model.mustache`'s top level, outside the per-model
`{{#models}}{{#model}}` loop where `vendorExtensions.allcrudIdType` lives) -
gate the stock template's unconditional `"import java.net.URI;"`/
`"import java.util.*;"` so generated POJOs only carry them when a property
actually needs `java.net.URI` (format: uri) or `java.util`'s unqualified
`ArrayList`/`HashMap`/`LinkedHashSet`/`Arrays` (container or `byte[]` vars -
see `pojo.mustache`'s fluent add/put methods and `Arrays.equals`/`hashCode`
calls).

Both imports are unconditional in the untouched stock
`JavaSpring`/`model.mustache` too (verified by diff) - not something this
project introduced, but still dead weight in every one of this project's
own generated POJOs so far, since `{{#imports}}` above already adds
`java.net.URI` on its own account whenever a property's type genuinely
resolves to it.

## x-allcrud-auto-resource — document-root vendor extension, defaults false

`OpenAPI#getExtensions()`, NOT `info#getExtensions()` - root is where
document-wide TOOLING directives conventionally live (e.g. Redoc's
`x-tagGroups`), as opposed to info-level extensions which describe the API
itself (e.g. Redoc's `x-logo`). Default false/absent is a deliberate safety
choice: turning this on changes what gets generated for every existing spec
that doesn't opt in, so it must never be silently assumed - same "explicit
beats absent, absent falls back to a safe default" pattern as
`x-allcrud-resource`'s own per-path override, and the same one already used
elsewhere in this project (`resolveEffectivePackage`, `generate:[...]` per
resource, `onRegenerate`).

## entityPackageByEntityName — why it's cached

`resolveEntityPackage` (see `docs/adr/0006-entity-out-of-scope-v1.md`)
requires walking the whole `sourceRoot` tree - cheap for the handful of
resources a real project has, but cached per entity name anyway since
`postProcessOperationsWithModels` can run more than once for the same
resource's tag group across a single `generate()` invocation.

## confirmedResourceNames — real output vs. openapi-generator scaffolding

Entity names (`CodegenModel#schemaName`) confirmed as real Allcrud
resources - populated only when `postProcessOperationsWithModels` actually
resolves a tag (explicit or inferred `x-allcrud-resource`, with an `"id"`),
read back by `AllcrudGenerator#relocate` to decide which staged
REPOSITORY/CONVERTER/SERVICE/CONTROLLER/UNIT_TEST/INTEGRATION_TEST files
are real output vs. openapi-generator's own per-tag scaffolding for tags
that were never marked as a resource at all. POJO is deliberately NOT
gated by this - its generation has always been schema-driven, not
path/tag-driven, independent of `x-allcrud-resource` entirely (a schema can
be a legitimate request/response body of a non-CRUD endpoint and still need
its VO/DTO generated).

## isItemPathOf — path matching rule for auto-resource inference

`"/products"` -> `"/products/{id}"` matches (exactly one more segment, and
that segment is a path parameter). `"/products"` -> `"/products/search"`
does NOT match (extra segment is a literal, not `"{...}"`) - this is what
keeps a genuinely non-CRUD sibling endpoint like a search/export/report
route from being misdetected as this resource's item path.

## resolveListItemSchemaName — POST not required (approved design decision)

Collection path signal: a GET whose success response is an array of a
named (`$ref`'d) schema. POST is deliberately NOT required here - a
read-only-via-API resource (managed through another channel, e.g. an admin
tool) still benefits from generated Repository/Service/Converter/Controller
for the operations that do exist in the spec.

## referencesSchema — array instead of List.of() for candidates

`List.of(...)` rejects null elements outright - most item paths only
implement a subset of GET/PUT/PATCH/DELETE, so this array (which allows
null) is walked by index instead of collected into a `List` first.

## schemaRefName — only $ref'd schemas count

An inline schema has no stable name to correlate a collection path against
an item path with, consistent with how every other schema resolution in
this class (`findResourceModel`, `findIdProperty`) only ever deals in named
component schemas.

## postProcessOperationsWithModels — early break on non-resource tag

See `docs/adr/0002-x-allcrud-resource-gate-and-inference.md`. Stopping here
(not populating `objs`, not adding to `confirmedResourceNames`) means
`AllcrudGenerator#relocate` will discard this tag's staged
Controller/Service/Repository/Converter/*Test files entirely -
openapi-generator still renders them into the throwaway staging dir
(harmless), they just never reach `sourceRoot`. POJO is unaffected (see
`confirmedResourceNames`' own note above).

## confirmedResourceNames() — read by AllcrudGenerator#relocate

Read by `AllcrudGenerator#relocate` after `DefaultGenerator#generate()`
completes (same `CodegenConfig` instance, obtained via
`ClientOptInput#getConfig()`) to decide which staged
REPOSITORY/CONVERTER/SERVICE/CONTROLLER/UNIT_TEST/INTEGRATION_TEST files
are real, confirmed-resource output versus openapi-generator's own per-tag
scaffolding for tags nobody marked/inferred as an allcrud resource - see
`postProcessOperationsWithModels`' own early break on a non-resource tag,
above.

## isAllcrudResource — why it's checked per-operation, not assumed true

Path-level vendor extension (`x-allcrud-resource`) backfilled onto the
operation by `fromOperation` - checked here, not assumed true for every
tag, because `postProcessOperationsWithModels` runs for every tag in the
spec regardless of that marker (nothing upstream filters non-resource paths
out of codegen). Without this check, the no-`"id"` fail-fast would wrongly
reject any incidental model-with-no-id that happens to share a tag with
unrelated, non-CRUD operations.

## resolveBasePath — the actual formula

See `docs/adr/0005-basepath-absolute-not-concatenated.md`. Without an
override, the path is always `"{basePathPrefix}/{entityName, lowercased}"`;
`basePathPrefix` defaults to `""` (no opinion, e.g. no forced `"/api"`) when
the caller doesn't set it at all.

## resolvePackage — override wins outright, never merges

`resources.<entityName>.<layer>.package` (`ALLCRUD_PACKAGE_OVERRIDES`) wins
outright over the layer's global package (`ALLCRUD_LAYER_PACKAGES`) when
present - same "override replaces, never merges/concatenates" rule as
`resolveBasePath`.
