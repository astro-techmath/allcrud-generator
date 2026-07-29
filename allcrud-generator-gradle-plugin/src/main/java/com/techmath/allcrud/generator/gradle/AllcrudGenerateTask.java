package com.techmath.allcrud.generator.gradle;

import com.techmath.allcrud.generator.AllcrudGenerator;
import com.techmath.allcrud.generator.GenerationRequest;
import com.techmath.allcrud.generator.PojoNamingStyle;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

// Pure Gradle-lifecycle glue over AllcrudGenerator.generate(GenerationRequest) - no
// generation logic here. generateServiceLayer is intentionally NOT exposed as a task
// input: V1 always generates the full stack (VO+Repository+Converter+Service+Controller).
// Per-layer selection is deferred to the not-yet-designed allcrud-generator.yml, which
// will configure GenerationRequest directly - no throwaway boolean in between.
public abstract class AllcrudGenerateTask extends DefaultTask {

    // javaSourceDir's convention is wired in AllcrudGeneratorPlugin from the extension's
    // outputDir, NOT from this task's own getOutputDir() - Gradle disallows deriving one
    // @OutputDirectory of a task from another @OutputDirectory of that same task via
    // map()/flatMap() ("Querying the mapped value of a task output property before the
    // task has completed is not supported"), even when both ultimately share one
    // upstream source. outputDir is the raw openapi-generator output root; the actual
    // Java sources land under <outputDir>/src/main/java - see AllcrudGeneratorPlugin for
    // why javaSourceDir (not outputDir) is what gets wired into sourceSets.main.java.
    //
    // pojoNamingStyle's VO default lives on the extension (AllcrudGeneratorPlugin), not
    // here - task.getPojoNamingStyle().convention(extension.getPojoNamingStyle()) replaces
    // any convention set on this property, it doesn't fall back to it.

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSpecFile();

    @Input
    public abstract Property<PojoNamingStyle> getPojoNamingStyle();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @OutputDirectory
    public abstract DirectoryProperty getJavaSourceDir();

    @TaskAction
    public void generate() {
        AllcrudGenerator.generate(new GenerationRequest(
                getSpecFile().get().getAsFile().toPath(),
                getOutputDir().get().getAsFile().toPath(),
                getPojoNamingStyle().get(),
                true
        ));
    }

}
