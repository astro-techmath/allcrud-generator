# 5. Per-resource `basePath` is a final absolute path, never concatenated

## Context

The default `@RequestMapping` base path for a resource's Controller is computed from
`routing.basePathPrefix` (a global default, e.g. `/api`) plus a path segment derived from the
resource name. Some resources need a base path that doesn't fit that convention at all (a legacy
route, a path with no relation to the resource's own name).

## Decision

`resources.<name>.basePath`, when set, is a **final, absolute** `@RequestMapping` path for that
resource. It **replaces** the computed default entirely - it is never concatenated with
`routing.basePathPrefix`. If a resource needs the prefix plus a custom suffix, the full path
(prefix included) has to be spelled out in `basePath` itself.

## Consequences

- There's no "prefix + custom suffix" mode - `basePath` is all-or-nothing once set.
- A resource with no `basePath` override always gets `routing.basePathPrefix` + the
  resource-name-derived path; a resource with an override ignores the prefix completely, including
  future changes to it.
