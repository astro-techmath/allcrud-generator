# Technical notes — IoExceptions

Forensic findings and non-obvious internal rationale, not decisions about
this project's own architecture.

## Public visibility — shared across 2 packages, not a public API surface

Public only because it's shared across `com.techmath.allcrud.generator` and
`com.techmath.allcrud.generator.codegen` (`AllcrudSpringCodegen`) - not
otherwise part of this module's public API surface in spirit. Hosts every
defensive "checked IOException -> unchecked" wrapper in the module in one
place, deliberately excluded from JaCoCo (see `build.gradle.kts`): these
branches are only reachable by mocking a real filesystem failure (a full
disk, a permissions error mid-run), which none of this project's real,
meaningful tests do - they exercise real filesystems directly instead.

## createTempDirectory — java:S5443 is a false positive here

`java.nio.file.Files#createTempDirectory` (NIO.2, since JDK 7) already
restricts the created directory to owner-only permissions (`rwx------` on
POSIX) by default when no `FileAttribute` is passed - confirmed against the
JDK's own documented behavior, not the older, genuinely-unsafe
`File#createTempFile`/`mkdir` pattern this rule is designed to catch.
