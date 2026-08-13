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
        // src/main/java, not build/generated/... - see docs/adr/0001-generate-once-never-overwrite.md
        // This is also now the Java source root directly (see javaSourceDir below), not an
        // openapi-generator scratch output.
        extension.getOutputDir().convention(project.getLayout().getProjectDirectory().dir("src/main/java"));
        // See docs/adr/0010-test-source-root-separate.md - only the DEFAULT is a convention
        // here, same as outputDir's; nothing downstream lets a caller point testOutputDir at
        // outputDir.
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
            // See docs/notes/AllcrudGeneratorPlugin.md#javasourcedir-convention--derived-from-the-extension-not-the-tasks-own-output
            task.getJavaSourceDir().convention(extension.getOutputDir());
            task.getTestOutputDir().convention(extension.getTestOutputDir());
            task.getJavaTestSourceDir().convention(extension.getTestOutputDir());
        });

        // See docs/notes/AllcrudGeneratorPlugin.md#wiring-the-source-set-to-the-tasks-own-output-property-enables-automatic-task-dependency
        project.getPluginManager().withPlugin("java", appliedPlugin -> {
            JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            javaExtension.getSourceSets().getByName("main").getJava()
                    .srcDir(generateTask.flatMap(AllcrudGenerateTask::getJavaSourceDir));
            javaExtension.getSourceSets().getByName("test").getJava()
                    .srcDir(generateTask.flatMap(AllcrudGenerateTask::getJavaTestSourceDir));
        });
    }

}
