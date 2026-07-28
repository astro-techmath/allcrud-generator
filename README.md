# allcrud-generator

Contract-first codegen for the Allcrud framework. Reads an OpenAPI spec and generates
VO/Repository/Converter/Service/Controller skeletons that extend Allcrud's base classes
(`AbstractEntityVO`, `CrudController`, `CrudService`), instead of generic Spring code.

Lives in its own repo/artifact so it never becomes a transitive dependency of projects
that only consume `allcrud` at runtime.

## Core version compatibility

This generator targets a pinned version of the `allcrud` core, declared in
`gradle.properties` (`allcrudCoreGroup` / `allcrudCoreArtifact` / `allcrudCoreVersion`).
Never a version range.

Compat check has two parts:

1. **Automated, every push**: build-time test resolves the pinned core version and
   verifies its base classes (`AbstractEntityVO`, `CrudController`, `CrudService`)
   exist and expose the expected shape.
2. **TODO**: manual/scheduled job to detect when a newer `allcrud` core version is
   published, so `allcrudCoreVersion` can be bumped deliberately and the compat
   check re-run before adopting it.
