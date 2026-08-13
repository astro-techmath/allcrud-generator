package com.techmath.allcrud.generator.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;

// Configuration surface for the "allcrudGenerator { ... }" DSL block. specFile has no
// convention - it's mandatory, and the task fails with a clear message if it's left unset.
//
// See docs/notes/AllcrudGeneratorExtension.md#no-pojonamingstyle-property-here-there-used-to-be-one
public abstract class AllcrudGeneratorExtension {

    public abstract RegularFileProperty getSpecFile();

    public abstract RegularFileProperty getConfigFile();

    public abstract DirectoryProperty getOutputDir();

    // See docs/adr/0010-test-source-root-separate.md
    public abstract DirectoryProperty getTestOutputDir();

}
