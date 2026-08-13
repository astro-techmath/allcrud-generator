# 7. The global exceptionHandler defaults to enabled (opt-out), unlike every other artifact

## Context

`exceptionHandler` is the one project-level artifact allcrud-generator produces - a single
`GlobalExceptionHandler` generated once per project (extending
`AbstractGlobalExceptionHandler`), not once per resource like the other 5 layers. Every other
artifact in this project follows an explicit-marker-or-config convention to be generated at all
(a resource needs to exist and have its layer flags set). A generated API without any exception
handling wired up is a real, easy-to-hit gap by omission - most Spring Boot projects want
consistent error responses without an extra manual step.

## Decision

The `exceptionHandler` block in `allcrud-generator.yml` defaults to `enabled: true` - it generates
by default, with no section needed in the yml at all, unlike every other artifact in this project.
Its target package falls back to `generation.controller.package` when not declared explicitly, and
its class name defaults to `GlobalExceptionHandler` when not declared. It can be turned off with
`exceptionHandler.enabled: false` for a project that wants to wire its own exception handling
independently.

## Consequences

- A project with no `exceptionHandler` section at all still gets a `GlobalExceptionHandler`
  generated, as long as `generation.controller.package` (or an explicit
  `exceptionHandler.package`) resolves to something.
- If `exceptionHandler.enabled` is true (the default) and no package can be resolved for it -
  Controller layer disabled globally, and no explicit `exceptionHandler.package` declared either -
  generation fails fast with a clear message, rather than silently skipping the handler.
