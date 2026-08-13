# 4. Layers toggle independently but aren't semantically independent

## Context

Each of the 7 generated layers (POJO, Repository, Converter, Service, Controller, unitTest,
integrationTest) has its own `enabled` flag, settable globally and overridden per resource -
confirmed by grepping every one of the 6 non-POJO templates that a layer can genuinely depend on
another layer's generated class existing to compile: a Controller extends `CrudController<T, VO,
ID>` and needs a Service and Converter; a Service extends `CrudService<T, ID>` and needs a
Repository; an integration test extends a base class that references the Controller; and so on.
Independent toggles alone would let a user enable, say, Controller while leaving Service disabled -
producing generated code that references a class that doesn't exist.

## Decision

Layer *enablement* stays independent (each layer's own flag, global + per-resource), but layer
*dependencies* are validated eagerly, at yml-load time, before any generation happens:

- `CONVERTER` requires `POJO`.
- `CONTROLLER` requires `SERVICE`, `CONVERTER`, and `POJO`.
- `SERVICE` requires `REPOSITORY`.
- `INTEGRATION_TEST` requires `CONTROLLER`.
- `UNIT_TEST` requires `SERVICE`.

No separate `POJO` check exists for `INTEGRATION_TEST` despite `integrationTest.mustache`
referencing `POJO` directly - `CONTROLLER`'s own check already guarantees `POJO` is present
whenever `CONTROLLER` passes validation, and `INTEGRATION_TEST` already requires `CONTROLLER`. A
separate check would be dead code, never reachable.

## Consequences

- An invalid layer combination (e.g. `CONTROLLER` enabled with `SERVICE` disabled) fails fast with
  a message naming exactly which dependency is missing, instead of producing generated code that
  fails to compile later, far from the actual misconfiguration.
- The dependency chain is fixed and not configurable - there's no way to declare a Controller that
  doesn't need a Service.
