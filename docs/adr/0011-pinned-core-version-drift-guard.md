# 11. The `allcrud` core version is pinned, with a test guarding against silent drift

## Context

allcrud-generator's templates assume a specific shape for the `allcrud` core library's base
classes - `CrudController<T, VO, ID>` and `CrudService<T, ID>`'s abstract methods and type
parameter counts, `AbstractEntityVO`'s interface contract, and so on. `allcrud` is consumed as a
real published dependency (Maven Central coordinate, not a `project(...)` reference - it lives in
a separate repository/build entirely), so nothing in this build would normally catch it if a
future `allcrud` release changed that shape.

## Decision

`allcrudCoreVersion` (`gradle.properties`) pins an exact `allcrud` version. `CoreDependencyCompatTest`
exists specifically to guard against silent drift: it reflects on the pinned version's actual
classes at test time and asserts the shape the generator's templates depend on still holds
(`CrudController` is abstract with 3 type parameters and the expected abstract hooks,
`CrudService` with 2 and its own hook, `AbstractEntityVO` is an interface with `getId`/`setId`).

A separate, manually-invoked `checkCoreUpdates` Gradle task checks Maven Central's metadata for a
newer `allcrud` version than the one pinned - a reminder to go look, not an automatic bump.

## Consequences

- Bumping `allcrudCoreVersion` without `CoreDependencyCompatTest` passing means the generator's
  templates may now produce code that doesn't compile against the new core version - the test is
  the first, fast signal for that, well before a real end-to-end generation + compile would catch
  it.
- The version bump itself is always a deliberate, manual edit to `gradle.properties` - nothing
  bumps it automatically.
