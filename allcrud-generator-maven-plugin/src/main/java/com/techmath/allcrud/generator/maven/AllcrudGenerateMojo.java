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
// config-parsing logic here, that's all AllcrudGeneratorYamlConfig.
// See docs/notes/AllcrudGenerateMojo.md#no-automatic-source-set-inference--maven-needs-explicit-registration
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
