package com.kasukusakura.kamiloply.gradle;

import com.kasukusakura.kamiloply.core.BuiltinAttributes;
import com.kasukusakura.kamiloply.core.BuiltinClassSourceLoaders;
import com.kasukusakura.kamiloply.core.BuiltinTransformers;
import com.kasukusakura.kamiloply.core.KamiloplyTransform;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KamiloplyTransformTask extends DefaultTask {
    SourceSet attached;
    ConfigurableFileCollection srcs;

    @InputFiles
    protected Collection<File> getInputSources() throws Exception {
        return srcs.getFiles();
    }

    private interface ErrFunc<T, R> {
        R invoke(T argx) throws Throwable;
    }

    private static <T, R> Function<T, R> er(ErrFunc<T, R> f) {
        return v -> {
            try {
                return f.invoke(v);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }

    private File resolveOutput() {
        File rsp = getTemporaryDir();
        rsp.mkdirs();
        return rsp;
    }

    @OutputDirectory
    protected File getOutputX() {
        return getTemporaryDir();
    }


    @TaskAction
    protected void act() throws Exception {
        KamiloplyTransform transform = new KamiloplyTransform()
                .withDefaultSettings()
                .allowDynamicEdit();

        List<File> direx = new ArrayList<>();
        for (File dirx : srcs) {
            direx.add(dirx);
        }

        transform.sourceLoader = new BuiltinClassSourceLoaders.MultiDirectoryClassSourceLoader(direx);
        {
            Lazy<ClassLoader> lazy = LazyKt.lazy(() -> {
                ArrayList<URL> urls = new ArrayList<>();
                for (File f : attached.getCompileClasspath().getFiles()) {
                    try {
                        urls.add(f.toURI().toURL());
                    } catch (MalformedURLException e) {
                        getLogger().warn("Error when loading classpath: " + f, e);
                    }
                }

                if (urls.isEmpty()) return BuiltinTransformers.getAsmClassLoader();

                return new URLClassLoader(urls.toArray(new URL[0]), BuiltinTransformers.getAsmClassLoader());
            });
            transform.withAttribute(BuiltinAttributes.DYNAMIC_CODE_CLASS_LOADER_PARENT, $ -> lazy.getValue());
        }

        @SuppressWarnings("resource")
        Stream<String> ptstream = direx.stream().map(File::toPath)
                .flatMap(er(adir -> Files.walk(adir).filter(Files::isRegularFile).map(adir::relativize)))
                .map(String::valueOf)
                .filter(it -> it.endsWith(".class"))
                .map(it -> it.substring(0, it.length() - 6).replace('.', '/'));

        try (Stream<String> ptwx = ptstream) {
            transform.processList = ptwx.collect(Collectors.toList());
        }
        transform.output = resolveOutput().toPath();

        ExecutorService service;
        boolean doShutdownService;
        if (ForkJoinPool.getCommonPoolParallelism() >= 1) {
            service = ForkJoinPool.commonPool();
            doShutdownService = false;
        } else {
            service = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
            doShutdownService = true;
        }
        try {
            transform.performAll(service);
        } finally {
            if (doShutdownService) service.shutdown();
        }
        transform.writeProcessed();
    }
}
