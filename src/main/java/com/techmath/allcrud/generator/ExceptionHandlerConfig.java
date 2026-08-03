package com.techmath.allcrud.generator;

// Resolved allcrud-generator.yml's "exceptionHandler" block (AllcrudGeneratorYamlConfig) - the
// first "global" artifact this generator produces: one file per PROJECT, not one per resource
// like the 5 layers (see AllcrudGenerator#generateGlobalExceptionHandler, which runs once,
// outside the per-resource relocate() loop).
//
// enabled: default true (opt-out) - unlike every other artifact in this project, which
// defaults to NOT generating unless asked. Deliberate: without a GlobalExceptionHandler
// registered, Spring's default exception handling returns the wrong HTTP status for the
// exceptions AbstractGlobalExceptionHandler maps (EntityNotFoundException -> 500 instead of
// 404, etc) - this is a functional correction to behavior the Controller (always generated)
// already implies, not an opt-in feature. Named "enabled", not "generate", to match the same
// key every one of the 7 "generation.<layer>" layers uses (see AllcrudGeneratorYamlConfig) -
// exceptionHandler stays its own top-level yml section (global, not per-resource, no layer
// dependency chain), just the key name lines up now.
//
// targetPackage: null only when enabled is false - non-null and validated eagerly at yml load
// time otherwise (AllcrudGeneratorYamlConfig fails fast if it can't resolve one, rather than
// leaving AllcrudGenerator to discover a null package at generation time).
//
// className: always non-null, defaults to "GlobalExceptionHandler" when the yml doesn't set one.
//
// No onRegenerate knob, unlike POJO - always "generate once, never overwrite", no exception:
// this class isn't derived from the OpenAPI contract (nothing to keep in sync with), and a dev
// is always expected to stack their own @ExceptionHandler methods on top of the generated stub.
public record ExceptionHandlerConfig(boolean enabled, String targetPackage, String className) {
}
