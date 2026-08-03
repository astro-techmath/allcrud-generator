package com.techmath.allcrud.generator.maven;

import com.techmath.allcrud.generator.AllcrudGenerator;
import com.techmath.allcrud.generator.AllcrudGeneratorYamlConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

// Pure Maven-lifecycle glue over AllcrudGenerator.generate(GenerationRequest) - no generation or
// config-parsing logic here, that's all AllcrudGeneratorYamlConfig. Mirrors
// AllcrudGenerateTask/AllcrudGeneratorExtension's parameter surface from the Gradle plugin - same
// 4 inputs, same defaults - but unlike Gradle (source sets inferred automatically from a task's
// declared @OutputDirectory), Maven has no such inference: outputDir/testOutputDir have to be
// registered on the MavenProject explicitly below, or compileJava/compileTestJava never see them.
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class AllcrudGenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "allcrud.specFile", required = true)
    private File specFile;

    @Parameter(property = "allcrud.configFile", defaultValue = "${project.basedir}/allcrud-generator.yml")
    private File configFile;

    @Parameter(property = "allcrud.outputDir", defaultValue = "${project.basedir}/src/main/java")
    private File outputDir;

    @Parameter(property = "allcrud.testOutputDir", defaultValue = "${project.basedir}/src/test/java")
    private File testOutputDir;

    @Override
    public void execute() throws MojoExecutionException {
        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(configFile.toPath());
        AllcrudGenerator.generate(config.toGenerationRequest(specFile.toPath(), outputDir.toPath(), testOutputDir.toPath()));

        project.addCompileSourceRoot(outputDir.getAbsolutePath());
        project.addTestCompileSourceRoot(testOutputDir.getAbsolutePath());
    }

}
