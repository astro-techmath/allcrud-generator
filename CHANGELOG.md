# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-08-17

### Added
- Initial release
- Contract-first code generation from an OpenAPI spec plus an
  `allcrud-generator.yml` configuration file
- Generates 7 artifact types per resource: POJO (VO or DTO), Repository,
  Converter, Service, Controller, plus a project-level exception handler
  and unit/integration tests
- Gradle plugin and Maven plugin, both fully supported
- Per-layer target packages, with global defaults and per-resource
  overrides
- `x-allcrud-resource` / `x-allcrud-auto-resource` OpenAPI extensions to
  control which paths get full CRUD generation
- Generate-once, never-overwrite policy for hand-editable layers; POJOs
  support explicit regeneration
- Version-checked compatibility with the pinned `allcrud` core library

### Known Limitations
- Entity classes are not generated — they're hand-written and located via
  a source-tree scan
- Composite IDs (nested `$ref` in a schema's `id` property) are not
  supported yet
- Removing a resource from the spec doesn't clean up its previously
  generated files