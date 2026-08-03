package com.techmath.allcrud.generator.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskProvider;

public class AllcrudGeneratorPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "generateAllcrud";
    private static final String EXTENSION_NAME = "allcrudGenerator";

    @Override
    public void apply(Project project) {
        AllcrudGeneratorExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, AllcrudGeneratorExtension.class);
        // src/main/java, not build/generated/... - files here are meant to persist and be
        // hand-edited (Repository/Converter/Service/Controller never overwrite themselves),
        // so the default must survive clean/CI. This is also now the Java source root
        // directly (see javaSourceDir below), not an openapi-generator scratch output.
        extension.getOutputDir().convention(project.getLayout().getProjectDirectory().dir("src/main/java"));
        // Fixed, not configurable to be anywhere else in spirit (GenerationRequest#testSourceRoot
        // is a distinct field from sourceRoot, never merged) - only the DEFAULT is a convention
        // here, same as outputDir's, for consistency with how every other directory in this
        // plugin is wired; nothing downstream lets a caller point testOutputDir at outputDir.
        extension.getTestOutputDir().convention(project.getLayout().getProjectDirectory().dir("src/test/java"));
        // allcrud-generator.yml at the project root - same convention as checkstyle.xml,
        // detekt.yml etc: build/tooling config, not a packaged runtime resource.
        extension.getConfigFile().convention(project.getLayout().getProjectDirectory().file("allcrud-generator.yml"));

        TaskProvider<AllcrudGenerateTask> generateTask = project.getTasks().register(TASK_NAME, AllcrudGenerateTask.class, task -> {
            task.setGroup("allcrud");
            task.setDescription("Generates the Allcrud VO/Repository/Converter/Service/Controller stack from an OpenAPI spec.");
            task.getSpecFile().convention(extension.getSpecFile());
            task.getConfigFile().convention(extension.getConfigFile());
            task.getOutputDir().convention(extension.getOutputDir());
            // Derived from the extension's outputDir, not the task's own getOutputDir() -
            // see AllcrudGenerateTask's constructor comment for why. No longer appends
            // "src/main/java": GenerationRequest#sourceRoot (see AllcrudGenerator) IS now the
            // Java source root files land under directly - openapi-generator's own nested
            // src/main/java output layout only existed in the pre-staging design.
            task.getJavaSourceDir().convention(extension.getOutputDir());
            task.getTestOutputDir().convention(extension.getTestOutputDir());
            task.getJavaTestSourceDir().convention(extension.getTestOutputDir());
        });

        // Wiring the source set to the task's own output property (not a detached
        // provider) is what lets Gradle infer the compileJava -> generateAllcrud task
        // dependency automatically - no explicit dependsOn needed. Same for compileTestJava via
        // the "test" source set below.
        project.getPluginManager().withPlugin("java", appliedPlugin -> {
            JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            javaExtension.getSourceSets().getByName("main").getJava()
                    .srcDir(generateTask.flatMap(AllcrudGenerateTask::getJavaSourceDir));
            javaExtension.getSourceSets().getByName("test").getJava()
                    .srcDir(generateTask.flatMap(AllcrudGenerateTask::getJavaTestSourceDir));
        });
    }

}
