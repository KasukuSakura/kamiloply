package com.kasukusakura.kamiloply.core;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Collection;

public class MultiClassSourceLoader implements KamiloplyTransform.ClassSourceLoader {
    public Collection<KamiloplyTransform.ClassSourceLoader> loaders;

    public MultiClassSourceLoader(Collection<KamiloplyTransform.ClassSourceLoader> loaders) {
        this.loaders = loaders;
    }

    public MultiClassSourceLoader() {
        this.loaders = new ArrayList<>();
    }

    @Override
    public ClassNode loadClass(String name) throws Exception {
        Exception topException = null;
        for (KamiloplyTransform.ClassSourceLoader loader : loaders) {
            try {
                ClassNode rsp = loader.loadClass(name);
                if (rsp != null) return rsp;
            } catch (Exception e) {
                if (topException == null) {
                    topException = e;
                } else {
                    topException.addSuppressed(e);
                }
            }
        }
        if (topException != null) throw topException;
        return null;
    }
}
