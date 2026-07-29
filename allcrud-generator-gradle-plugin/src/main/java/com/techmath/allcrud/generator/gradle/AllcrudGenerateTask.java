package com.techmath.allcrud.generator.gradle;

import com.techmath.allcrud.generator.AllcrudGenerator;
import com.techmath.allcrud.generator.AllcrudGeneratorYamlConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

// Pure Gradle-lifecycle glue over AllcrudGenerator.generate(GenerationRequest) - no generation
// or config-parsing logic here, that's all AllcrudGeneratorYamlConfig (see generate() below).
public abstract class AllcrudGenerateTask extends DefaultTask {

    // javaSourceDir's convention is wired in AllcrudGeneratorPlugin from the extension's
    // outputDir, NOT from this task's own getOutputDir() - Gradle disallows deriving one
    // @OutputDirectory of a task from another @OutputDirectory of that same task via
    // map()/flatMap() ("Querying the mapped value of a task output property before the
    // task has completed is not supported"), even when both ultimately share one upstream
    // source. Also no longer appends "src/main/java" - see AllcrudGeneratorPlugin for why
    // outputDir already IS the Java source root now.

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSpecFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConfigFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @OutputDirectory
    public abstract DirectoryProperty getJavaSourceDir();

    @TaskAction
    public void generate() {
        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(getConfigFile().get().getAsFile().toPath());
        AllcrudGenerator.generate(config.toGenerationRequest(
                getSpecFile().get().getAsFile().toPath(),
                getOutputDir().get().getAsFile().toPath()));
    }

}
