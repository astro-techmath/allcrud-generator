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

/**
 * Pure Maven-lifecycle glue: generation in {@link AllcrudGenerator#generate},
 * config loading in {@link AllcrudGeneratorYamlConfig}.
 * See docs/notes/AllcrudGenerateMojo.md#no-automatic-source-set-inference--maven-needs-explicit-registration
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class AllcrudGenerateMojo extends AbstractMojo {

    /**
     * Current Maven project; used to register generated source roots after generation.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * OpenAPI specification file to generate from.
     */
    @Parameter(property = "allcrud.specFile", required = true)
    private File specFile;

    /**
     * allcrud-generator.yml config file (defaults to {@code ${project.basedir}/allcrud-generator.yml}).
     */
    @Parameter(property = "allcrud.configFile", defaultValue = "${project.basedir}/allcrud-generator.yml")
    private File configFile;

    /**
     * Directory for generated main sources (defaults to {@code ${project.basedir}/src/main/java}).
     */
    @Parameter(property = "allcrud.outputDir", defaultValue = "${project.basedir}/src/main/java")
    private File outputDir;

    /**
     * Directory for generated test sources (defaults to {@code ${project.basedir}/src/test/java}).
     */
    @Parameter(property = "allcrud.testOutputDir", defaultValue = "${project.basedir}/src/test/java")
    private File testOutputDir;

    /**
     * Runs Allcrud generation and registers the output directories as compile and test source roots.
     *
     * @throws MojoExecutionException if generation fails
     */
    @Override
    public void execute() throws MojoExecutionException {
        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(configFile.toPath());
        AllcrudGenerator.generate(config.toGenerationRequest(specFile.toPath(), outputDir.toPath(), testOutputDir.toPath()));

        project.addCompileSourceRoot(outputDir.getAbsolutePath());
        project.addTestCompileSourceRoot(testOutputDir.getAbsolutePath());
    }

}
