# Technical notes — AutoResourceInferenceInternalsCompatTest

Forensic findings about external tooling behavior (openapi-generator's `ModelUtils`), not decisions about this project's own architecture.

## Dangling requestBody $ref falls through to false without throwing — the null comes from getSchemaFromContent, not getReferencedRequestBody

Confirmed by decompiling `openapi-generator-7.23.0.jar`'s
`ModelUtils` (bytecode, not assumed): `ModelUtils#getReferencedRequestBody`
does NOT return null for an unresolvable `$ref` - it falls back to
returning the original, unresolved `RequestBody` object itself (the `$ref`
stub), which is still non-null.

The null that actually makes `referencesSchema`'s `requestSchema` ternary
and `schemaName.equals(null)` check fall through to `false` safely comes
one step later: `ModelUtils#getSchemaFromRequestBody` calls
`requestBody.getContent()`, and the unresolved stub has no `content` set
(only `$ref`) - `ModelUtils#getSchemaFromContent(null)` returns null
because `content == null`, not because the reference itself failed to
resolve.

Net observable behavior is the same either way (no exception, falls
through to `false`), but the mechanism is one method later than it looks.
