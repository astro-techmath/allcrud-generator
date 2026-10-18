# 13. Published Jackson floor via dependency constraints, not a BOM

## Context

All Jackson modules reach the generator transitively through `openapi-generator` (via
`swagger-parser`). Before this change, consumers of the published plugins resolved vulnerable
versions: the Gradle plugin's own classpath was forced down to 2.21.5 by the Spring Boot BOM, and
the Maven plugin resolved `jackson-core` and `jackson-databind` 2.21.4 (with
`jackson-dataformat-yaml` 2.22.1). Neither plugin's published `.pom` or `.module` mentioned
Jackson. A BOM imported at build time is not seen by consumers. Publishing `jackson-bom` as a
platform was considered and declined for 0.1.0.

## Decision

Each published artifact declares a floor of Jackson 2.22.3 for `jackson-core`,
`jackson-databind` and `jackson-dataformat-yaml`, and 2.22 for `jackson-annotations`. The versions
live in two properties per build: `jacksonVersion` and `jacksonAnnotationsVersion` in
`gradle.properties`, and `jackson.version` and `jackson.annotations.version` in the Maven plugin's
`pom.xml`.

- Root and Gradle plugin: Gradle `constraints { }` with a `because(...)` naming the advisories,
  so the floor is published in `.module` and in the POM's `<dependencyManagement>`.
- Gradle plugin: the same versions also go in its Spring `dependencyManagement { }` block, because
  that plugin's resolution rule overrides Gradle constraints on its own classpath. Its
  `generatedPomCustomization` is disabled, so the published POM lists each Jackson entry once.
- Maven plugin: a `<dependencyManagement>` section in `pom.xml`.

This change publishes no new platform or BOM; the root POM's existing jackson-bom and
spring-boot-dependencies imports are unchanged.

## Consequences

- Gradle and Maven consumers resolve Jackson 2.22.3 (annotations 2.22) or newer without extra
  configuration, and Maven raises no duplicate-declaration warning.
- The Gradle plugin's POM no longer imports `spring-boot-dependencies`; its only dependency,
  `allcrud-generator`, carries an explicit version.
- Raising the floor means changing the two properties per build, plus the verification metadata
  when new artifacts appear.
