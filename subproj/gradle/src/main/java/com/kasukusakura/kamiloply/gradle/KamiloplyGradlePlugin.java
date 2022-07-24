package com.kasukusakura.kamiloply.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;

public class KamiloplyGradlePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", $$$$$$$$ -> {
            SourceSetContainer srcs = (SourceSetContainer) project.getExtensions().getByName("sourceSets");
            srcs.all(src -> {
                ConfigurableFileCollection oldSnapshot = project.getObjects().fileCollection();
                ConfigurableFileCollection classesDirs = (ConfigurableFileCollection) src.getOutput().getClassesDirs();

                oldSnapshot.setFrom(new ArrayList<>(classesDirs.getFrom()));
                // oldSnapshot.setBuiltBy(new ArrayList<>(classesDirs.getBuiltBy()));

                TaskProvider<KamiloplyTransformTask> provider = project.getTasks().register(src.getTaskName("transform", "classes"), KamiloplyTransformTask.class);

                classesDirs.setFrom(project.provider(() -> provider.get().getOutputs()));

                provider.configure(it -> {
                    it.srcs = oldSnapshot;
                    it.attached = src;
                    it.dependsOn(project.provider(src::getCompileClasspath));
                    it.dependsOn(oldSnapshot);
                    it.dependsOn(((DefaultSourceSetOutput) src.getOutput()).getClassesContributors());
                });
            });
        });
    }
}
