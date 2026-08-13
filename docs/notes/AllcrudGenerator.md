# Technical notes — AllcrudGenerator

Forensic findings about *external tooling behavior* (openapi-generator,
Mustache, Gradle), confirmed empirically, not decisions about this project's
own architecture. Architecture decisions live in `docs/adr/`; these notes are
referenced from inline pointers in the source instead of being inlined
themselves, because they document *how a third-party dependency actually
behaves* rather than *what this code does* — that distinction is what keeps
them out of javadoc too.

## Template resolution: filesystem + classpath via GeneratorTemplateContentLocator

`CodegenConfigurator#setTemplateDir` is checked both as a filesystem path
AND, transparently, as a classpath resource root by openapi-generator's
`GeneratorTemplateContentLocator` (no `"classpath:"` prefix needed — it just
retries the same string via `ClassLoader#getResource`).

`TEMPLATE_DIR` is set to `"templates"`, not `"src/main/resources/templates"`:
that's where the templates live in this module's source tree, but Gradle's
`processResources` strips that prefix when packaging — the jar has them at
`templates/*.mustache`, so that's the string that must resolve. The
filesystem-relative form only ever worked by accident, because this
project's own tests happen to run with the CWD at the repo's root; any real
caller (the Gradle plugin included) runs with a different CWD and the
filesystem check silently fails over to the classpath check — see
`AllcrudGeneratorPluginFunctionalTest`.

## putAll / DefaultGenerator#generateApis (canonical — see also AllcrudSpringCodegen)

`allcrudPojoPackage`/`allcrudRepositoryPackage`/`allcrudConverterPackage`/
`allcrudServicePackage` are deliberately NOT set as `additionalProperties`
(unlike a first attempt at this, and unlike their siblings
`allcrudEntityName`/`allcrudBasePath`, which *are* set that way on purpose).

