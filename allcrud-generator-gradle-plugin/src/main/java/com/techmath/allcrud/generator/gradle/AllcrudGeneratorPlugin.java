package com.techmath.allcrud.generator.gradle;

import com.techmath.allcrud.generator.PojoNamingStyle;
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
        extension.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated/sources/allcrud"));
        extension.getPojoNamingStyle().convention(PojoNamingStyle.VO);

        TaskProvider<AllcrudGenerateTask> generateTask = project.getTasks().register(TASK_NAME, AllcrudGenerateTask.class, task -> {
            task.setGroup("allcrud");
            task.setDescription("Generates the Allcrud VO/Repository/Converter/Service/Controller stack from an OpenAPI spec.");
            task.getSpecFile().convention(extension.getSpecFile());
            task.getPojoNamingStyle().convention(extension.getPojoNamingStyle());
            task.getOutputDir().convention(extension.getOutputDir());
            // Derived from the extension's outputDir, not the task's own getOutputDir() -
            // see AllcrudGenerateTask's constructor comment for why.
            task.getJavaSourceDir().convention(extension.getOutputDir().map(dir -> dir.dir("src/main/java")));
        });

        // Wiring the source set to the task's own output property (not a detached
        // provider) is what lets Gradle infer the compileJava -> generateAllcrud task
        // dependency automatically - no explicit dependsOn needed.
        project.getPluginManager().withPlugin("java", appliedPlugin -> {
            JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            javaExtension.getSourceSets().getByName("main").getJava()
                    .srcDir(generateTask.flatMap(AllcrudGenerateTask::getJavaSourceDir));
        });
    }

}
