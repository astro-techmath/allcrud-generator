package com.techmath.allcrud.generator;

// Only meaningful for GeneratedLayer.POJO (see GenerationRequest#pojoOnRegenerate) -
// Repository/Converter/Service/Controller are always "never overwrite", not configurable,
// per the allcrud-generator.yml design: onRegenerate only nests under the pojo layer.
public enum OnRegenerate {
    PRESERVE,
    OVERWRITE
}
