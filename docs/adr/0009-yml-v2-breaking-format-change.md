# 9. `allcrud-generator.yml` v2 deliberately breaks the v1 format

## Context

The original (v1) `allcrud-generator.yml` shape used top-level `packages`/`defaults` blocks and a
per-resource `generate: [...]` list that fully replaced the default layer set (all-or-nothing per
resource, no way to say "disable just this one layer"). As the config's responsibilities grew
(per-layer `enabled`/`package`/`onRegenerate`, the exceptionHandler block, routing prefix), the v1
shape couldn't express independent per-layer toggling (see ADR 0004) without an awkward,
error-prone redesign bolted on top of the old structure.

## Decision

The current (v2) shape - `generation.<layer>.{enabled, package, onRegenerate}` globally, mirrored
per-resource under `resources.<name>.<layer>`, plus dedicated `routing` and `exceptionHandler`
blocks - **deliberately breaks compatibility** with the v1 `packages`/`defaults`/`generate: [...]`
format. There is no migration shim or dual-format support.

## Consequences

- Any project's `allcrud-generator.yml` written against the v1 format must be manually rewritten
  to v2 - there's no automatic upgrade path.
- The validation and parsing code (`AllcrudGeneratorYamlConfig`) only ever has one format to
  support, keeping the manual SnakeYAML walking (see ADR 0012) and its per-key error messages
  simpler than a dual-format parser would allow.
