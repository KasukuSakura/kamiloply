package com.kasukusakura.kamiloply.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class BuiltinClassSourceLoaders {
    public static ClassNode readClass(InputStream stream) throws IOException {
        if (stream == null) return null;
        ClassReader reader = new ClassReader(stream);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    public static class ClassSourceByClassLoader implements KamiloplyTransform.ClassSourceLoader {
        private final ClassLoader classLoader;

        public ClassSourceByClassLoader(ClassLoader classLoader) {
            this.classLoader = Objects.requireNonNull(classLoader);
        }

        @Override
        public ClassNode loadClass(String name) throws Exception {
            try (InputStream is = classLoader.getResourceAsStream(name + ".class")) {
                return readClass(is);
            }
        }
    }

    public static class MultiDirectoryClassSourceLoader implements KamiloplyTransform.ClassSourceLoader {
        private final Iterable<File> dirs;

        public MultiDirectoryClassSourceLoader(Iterable<File> dirs) {
            this.dirs = dirs;
        }

        @SuppressWarnings("IOStreamConstructor")
        @Override
        public ClassNode loadClass(String name) throws Exception {
            name += ".class";
            for (File adir : dirs) {
                File itfx = new File(adir, name);
                if (itfx.isFile()) {
                    try (InputStream is = new FileInputStream(itfx)) {
                        return readClass(is);
                    }
                }
            }
            return null;
        }
    }
}
