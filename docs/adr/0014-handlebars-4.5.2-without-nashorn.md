# 14. Handlebars 4.5.2 without nashorn-core

## Context

Handlebars (`com.github.jknack:handlebars`) reaches the generator transitively through
`openapi-generator` 7.26.0, which resolved 4.3.1. That version is affected by
GHSA-r4gv-qr8j-p3pg (CVE-2026-55760, high): path traversal when `Handlebars.compile()` receives a
user-controlled name with `FileTemplateLoader` or `ClassPathTemplateLoader`. The only patched
version is 4.5.2.

The vulnerable code is not reachable here. The generator uses `.mustache` templates and never sets
a templating engine, so `openapi-generator` uses its Mustache adapter. Its Handlebars adapter uses
its own string-based template loader, never the affected loaders. A generation run with
`-verbose:class` loaded no `com.github.jknack` class.

Handlebars 4.5.2 adds `nashorn-core` 15.4 (with five `org.ow2.asm` 7.3.1 modules) and
`commons-text` 1.15.0. Nashorn is used only to register JavaScript helpers, which neither the
generator nor `openapi-generator` does. `commons-text` is also a direct dependency of
`openapi-generator` (1.10.0), so it cannot be dropped.

## Decision

Each published artifact declares a Handlebars 4.5.2 floor, and the root module excludes
`org.openjdk.nashorn:nashorn-core` from `openapi-generator`, which also removes the asm modules.
`commons-text` is left as resolved (revisited in #48).

- `handlebarsVersion` in `gradle.properties`, read by the root and Gradle plugin builds as a
  Gradle `constraints { }` entry, published in `.module` and in the POM's
  `<dependencyManagement>`.
- `handlebars.version` in the Maven plugin's `pom.xml`, in its `<dependencyManagement>`.
- The exclusion is published in the root `.module` and POM.

Maintainer decision:
https://github.com/astro-techmath/allcrud-generator/issues/39#issuecomment-6256755279

## Alternatives

- Stay on 4.3.1: no new artifacts, but the advisory stays open.
- 4.5.2 without exclusions: adds a JavaScript engine and asm 7.3.1 to every consumer's classpath
  for a feature nobody uses, and needs more verification metadata entries.
- An intermediate version: none exists. 4.4.0, 4.5.0 and 4.5.1 are all inside the affected range.

## Consequences

Measured with the locally published 0.1.0:

- Gradle plugin and Maven plugin users resolve Handlebars 4.5.2 without `nashorn-core` or asm.
- Gradle builds that depend on `allcrud-generator` directly resolve 4.5.2 without `nashorn-core`.
- Maven builds that depend on `allcrud-generator` directly resolve 4.3.1, because Maven does not
  apply a dependency's dependencyManagement transitively. The exclusion still applies. They get
  4.5.2 by managing `com.github.jknack:handlebars` themselves.
- `commons-text` resolves 1.15.0 on Gradle and 1.10.0 in the Maven plugin.
- Generated output is byte-identical to the 4.3.1 baseline in both plugins.
- New verification metadata entries are needed for `handlebars` 4.5.2, `handlebars.java` 4.5.2,
  `commons-text` 1.15.0 and `commons-parent` 93.
