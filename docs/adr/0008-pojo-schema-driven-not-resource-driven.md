# 8. POJO generation is schema-driven, not resource-driven

## Context

Controller/Service/Repository/Converter only make sense for a confirmed CRUD resource (see ADR
0002) - they're generic over an entity type and need a real Entity, Service, Repository chain to
compile. A POJO (VO/DTO), on the other hand, is just a plain data-holder class derived directly
from an OpenAPI schema - it has no such dependency, and plenty of schemas exist in a spec purely as
request/response shapes without ever being a "resource" in the CRUD sense (e.g. a paginated
wrapper schema, or a schema used only by a non-resource endpoint).

## Decision

POJO generation is driven by **schema presence**, not by whether the corresponding path is marked
or inferred as an `x-allcrud-resource`. Every schema encountered gets its POJO generated
(subject to `generation.pojo.enabled` / `resources.<name>.pojo.enabled`), independent of the
resource gate that controls the other 4 layers.

## Consequences

- A schema with no `id` property, or belonging to a path never marked/inferred as a resource,
  still gets its POJO generated - even though Controller/Service/Repository/Converter can't be
  (no usable ID type, or no gate opened for it).
- Disabling a resource's path-level generation (`x-allcrud-resource: false`, or simply never
  marking it) does not stop its POJO from being generated - only the POJO layer's own `enabled`
  flag does that.
- The gate is enforced at relocation time, not generation time: openapi-generator's staging pass
  still renders Controller/Service/Repository/Converter for every tag unconditionally (its own
  pipeline isn't fought here), including tags that never resolved to a confirmed resource. Those
  staged files are simply discarded before reaching `sourceRoot` (`AllcrudGenerator#relocateOne`)
  instead of ever being generated conditionally - harmless, since the whole staging directory is
  thrown away regardless once relocation finishes.