Reason: `DefaultGenerator#generateApis` calls
`operation.putAll(config.additionalProperties())` AFTER
`postProcessOperationsWithModels` already ran for that same operation
(confirmed by reading `DefaultGenerator`'s source, not assumed). That
`putAll` blindly overwrites any key already present in `additionalProperties()`
for the whole run. So any per-resource value that
`AllcrudSpringCodegen#postProcessOperationsWithModels` puts under one of
those 4 exact keys — via `objs.put(...)`, in an attempt to give each resource
its own resolved package — would be silently clobbered right back to the
global default by that `putAll`.

`allcrudEntityPackage`/`allcrudBasePath` never hit this because they're
NEVER set as `additionalProperties` at all — only ever `objs.put()`
per-resource, so `putAll` has nothing to clobber for those key names.

The fix applies the same idea to the package properties: the 4 global
defaults travel under a DIFFERENT key, `allcrudLayerPackages` (a plain
layer-name -> package map, read only by `AllcrudSpringCodegen`, never by a
template directly), so `putAll` can't collide with the per-resource-resolved
`allcrudPojoPackage`/etc. keys the templates actually read.

## GlobalSettings presence-vs-value + CSV dual-purpose (APIS/MODELS)

`DefaultGenerator#configureGeneratorProperties` reads the 4 `GlobalSettings`
flags (`apis`/`models`/`supportingFiles`/`webhooks`) by PRESENCE, not value:
if NONE are set, it defaults all 4 to `generate=true`; the instant ANY one is
set, the other unset ones default to `generate=false` individually. So
setting `SUPPORTING_FILES="false"` alone does NOT disable it (a set property
is "on" regardless of its string value for this particular check) — it
silently disables MODELS/APIS instead, since they were left unset. The fix
is the reverse: explicitly mark `apis`/`models` as present and leave
`supportingFiles` unset, so it falls through to the `false` default in the
same branch.

The value used must be `""` (empty), NOT `"true"`: APIS/MODELS are
dual-purpose — presence decides the boolean above, but
`DefaultGenerator#getPropertyAsSet` (used elsewhere to filter which specific
models/apis to generate, e.g. `-Dmodels=Product,Order`) parses this SAME
string as a CSV set. `"true"` would be parsed as "only generate a model/api
literally named true", filtering out everything and silently producing zero
files — hit this for real while verifying against 7.23.0's source, not a
hypothetical.

## AllcrudGenerator is not (yet) a production entrypoint

`generate(GenerationRequest)` is the generic, parameterized form of what
used to be the test-only `ProductExampleGeneration`: same
`CodegenConfigurator`/`DefaultGenerator` invocation, same
`AllcrudSpringCodegen` and custom templates, no design decisions reopened.
It is not itself a CLI or Gradle-plugin production entrypoint — that's a
separate, not-yet-designed decision; callers (the Gradle/Maven plugins) call
into it.

## Staging + move + package rewrite (idempotent-scaffolding design)

openapi-generator always runs into a scratch directory first, never
directly into the caller's `sourceRoot`. Each staged file gets its
`package ...;` line (always line 1 — see the "package statement is always
line 1" note below) rewritten to its configured target package, then is
written under `sourceRoot` at the path that package implies — unless the
target already exists and the per-layer overwrite policy says to leave it
alone (see `shouldOverwrite()`).

## openApiNullable disabled — no jackson-databind-nullable dependency

This project doesn't use the JsonNullable-based absent-vs-null distinction,
and `org.openapitools:jackson-databind-nullable` isn't a dependency here —
leaving `openApiNullable` at its stock default would produce an unresolved
import in the generated VO/DTO.

## allcrudBasePathPrefix/Overrides set at codegen time, not in relocate()

Baked into the generated `@RequestMapping` literal by `AllcrudSpringCodegen`
(see `ALLCRUD_BASE_PATH_PREFIX`/`ALLCRUD_BASE_PATH_OVERRIDES` there) — has
to happen at codegen time, not in `relocate()`, since it's part of the Java
source text itself, not a file-placement decision.

## Cross-layer imports need per-layer packages

Controller importing the Entity/POJO/Service/Converter it depends on,
Service importing Repository, etc. used to either hardcode
`"org.openapitools.*"` or assume "same package, no import needed" — both
wrong once `packages.<layer>` in `allcrud-generator.yml` can put each layer
in a different package. This is the motivation behind
`allcrudPojoPackage`/etc. existing at all — see the canonical `putAll` note
above for why they aren't set as `additionalProperties` directly.

## registerApiLayerTemplates always registered regardless of layersToGenerate

Staging generates all 5 layers unconditionally (openapi-generator's own
pipeline, not fought here) — `relocateOne()` is what filters which ones
actually reach `sourceRoot`.

## Stock supportingFiles don't map to this project's layer model

The stock "spring" generator's supporting files (Application main class,
SpringDocConfiguration, ApiUtil, HomeController) don't map to the
VO/Repository/Converter/Service/Controller layer model this project
generates — a real consumer already has its own Application class. None of
the 5 custom templates reference them (verified). This is why they're
disabled via the `GlobalSettings` presence-vs-value mechanism documented
above, instead of left to generate and discarded.

## ClientOptInput#getConfig() is deprecated with no replacement (openapi-generator 7.23.0)

`ClientOptInput#getConfig()` is deprecated with no replacement exposed yet:

- In `generate()`, it's still how `AllcrudSpringCodegen#confirmedResourceNames`
  (populated during `postProcessOperationsWithModels`, which already ran as
  part of `generate()`) gets read back after the fact — same `CodegenConfig`
  instance the whole run just used.
- In `registerApiLayerTemplates()`, it's still the only supported way to
  register a new per-tag template file outside of writing a custom
  `CodegenConfig` subclass, for mutating `apiTemplateFiles()`
  programmatically.

Both uses are suppressed with `@SuppressWarnings("deprecation")` rather than
worked around, since there's no replacement API yet.

## generateGlobalExceptionHandler is a plain text block, not a .mustache template

The one PROJECT-level (not per-resource) artifact this generator produces —
runs once, independent of the OpenAPI spec entirely (no staging dir, no
openapi-generator pipeline, no per-resource classify-by-suffix relocate:
there's no resource name or spec content involved, just package + className
from `allcrud-generator.yml`'s `exceptionHandler` block). "Generate once,
never overwrite", no configurable exception — see `ExceptionHandlerConfig`
for why.

A plain Java text block, not a `.mustache` template: the content has zero
variability beyond package/className substitution, so routing it through
the openapi-generator template/staging machinery built for the 5
per-resource layers would be needless coupling for no benefit — and would
require re-enabling `supportingFiles`, which this project deliberately
disabled elsewhere (see the `GlobalSettings` note above) because none of the
stock supporting files map to this project's layer model.

## layerPackages map — CONTROLLER inclusion history

Layer name (`GeneratedLayer#name()`, e.g. `"SERVICE"`) -> global package —
only the 5 layers `AllcrudSpringCodegen` resolves per-resource need to
travel this way. `CONTROLLER` used to be excluded here ("nothing imports
the Controller from another layer") — `integrationTest.mustache` broke that
assumption, so it's included now too. `UNIT_TEST`/`INTEGRATION_TEST`
themselves are never in this map: nothing ever imports "the unit/integration
test" class from elsewhere.

## resolveEffectivePackage — recursive fallback for test layers

`UNIT_TEST`/`INTEGRATION_TEST` have no `"packages.<layer>"` global entry to
fall back to (see `GeneratedLayer`'s javadoc) — their default is the SIBLING
production layer's own resolved package for this exact resource (`SERVICE`
for `UNIT_TEST`, `CONTROLLER` for `INTEGRATION_TEST`), itself computed by
the same override-then-global rule, recursively — not a flat
`"packages.service"` global lookup, since the sibling layer might ALSO have
a per-resource override for this resource that should be honored first.

## classify() suffix non-collision — verified by hand, not assumed

Suffix-based classification of the project's own template output filenames
(`ProductVO.java`, `ProductController.java`, etc) into `(resourceName,
layer)` - resourceName is the suffix stripped (`"Product"`). Order doesn't
matter - the suffixes are mutually exclusive by construction: verified by
hand, not just assumed. E.g. `"ProductServiceTest"` does NOT end with
`"Service"` — its last 7 characters are `"iceTest"` — and
`"ProductControllerIT"` does NOT end with `"Controller"` — its last 10
characters are `"ntrollerIT"` (see apiController/converter/pojo/repository/
service/unitTest/integrationTest.mustache output filenames).

Throws on anything else: with supporting files disabled, every staged file
is expected to match one of these — an unrecognized file means either a
template was added without updating this classifier, or a stale assumption,
and failing loudly beats silently misplacing (or losing) a file.

## layerSuffix — why UNIT_TEST/INTEGRATION_TEST aren't mechanically derived

Generated Java class name / filename suffix per layer is NOT mechanically
derived from the enum constant name (`capitalize(layer.name())`) for
`UNIT_TEST`/`INTEGRATION_TEST`: those are multi-word SCREAMING_SNAKE_CASE
Java identifiers (`"UNIT_TEST"`), and `capitalize()` would produce
`"Unit_test"`, not a valid convention-following Java class name suffix.
Must match the suffix string registered for the corresponding template in
`registerApiLayerTemplates`.

## rewritePackageStatement — package line is always line 1 (verified empirically)

Verified empirically (not assumed) that the package statement is always the
literal first line of every one of the 5 templates' output, with nothing
(license header, blank line) before it — see the
`AllcrudGeneratorPluginFunctionalTest` smoke test output this was checked
against. Fails loudly if that ever stops being true rather than silently
corrupting the file.

## packageOverrides — why the Map is nested one level deeper than basePathOverrides

Nested `Map<String, Map<String, String>>` shape (resourceName -> layer name
-> package) mirrors `basePathOverrides`' flat `Map<String, String>` one
level deeper, since a resource can override more than one layer's package
independently - `basePathOverrides` only ever has one value per resource
(the whole `@RequestMapping` path), but package overrides need a value per
layer per resource.

## registerApiLayerTemplates — service/repository/converter.mustache are brand-new templates

`service.mustache`/`repository.mustache`/`converter.mustache` are brand-new
templates (the stock "spring" generator has none of these layers) —
registering them here as per-tag api template files is the supported
extension point for this (`CodegenConfig#apiTemplateFiles()`), the same
`ClientOptInput#getConfig()` extension point documented above.
