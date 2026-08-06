# Agent Instructions for Allcrud Generator

This file guides AI coding assistants (Claude Code, Cursor, Copilot, etc.)
working in this repository. It captures durable project discipline, not
session-specific state.

## Core principles

- **Verify empirically before assuming.** Don't assume how a library,
  framework, or tool behaves - read its source or test it directly. Several
  important decisions in this project changed direction only after this kind
  of verification (openapi-generator's pipeline ordering, Gradle's task
  output-tracking constraints, Spring's dependency resolution behavior). This
  applies to Spring, Hibernate, Gradle/Maven internals, openapi-generator,
  and any third-party dependency.

- **Real validation over assumption, for anything with runtime implications.**
  A change to bean registration, serialization, dependency injection, or any
  other Spring-context-dependent behavior in GENERATED code needs to be
  validated by actually generating code and loading it in a real Spring
  context (an external consumer project, real or throwaway), not just unit
  tests with mocks or reflection-only assertions. Compile-only success does
  not prove runtime correctness - several serious bugs in this project (bean
  registration, `@RestController` vs `@Controller`, request mapping) were
  only found this way. Each contributor can set this up however suits them
  (a throwaway Gradle/Maven project, an example repo, etc.).

- **Fail fast, with a clear message.** Prefer throwing a clear exception over
  silently ignoring invalid input, falling back unexpectedly, or producing
  output that "sort of" works. If something is misconfigured or unsupported,
  say so explicitly at the earliest point possible - this applies both to the
  generator's own config parsing and to the code it generates.

- **Hardcode before generalizing.** Don't add configuration options,
  abstraction layers, or parameterization for a use case that doesn't exist
  yet. Prove a concrete case works first; generalize only when a second real
  need appears. Avoid speculative flexibility (YAGNI).

- **SOLID, Clean Code, DRY, KISS.** Favor small, single-responsibility
  classes and methods. Avoid duplication, but don't abstract prematurely to
  avoid it (see above).

## Code style

- All code comments are in English, with no exceptions.
- Avoid the "it's not X, it's Y" rhetorical construction in comments,
  commit messages, and documentation - state things directly instead.

## Generator-specific discipline

- **Generated scaffolding is preserved, not overwritten, once it exists.**
  Repository/Converter/Service/Controller/exceptionHandler/unitTest/
  integrationTest are meant to carry hand-written logic after generation -
  never make a change that would silently overwrite an existing file for
  these layers. Only the POJO layer has a configurable overwrite policy, and
  it's opt-in (`preserve` is the default).
- **Every customized `.mustache` template needs a traceability comment** at
  the top, referencing the original embedded `openapi-generator` template
  path and the pinned generator version it was derived from.
- **Don't hardcode a name, type, or package in a template** that could
  instead come from a dynamically resolved variable (`allcrudEntityName`,
  `allcrudPojoClassName`, `allcrudIdType`, per-layer package variables). If a
  template needs a new piece of dynamic data, resolve it via
  `AllcrudSpringCodegen`, following the existing pattern - don't assume
  "same package" or any other unstated convention between layers.

## Testing

- New behavior needs tests. Runtime-affecting changes to generated code need
  validation with a real Spring context (an external consumer project,
  generated code compiled and run for real), not only mocked/reflection-based
  tests - "it compiles" alone is not sufficient proof for anything that
  touches bean wiring, serialization, or request handling in the generated
  output.