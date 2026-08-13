# Technical notes — AllcrudGeneratorPluginFunctionalTest

Forensic findings about external tooling behavior (JUnit, Maven POM
resolution, openapi-generator), not decisions about this project's own
architecture.

## JUnit 5 doesn't guarantee @BeforeEach execution order without @Order

3 independent fixture setups (spec, config yml, entities) - each writes its
own, never-read-by-another file under `projectDir`, so there's no real
ordering dependency between them. Merged into one `@BeforeEach` because
JUnit 5 doesn't guarantee execution order across multiple `@BeforeEach`
methods in the same class without `@Order`, rather than 3 separate ones
relying on declaration order to happen to work.

## allcrud's published POM leaves Spring Boot starters unversioned

Same pinned coordinate as `gradle.properties` in the root module
(`allcrudCoreVersion`). Hardcoded in this fixture deliberately: it's a
throwaway fixture project, not production code. allcrud's published POM
leaves its Spring Boot starters unversioned (managed by the same BOM at
allcrud's own build time, not baked into the published POM) - any
consumer, this fixture included, has to import that BOM itself.

## allcrud's POM declares its own dependencies at runtime scope, not compile

By design, a Spring Boot library expects the consuming app to bring its own
starters at compile scope, exactly like any real app already would. This
fixture simulates that real app dependency - it's not compensating for a
gap in allcrud or the plugin.

## openapi-generator's swagger2AnnotationLibrary emits @Schema annotations

This project's default annotation library emits `@Schema` annotations on
generated models - a real consumer project needs
`io.swagger.core.v3:swagger-annotations-jakarta` on its own compile
classpath for that to resolve, same reasoning as the Spring Boot starters
above.

## The stock "spring" generator also emits SpringDocConfiguration.java by default

References `io.swagger.v3.oas.models.*` - not an allcrud-generator concern,
just what a real consumer of the underlying spring library needs on its
classpath (`swagger-models-jakarta`) for that stock-generated file to
compile.
