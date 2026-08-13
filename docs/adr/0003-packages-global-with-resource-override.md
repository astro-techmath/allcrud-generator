# 3. Target packages are global by default, per-resource override is the escape hatch

## Context

Every generated layer (POJO, Repository, Converter, Service, Controller) needs a target Java
package to land in. A project with dozens or hundreds of resources almost always wants all of them
in the same 5 package paths (`com.acme.model`, `com.acme.repository`, etc.) - if the yml config
required declaring the package for every resource individually, that would be pure repeated noise
for the common case, with zero payoff.

## Decision

`generation.<layer>.package` is the **global default** package for that layer, declared once.
`resources.<name>.<layer>.package` is a **per-resource override**, only needed for the resources
that genuinely have to live somewhere else. Templates read the already-resolved package value
(global, overridden by per-resource if present) via generator-set additional properties - they
never read the raw global-package map directly, so template logic doesn't need to know about the
override mechanism at all.

## Consequences

- Adding a new standard resource to the spec requires zero extra yml beyond the resource itself -
  it inherits every global package default automatically.
- A layer that ends up enabled (globally or per-resource) with no resolvable package - neither a
  per-resource override nor a global default - fails fast at yml-load time, before any code
  generation is attempted.
- Package resolution is a two-level lookup (per-resource override wins, else global default) that
  every layer follows identically - there's no third, more specific override level.
