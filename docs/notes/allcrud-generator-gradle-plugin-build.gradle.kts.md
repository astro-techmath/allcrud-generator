# Technical notes — allcrud-generator-gradle-plugin/build.gradle.kts

Forensic findings and non-obvious internal rationale, not decisions about
this project's own architecture.

## springBootVersion BOM re-imported — doesn't propagate through project reference

The allcrud core artifact (see `project(":")` below) declares its Spring
Boot starters without explicit versions, managed by this same BOM in the
root module - it doesn't propagate transitively through `project(":")`, so
it's imported here too.
