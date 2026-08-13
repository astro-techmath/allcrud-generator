# 10. Test source root is a separate, non-configurable-to-merge root

## Context

`unitTest`/`integrationTest` generated files (`GeneratedLayer.UNIT_TEST` /
`GeneratedLayer.INTEGRATION_TEST`) need to land under a project's test source tree (typically
`src/test/java`), not alongside the main production layers (typically `src/main/java`). Both are
just directories from the generator's point of view, but conflating them would let test code and
production code end up in the same source root by accident.

## Decision

`testSourceRoot` (`GenerationRequest#testSourceRoot`) is a field distinct from `sourceRoot`,
carried independently end to end - through `GenerationRequest`, the Gradle plugin's
`AllcrudGeneratorExtension#testOutputDir` (convention defaults to `src/test/java`, mirroring
`outputDir`'s own `src/main/java` default), and `AllcrudGenerateTask`. It is never configurable to
resolve to the same directory as `sourceRoot`, and the two are never merged into one value
anywhere in the pipeline.

## Consequences

- Test-layer generation always lands under its own root even if a project customizes its main
  `outputDir` to something non-standard.
- The Gradle plugin wires 2 independent `SourceSet` `srcDir` entries (`main`'s `java` and `test`'s
  `java`) from the 2 separate task output properties, rather than deriving one from the other.
