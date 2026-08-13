# 1. Generate once, never silently overwrite

## Context

Generated files for the Repository, Converter, Service, Controller, and exceptionHandler layers
land directly under the consumer's real source root (`src/main/java`, not `build/generated/...`),
because they're meant to be persisted and hand-edited after generation - this is the whole point
of a scaffolding generator: fill in the boilerplate once, let the developer take over from there.

If a second `generateAllcrud` run silently overwrote those files, any hand-written customization
(custom `@ExceptionHandler` methods, business logic added to a Service, etc.) would be destroyed
without warning the next time the generator runs - a scaffolding tool that can't be trusted not to
clobber your work stops being useful.

## Decision

Repository, Converter, Service, Controller, and the global exceptionHandler artifact **never
overwrite an existing file once generated** - there's no configuration knob to change this for any
of them, on purpose. If the file already exists at the target path, generation for that layer is a
no-op for that resource.

The POJO layer is the sole exception: it has its own `onRegenerate` setting
(`generation.pojo.onRegenerate`, overridable per resource), defaulting to `PRESERVE` (same
never-overwrite behavior as everything else) with `OVERWRITE` available as an explicit opt-in for
callers who want the POJO regenerated from the spec every time (e.g. because it's treated as pure
generated code, never hand-edited).

## Consequences

- Generated files are safe to hand-edit immediately after the first generation - a later
  `generateAllcrud` run won't touch them again for those 5 layers.
- Removing a resource from the OpenAPI spec does **not** remove its previously generated files -
  the generator only ever adds, it never deletes. Cleaning up after a removed resource is a manual
  step.
- Only POJO can be forced to regenerate (`onRegenerate: overwrite`), and only because it's the one
  layer expected to sometimes be treated as disposable, schema-derived code rather than a
  developer's own logic.
