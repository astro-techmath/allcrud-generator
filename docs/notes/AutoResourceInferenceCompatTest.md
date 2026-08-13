# Technical notes — AutoResourceInferenceCompatTest

Forensic findings about external tooling behavior (openapi-generator), not
decisions about this project's own architecture.

## openapi-generator wraps postProcessOperationsWithModels' own exception in its own RuntimeException

openapi-generator's own per-tag api generation wraps whatever
`postProcessOperationsWithModels` throws in its own `RuntimeException` (same
wrapping proven empirically for the plain "no id" case, see the fail-fast's
own tests) - the `IllegalStateException` this project actually throws is the
root cause, not the top-level exception type a caller sees.
