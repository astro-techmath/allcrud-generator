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

## Jackson floor — dependencyManagement in the plugin POM reaches the plugin realm

Maven resolves a plugin's dependencies with the plugin as the resolution
root, so this POM's `<dependencyManagement>` (`jackson.version`) applies to
the class realm users get. Confirmed on Maven 3.9.11 with a throwaway
consumer: `mvn -X ...:generate` lists `jackson-core`, `jackson-databind`
and `jackson-dataformat-yaml` 2.22.3 "(version managed from default)" in
`Populating class realm plugin>...`; `jackson-annotations` is managed too
(`jackson.annotations.version`, 2.22), so the realm gets 2.22 instead of
the 2.21 that `openapi-generator` would bring. The Maven module can't read
`gradle.properties`, so this property is its own source of the version.
See ADR 0013.
