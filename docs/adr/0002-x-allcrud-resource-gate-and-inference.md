# 2. `x-allcrud-resource` is the real generation gate; `x-allcrud-auto-resource` inference defaults to off

## Context

Early on, any OpenAPI path whose response/request schema happened to have an `id` property got
full CRUD scaffolding (Controller/Service/Repository/Converter) generated for it, regardless of
whether that was actually wanted - `x-allcrud-resource: false` was a no-op, and the marker itself
was purely decorative. That's a real gap: a path that happens to have an id-bearing schema but was
never meant to be a CRUD resource would still get generated for.

Separately, requiring every single resource path to be hand-marked with `x-allcrud-resource: true`
is repetitive for specs where the collection+item URL shape (`/widgets` + `/widgets/{id}`) already
makes the resource obvious - automatic inference from that shape removes the busywork, but
silently turning inference on by default would change what an existing, unmodified spec generates
the moment the generator itself is upgraded, with no action taken by the spec's author.

## Decision

`x-allcrud-resource` (a per-path vendor extension) is the actual, load-bearing gate on whether
Controller/Service/Repository/Converter get generated for a path pair - not just a label. A schema
with an `id` property but no `x-allcrud-resource` marker (explicit or inferred) does not get those
4 layers generated.

`x-allcrud-auto-resource: true` at the OpenAPI document root turns on automatic inference: a
collection/item path pair matching the `CrudController` shape gets `x-allcrud-resource` marked for
it automatically, unless the path explicitly opts out with `x-allcrud-resource: false`. This
inference flag **defaults to `false`** - the generator has always required explicit marking, so
defaulting inference to on would silently change generation output for pre-existing specs the
moment inference shipped. Turning it on is always an explicit, visible choice in the spec itself.

Explicit `x-allcrud-resource: true` on its own, without the auto-resource flag, keeps working
exactly as before - inference is additive, not a replacement.

## Consequences

- A path matching the collection/item shape generates nothing unless `x-allcrud-auto-resource:
  true` is set at the document root, or the path is explicitly marked.
- A resource marked/inferred as `x-allcrud-resource` but whose schema has no usable `id` property
  fails fast at generation time (can't satisfy the generic `CrudController<T, VO, ID>` shape) -
  it's an error, not a silently-skipped resource.
- POJO generation is unaffected by this gate entirely - see ADR 0008.
