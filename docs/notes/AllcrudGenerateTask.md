# Technical notes — AllcrudGenerateTask

Forensic findings about Gradle's own behavior, not decisions about this
project's architecture.

## javaSourceDir/javaTestSourceDir — why they're separate properties from outputDir/testOutputDir

`javaSourceDir`'s convention is wired in `AllcrudGeneratorPlugin` from the
extension's `outputDir`, NOT from this task's own `getOutputDir()` - Gradle
disallows deriving one `@OutputDirectory` of a task from another
`@OutputDirectory` of that same task via `map()`/`flatMap()` ("Querying the
mapped value of a task output property before the task has completed is not
supported"), even when both ultimately share one upstream source.

Same reasoning for `getTestOutputDir()`/`getJavaTestSourceDir()`:
`getTestOutputDir()` is what `generate()` reads, `getJavaTestSourceDir()` is
what `AllcrudGeneratorPlugin` wires the `"test"` source set's java `srcDir`
from, both fed by the extension's single `testOutputDir` convention.

`outputDir` no longer appends `"src/main/java"` - see `AllcrudGeneratorPlugin`
for why `outputDir` already IS the Java source root now.
