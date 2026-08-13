# 6. The JPA Entity is out of scope for V1

## Context

`CrudController<T, VO, ID>`, `CrudService<T, ID>`, and the generated Repository/Converter are all
generic over an entity type `T` - some concrete JPA `@Entity` class has to exist for the generated
code to compile against. Generating that entity automatically from the OpenAPI schema would mean
inferring JPA mapping decisions (relationships, column types, indexes, auditing fields) that the
schema alone doesn't fully specify, and that a consumer's own persistence model may already define
differently.

## Decision

allcrud-generator **never generates the Entity class**, in V1. It's hand-written by the consumer,
before running the generator. What the generator does do is *locate* it: it scans the configured
`sourceRoot` for a `<EntityName>.java` file matching the resource's schema name, to resolve its
package for imports in the generated Repository/Converter/Service/Controller. This lookup fails
fast, loudly, in 3 cases: the file doesn't exist anywhere under `sourceRoot`, more than one file
with that name exists (ambiguous - which one is "the" entity?), or the file's first line isn't a
well-formed `package` statement.

POJO generation is unaffected by this - see ADR 0008, POJO is schema-driven and doesn't need the
Entity to exist.

## Consequences

- The consumer must write the Entity class before running the generator, or generation fails fast
  with a clear "entity not found" message rather than producing code that doesn't compile.
- Removing a resource from the spec doesn't clean up its Entity either - same "generator only ever
  adds" limitation as ADR 0001.
- An ambiguous entity name (2+ files with the same simple name anywhere under `sourceRoot`) is a
  hard error, never a silent pick-the-first-one.
