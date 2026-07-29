package com.techmath.allcrud.generator.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;

// Configuration surface for the "allcrudGenerator { ... }" DSL block. specFile has no
// convention - it's mandatory, and the task fails with a clear message if it's left unset.
//
// No pojoNamingStyle property here (there used to be one): allcrud-generator.yml (see
// configFile) is now the single source of truth for it, packages, defaults and resources -
// keeping a second, independent pojoNamingStyle knob on the extension would have been the
// same duplicated-authority problem already fixed for allcrudIdType and
// generateServiceLayer.
public abstract class AllcrudGeneratorExtension {

    public abstract RegularFileProperty getSpecFile();

    public abstract RegularFileProperty getConfigFile();

    public abstract DirectoryProperty getOutputDir();

}
