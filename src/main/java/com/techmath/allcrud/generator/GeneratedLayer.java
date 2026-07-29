package com.techmath.allcrud.generator;

// Stable vocabulary for the 5 layers this project generates - matches the
// allcrud-generator.yml naming (packages.pojo/repository/converter/service/controller,
// resources.<name>.generate, etc). "POJO" not "VO"/"DTO": the layer name must stay stable
// regardless of which PojoNamingStyle is selected (allcrudPojoClassName is the class-name
// concept this already mirrors internally).
public enum GeneratedLayer {
    POJO,
    REPOSITORY,
    CONVERTER,
    SERVICE,
    CONTROLLER
}
