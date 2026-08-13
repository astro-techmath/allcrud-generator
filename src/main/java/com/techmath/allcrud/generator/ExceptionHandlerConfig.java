package com.techmath.allcrud.generator;

// Resolved allcrud-generator.yml's "exceptionHandler" block (AllcrudGeneratorYamlConfig) - the
// first "global" artifact this generator produces: one file per PROJECT, not one per resource
// like the 5 layers (see AllcrudGenerator#generateGlobalExceptionHandler, which runs once,
// outside the per-resource relocate() loop).
//
// enabled: see docs/adr/0007-exceptionhandler-opt-out-default.md. Without a
// GlobalExceptionHandler registered, Spring's default exception handling returns the wrong
// HTTP status for the exceptions AbstractGlobalExceptionHandler maps (EntityNotFoundException
// -> 500 instead of 404, etc) - a functional correction, not an opt-in feature. Named
// "enabled" (not "generate") to match the same key every "generation.<layer>" layer uses,
// even though exceptionHandler stays its own top-level yml section.
//
// targetPackage: null only when enabled is false - non-null and validated eagerly at yml load
// time otherwise (AllcrudGeneratorYamlConfig fails fast if it can't resolve one, rather than
// leaving AllcrudGenerator to discover a null package at generation time).
//
// className: always non-null, defaults to "GlobalExceptionHandler" when the yml doesn't set one.
//
// No onRegenerate knob, unlike POJO - see docs/adr/0001-generate-once-never-overwrite.md.
public record ExceptionHandlerConfig(boolean enabled, String targetPackage, String className) {
}
