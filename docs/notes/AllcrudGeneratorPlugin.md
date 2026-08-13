# Technical notes — AllcrudGeneratorPlugin

Forensic findings about Gradle's own behavior, not decisions about this
project's architecture.

## javaSourceDir convention — derived from the extension, not the task's own output

Derived from the extension's `outputDir`, not the task's own
`getOutputDir()` - see `AllcrudGenerateTask`'s own note on why (Gradle
disallows deriving one `@OutputDirectory` of a task from another
`@OutputDirectory` of that same task before the task has run). No longer
appends `"src/main/java"`: `GenerationRequest#sourceRoot` (see
`AllcrudGenerator`) IS now the Java source root files land under directly -
openapi-generator's own nested `src/main/java` output layout only existed
in the pre-staging design.

## Wiring the source set to the task's own output property enables automatic task dependency

Wiring the source set to the task's own output property (not a detached
provider) is what lets Gradle infer the `compileJava -> generateAllcrud`
task dependency automatically - no explicit `dependsOn` needed. Same for
`compileTestJava` via the `"test"` source set.
