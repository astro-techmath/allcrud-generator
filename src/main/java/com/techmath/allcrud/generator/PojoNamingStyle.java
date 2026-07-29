package com.techmath.allcrud.generator;

// Mirrors the "VO"/"DTO" values AllcrudSpringCodegen#processOpts reads from the
// allcrudPojoNamingStyle additional property - see AllcrudGenerator#generate, which
// passes name() through.
public enum PojoNamingStyle {
    VO,
    DTO
}
