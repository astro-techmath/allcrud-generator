# Technical notes — AllcrudGeneratorYamlConfig

Forensic findings and non-obvious internal rationale, not decisions about
this project's own architecture. Architecture decisions live in `docs/adr/`;
this class's format itself is documented inline (the class-level Javadoc
YAML example stays inline, WHAT not WHY, no matter its length).

## YAML key name constants — deduplication was verified, not assumed (S1192)

Same literal, same meaning (a key in the parsed yml map) everywhere it
appears in this class - confirmed one by one before unifying into constants
(java:S1192): each was a real duplicate, not coincidentally-equal text with
a different meaning depending on context.

## requireResourceLayerHasPackage — why unitTest/integrationTest are exempt

Eager, per-resource check: a production layer enabled for THIS resource
(whether by its own override or by inheriting the global default) needs a
resolvable package - either this resource's own override, or the global
one. `unitTest`/`integrationTest` are exempt: they always have a dynamic
fallback available (the sibling service/controller package), never a
missing-package failure mode.

## parseOnRegenerateValue — only ever called for the pojo layer node

Absent `"onRegenerate"` -> `PRESERVE` default. Only ever called for the pojo
layer node (global `generation.pojo` or a resource's pojo override) -
`POJO_LAYER_ALLOWED_KEYS` is the only allowed-keys set that includes
`"onRegenerate"` at all, so a caller passing any other layer's node here
would already have failed `requireOnlyKeys` before reaching this method.

## requireOnlyKeys — whitelist, not a search for specific misplaced keys

Whitelists the keys allowed at a given node instead of hunting for specific
misplaced keys - this single check catches typos and any other unsupported
key uniformly, with one clear error message naming the exact offending
key(s) and location.
