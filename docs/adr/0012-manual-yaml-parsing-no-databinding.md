# 12. `allcrud-generator.yml` is parsed manually, not via data-binding

## Context

`allcrud-generator.yml` has many validation rules that need to point at a precise location in the
file when they fail (a specific layer's `enabled` key, a specific resource's package, an unknown
key at a specific nesting level) - a generic data-binding library (e.g. a Jackson YAML mapper onto
an annotated POJO) would produce generic type-mismatch or unknown-property errors that don't speak
in terms of this project's own config vocabulary, and wouldn't easily support the eager,
cross-field validation this file needs (layer dependency checks, package-resolvability checks -
see ADR 0004 and 0003).

## Decision

`AllcrudGeneratorYamlConfig` parses the yml manually: SnakeYAML loads it into plain
`Map<String, Object>`/`List<Object>` structures, and dedicated helpers (`requireMap`,
`requireString`, `requireBoolean`, `requireOnlyKeys`, `requireNonNull`) walk that structure by
hand, each one producing an error message naming the exact yml path and the exact problem.

## Consequences

- Every validation error is hand-crafted and precise (e.g. `"generation.controller is enabled but
  has no \"package\" configured"`), rather than a generic deserialization failure.
- The parsing code is more verbose than an annotated-POJO approach would be - traded deliberately
  for that error-message control and for supporting validation rules that span multiple fields at
  once.
