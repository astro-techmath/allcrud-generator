# Technical notes — allcrud-generator-gradle-plugin/build.gradle.kts

Forensic findings and non-obvious internal rationale, not decisions about
this project's own architecture.

## springBootVersion BOM re-imported — doesn't propagate through project reference

The allcrud core artifact (see `project(":")` below) declares its Spring
Boot starters without explicit versions, managed by this same BOM in the
root module - it doesn't propagate transitively through `project(":")`, so
it's imported here too.

## Jackson floor — consumer constraint, dependencyManagement here, no generated POM customization

The Spring dependency-management plugin applies the Spring Boot BOM through
a resolution rule, and that rule wins over Gradle constraints: with only the
`constraints { }` block, `dependencyInsight` on `runtimeClasspath` still
showed `jackson-databind:2.21.5` with `Selected by rule` / `By constraint`,
so TestKit would have tested a classpath users never get. The same versions
(`jacksonVersion`, `jacksonAnnotationsVersion` in `gradle.properties`)
therefore also go in `dependencyManagement { dependencies { } }`, which fixes
this build's classpath to 2.22.3 (annotations 2.22).

The constraint is what Gradle publishes: `dependencyConstraints` in the
`.module` and `<dependencyManagement>` entries in the POM. The Spring plugin
would write the same entries into the POM a second time, which Maven reports
as `'dependencyManagement.dependencies.dependency.(groupId:artifactId:type:classifier)'
must be unique`, so `generatedPomCustomization` is disabled. That also drops
the `spring-boot-dependencies` import from this POM; nothing depends on it,
since the POM's only dependency (`allcrud-generator`) has an explicit version
and its own POM keeps its own imports. See ADR 0013.
