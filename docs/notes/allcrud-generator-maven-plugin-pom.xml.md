# Technical notes — allcrud-generator-maven-plugin/pom.xml

Forensic findings and non-obvious internal rationale, not decisions about
this project's own architecture.

## Separate Maven-built module, maven-plugin-plugin has no Gradle-native equivalent

Independent from the root Gradle multi-project build (see
`settings.gradle.kts` - it only includes allcrud-generator-gradle-plugin).
Generating a maven-plugin's `plugin.xml` requires the maven-plugin-plugin
descriptor goal, which runs as part of Maven's own lifecycle - there is no
Gradle-native equivalent.

## allcrud-generator dependency resolved from mavenLocal, same coordinate as the Gradle plugin

`AllcrudGenerator`/`AllcrudGeneratorYamlConfig` - the public generation API
this plugin wires into the Maven lifecycle. No generation logic lives here,
only the glue. Resolved from mavenLocal (published via the root Gradle
build's `publishToMavenLocal` - see `build.gradle.kts` there), same
coordinate the Gradle plugin consumes as `project(":")`.
