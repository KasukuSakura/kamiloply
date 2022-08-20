package com.kasukusakura.kamiloply.core;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class KamiloplyTransform {
    public interface ClassSourceLoader {
        public ClassNode loadClass(String name) throws Exception;
    }

    public static enum TransformStep {
        CLASS, METHOD, FIELD
    }

    public static class TransformContext {
        public boolean shouldDelete;
        public ClassNode currentClass;
        public MethodNode currentMethod;
        public FieldNode currentField;
        public KamiloplyTransform transform;
        public TransformStep step;
    }

    public Map<String, ClassNode> loadedClasses;
    public ClassSourceLoader sourceLoader;
    public Collection<String> processList;
    public Collection<ClassNode> transformedClasses;
    public Path output;
    public Collection<Transformer> transformers;
    public Map<AttributeKey<?>, Object> attributes;


    public KamiloplyTransform withDefaultSettings() {
        loadedClasses = new ConcurrentHashMap<>();
        MultiClassSourceLoader loaders = new MultiClassSourceLoader();
        sourceLoader = loaders;

        processList = new ArrayList<>();
        transformedClasses = new ConcurrentLinkedQueue<>();
        transformers = new ArrayList<>();
        attributes = new HashMap<>();

        loaders.loaders.add(new BuiltinClassSourceLoaders.ClassSourceByClassLoader(
                Thread.currentThread().getContextClassLoader()
        ));

        transformers.add(new BuiltinTransformers.ModifyBind());
        transformers.add(new BuiltinTransformers.CallSiteBindBind());
        transformers.add(new BuiltinTransformers.ThrowsSneakyBind());

        return this;
    }

    public KamiloplyTransform allowDynamicEdit() {
        transformers.add(new BuiltinTransformers.DynamicCodeGenerateBind());
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T attribute(AttributeKey<T> attributeKey) {
        T rsp = (T) attributes.get(attributeKey);
        if (rsp == null) throw new NoSuchElementException("No attribute found for " + attributeKey);
        return rsp;
    }

    @SuppressWarnings("unchecked")
    public <T> T attribute(AttributeKey<T> attributeKey, T defaultValue) {
        return (T) attributes.getOrDefault(attributeKey, defaultValue);
    }

    public <T> KamiloplyTransform withAttribute(AttributeKey<T> attributeKey, T value) {
        attributes.put(attributeKey, value);
        return this;
    }

    public ClassNode load(String name) {
        ClassNode rsp = loadedClasses.get(name);
        if (rsp != null) return rsp;
        try {
            rsp = sourceLoader.loadClass(name);
        } catch (Exception e) {
            throw new RuntimeException("Exception when loading " + name, e);
        } catch (Throwable t) {
            t.addSuppressed(stackTrace("Exception when loading " + name));
            throw t;
        }
        if (rsp == null) throw new RuntimeException("Failed to load " + name + " from source loader: no response");
        loadedClasses.put(name, rsp);
        return rsp;
    }

    public void performTransform(ClassNode node) throws Exception {
        ClassNode mirror = new ClassNode();
        node.accept(mirror);

        TransformContext ctx = new TransformContext();
        ctx.shouldDelete = false;
        ctx.transform = this;
        ctx.currentClass = mirror;
        ctx.step = TransformStep.CLASS;

        try {
            for (Transformer transformer : transformers) {
                transformer.process(ctx);
            }
        } catch (Throwable e) {
            e.addSuppressed(stackTrace("Exception when transforming class " + node.name + ", step=CLASS"));
            throw e;
        }

        if (ctx.shouldDelete) return;

        ctx.step = TransformStep.METHOD;
        try {
            for (Iterator<MethodNode> iterator = mirror.methods.iterator(); iterator.hasNext(); ) {
                ctx.currentMethod = iterator.next();
                for (Transformer transformer : transformers) {
                    transformer.process(ctx);
                }
                if (ctx.shouldDelete) {
                    iterator.remove();
                    ctx.shouldDelete = false;
                }
            }
        } catch (Throwable e) {
            MethodNode mn = ctx.currentMethod;
            String metToString = mn == null ? "<unknown>" : mn.name + mn.desc;
            e.addSuppressed(stackTrace("Exception when transforming class " + node.name + "." + metToString + ", step=METHOD"));
            throw e;
        }

        ctx.currentMethod = null;
        ctx.step = TransformStep.FIELD;
        try {
            for (Iterator<FieldNode> iterator = mirror.fields.iterator(); iterator.hasNext(); ) {
                ctx.currentField = iterator.next();
                for (Transformer transformer : transformers) {
                    transformer.process(ctx);
                }
                if (ctx.shouldDelete) {
                    iterator.remove();
                    ctx.shouldDelete = false;
                }
            }
        } catch (Throwable e) {
            FieldNode fn = ctx.currentField;
            String fieldToString = fn == null ? "<unknown>" : fn.name + "(" + fn.desc + ")";
            e.addSuppressed(stackTrace("Exception when transforming class " + node.name + "." + fieldToString + ", step=FIELD"));
            throw e;
        }

        ctx.step = null;
        ctx.transform = null;
        ctx.currentField = null;
        ctx.currentClass = null;
        ctx.currentMethod = null;

        transformedClasses.add(mirror);
    }

    public void performAll(ExecutorService executor) throws Exception {
        Collection<Callable<Object>> invokes = new ArrayList<>();
        for (String cname : processList) {
            invokes.add(() -> {
                performTransform(load(cname));
                return null;
            });
        }
        for (Future<?> future : executor.invokeAll(invokes)) {
            future.get();
        }
    }

    public void writeProcessed() throws Exception {
        for (ClassNode processed : transformedClasses) {
            try {
                ClassWriter writer = new ClassWriter(0);
                processed.accept(writer);
                byte[] code = writer.toByteArray();

                Path target = output.resolve(processed.name + ".class");
                Path parentDir = target.getParent();
                if (parentDir != null && !Files.isDirectory(parentDir)) {
                    Files.createDirectories(parentDir);
                }

                try (OutputStream outputStream = Files.newOutputStream(target)) {
                    outputStream.write(code);
                }

            } catch (Throwable throwable) {
                throw new IOException("Exception when writing " + processed.name);
            }
        }
    }

    public static Throwable stackTrace(String msg) {
        class StackTrace extends Throwable {
            StackTrace() {
                super(msg, null, true, false);
            }

            @Override
            public synchronized Throwable fillInStackTrace() {
                return this;
            }
        }
        return new StackTrace();
    }
}
