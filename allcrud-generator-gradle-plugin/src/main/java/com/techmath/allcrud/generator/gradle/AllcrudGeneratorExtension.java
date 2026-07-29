package com.techmath.allcrud.generator.gradle;

import com.techmath.allcrud.generator.PojoNamingStyle;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

// Configuration surface for the "allcrudGenerator { ... }" DSL block. specFile has no
// convention - it's mandatory, and AllcrudGeneratorPlugin fails the task with a clear
// message if it's left unset.
public abstract class AllcrudGeneratorExtension {

    public abstract RegularFileProperty getSpecFile();

    public abstract Property<PojoNamingStyle> getPojoNamingStyle();

    public abstract DirectoryProperty getOutputDir();

}
