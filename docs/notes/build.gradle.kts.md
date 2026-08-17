# Technical notes — build.gradle.kts (root)

Forensic findings and non-obvious internal rationale, not decisions about
this project's own architecture.

## sonar.coverage.jacoco.xmlReportPaths — multi-project build needs both paths listed

The allcrud-generator-gradle-plugin subproject has its own
jacocoTestReport.xml - Sonar treats a multi-project build as one analysis,
so both paths need to be listed (comma-separated, relative to the project
root) or the subproject's classes never get matched to a coverage report at
all - confirmed against Sonar's own docs on this property, not assumed.

## Coverage exclusions — IoExceptions and TestKit-forked tasks show 0% for different reasons

`IoExceptions` is already excluded from the JaCoCo XML itself (see the
`jacocoTestReport.classDirectories` exclusion below), but that only
controls what JaCoCo reports - Sonar reads the same XML and, for any class
absent from it, treats "absent" as "0% covered" rather than "excluded on
purpose". Those are different concepts to Sonar, so the exclusion has to be
declared here too, not just at the JaCoCo level.

`AllcrudGenerateTask`/`AllcrudGeneratorPlugin` (allcrud-generator-gradle-plugin
subproject) are genuinely exercised for real, not untested - see
`AllcrudGeneratorPluginFunctionalTest`, which applies the plugin and runs
both `generateAllcrud` and `build` via real GradleRunner/TestKit. They show
0% anyway because TestKit forks a separate Gradle daemon by default,
outside the JaCoCo agent attached to that test's own JVM. The documented
fix (`GradleRunner.withDebug(true)`, running the build in-process instead)
was tried and confirmed broken here: `withPluginClasspath()` under
in-process execution doesn't expose the plugin's classes to the fixture's
own buildscript compilation (an "Unresolved reference" on the plugin's own
extension type, not just its Kotlin DSL accessor sugar) - a real
TestKit/Kotlin-DSL limitation, not a gap this project's own tests can close
without risking the passing tests that already prove this code works.

## snakeyaml declared explicitly despite already being a transitive dependency

`allcrud-generator.yml` parsing (`AllcrudGeneratorYamlConfig`) - already
resolved transitively via openapi-generator's own dependency tree
(confirmed via `./gradlew dependencies --configuration compileClasspath`),
declared explicitly here instead of relying on that accidental transitive
availability.

## allcrud core test dependency scope — testImplementation avoids leaking Spring starters

Only `src/test/java` references allcrud core classes (reflection +
compiling generated fixtures against it) - main never does. Keeping this as
`implementation` pulled it into allcrud-generator-gradle-plugin's own
plugin classpath via `implementation(project(":"))`, needlessly dragging in
allcrud's runtime-scope Spring starters (unversioned by design - see
allcrud's own POM) into plugin resolution.

## testFixtures dependency — confirmed via javap, not assumed

`CrudServiceTests`/`CrudControllerIntegrationTests` (the base classes
`unitTest.mustache`/`integrationTest.mustache` extend) live in allcrud's
test-fixtures variant, not its main jar - needed here so generated
`*ServiceTest`/`*ControllerIT` compat tests (compiled for real via
`javax.tools.JavaCompiler`, not just reflected on) actually have something
to extend. Confirmed via `javap` against the fixtures jar directly, not
assumed.

## IoExceptions JaCoCo exclusion — excluded as its own class, not blanket

`IoExceptions` (`com.techmath.allcrud.generator`) is a single-purpose
wrapper around `catch (IOException e) { throw new
UncheckedIOException(e); }` - defensive I/O-failure plumbing with no
branching logic of its own, only reachable by mocking disk failure.
Excluded as its own dedicated class (not a blanket class-level exclusion
elsewhere) so every other line in `AllcrudGenerator`/
`AllcrudGeneratorYamlConfig` stays subject to the project's
100%-coverage goal.

## sonar task dependsOn jacocoTestReport — accidental ordering bug, same class as allcrud's fix

The sonar task only picked up `jacocoTestReport.xml` by accident, when a CI
step happened to run `./gradlew build` (which produces it via `test`'s
`finalizedBy`) before a separate `./gradlew sonar` step - confirmed
empirically via `./gradlew :sonar --dry-run`, which shows no dependency on
`jacocoTestReport` in the task graph. Same bug class as the
`plainJavadocJar`/`generateMetadataFile` issue already fixed in allcrud: a
task consuming another task's output without declaring it. Also needs the
subproject's own `jacocoTestReport` (see `sonar.coverage.jacoco.xmlReportPaths`
above) for the same reason - it's a separate task in a separate project,
with no dependency on it otherwise.

## Minimal publishing setup — only for publishToMavenLocal, not real Central publishing

Just enough for allcrud-generator-gradle-plugin's `implementation(project(":"))`
to resolve as a real coordinate via `publishToMavenLocal`. Not a statement
about real (Central) publishing, which is a separate decision.
