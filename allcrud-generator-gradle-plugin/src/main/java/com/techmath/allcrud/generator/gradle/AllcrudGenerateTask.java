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
import org.gradle.api.tasks.UntrackedTask;

// Pure Gradle-lifecycle glue over AllcrudGenerator.generate(GenerationRequest) - no generation
// or config-parsing logic here, that's all AllcrudGeneratorYamlConfig (see generate() below).
@UntrackedTask(because = "generate() reads pre-existing file state under outputDir/testOutputDir "
        + "(the generate-once-never-overwrite policy) that isn't captured by the declared "
        + "@InputFile/@OutputDirectory properties - caching this task could replay a stale "
        + "result after those files changed outside Gradle's view")
public abstract class AllcrudGenerateTask extends DefaultTask {

    // See docs/notes/AllcrudGenerateTask.md#javasourcedirjavatestsourcedir--why-theyre-separate-properties-from-outputdirtestoutputdir

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

    // See docs/notes/AllcrudGenerateTask.md#javasourcedirjavatestsourcedir--why-theyre-separate-properties-from-outputdirtestoutputdir
    @OutputDirectory
    public abstract DirectoryProperty getTestOutputDir();

    @OutputDirectory
    public abstract DirectoryProperty getJavaTestSourceDir();

    @TaskAction
    public void generate() {
        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(getConfigFile().get().getAsFile().toPath());
        AllcrudGenerator.generate(config.toGenerationRequest(
                getSpecFile().get().getAsFile().toPath(),
                getOutputDir().get().getAsFile().toPath(),
                getTestOutputDir().get().getAsFile().toPath()));
    }

}
