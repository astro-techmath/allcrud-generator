# Technical notes — AllcrudGenerateMojo

Forensic findings about Maven's own behavior, contrasted with Gradle's.

## No automatic source-set inference — Maven needs explicit registration

Mirrors `AllcrudGenerateTask`/`AllcrudGeneratorExtension`'s parameter
surface from the Gradle plugin - same 4 inputs, same defaults - but unlike
Gradle (source sets inferred automatically from a task's declared
`@OutputDirectory`), Maven has no such inference: `outputDir`/`testOutputDir`
have to be registered on the `MavenProject` explicitly (via
`addCompileSourceRoot`/`addTestCompileSourceRoot`), or
`compileJava`/`compileTestJava` (their Maven equivalents) never see them.
