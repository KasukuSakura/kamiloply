package com.kasukusakura.kamiloply.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

@SuppressWarnings({"unchecked", "rawtypes"})
public class BuiltinTransformers {
    public static List<AnnotationNode> getAnnotations(KamiloplyTransform.TransformContext ctx) {
        switch (ctx.step) {
            case CLASS:
                return ctx.currentClass.invisibleAnnotations;
            case FIELD:
                return ctx.currentField.invisibleAnnotations;
            case METHOD:
                return ctx.currentMethod.invisibleAnnotations;
            default:
                throw new AssertionError();
        }
    }

    public static Handle parseAsMethodHandle(Object mhv) {
        if (mhv instanceof AnnotationNode) {
            mhv = convertToMap((AnnotationNode) mhv);
        }
        Map<String, Object> argm = (Map) mhv;

        String owner = (String) argm.get("ownerClass");
        if (owner == null) {
            Type t = (Type) argm.get("owner");
            if (t == null) {
                throw new IllegalArgumentException("No owner in @MethodHandleBind" + argm);
            }
            if (t.getSort() != Type.OBJECT) {
                throw new IllegalArgumentException("Invalid class(" + t + ") in @MethodHandleBind" + argm);
            }
            owner = t.getInternalName();
        } else {
            owner = owner.replace('.', '/');
        }
        boolean itf = false;
        {
            Boolean iffx = (Boolean) argm.get("itf");
            if (iffx != null) itf = iffx;
        }
        int opcode = (Integer) argm.get("opcode");
        String name = (String) argm.get("name");
        String desc = (String) argm.get("desc");
        return new Handle(
                opcode, owner, name, desc, itf
        );
    }

    public static AnnotationNode findAnnotation(List<AnnotationNode> nodes, String name) {
        if (nodes == null) return null;
        for (AnnotationNode n : nodes) {
            if (n.desc.equals(name)) return n;
        }
        return null;
    }

    public static Map<String, Object> convertToMap(AnnotationNode v) {
        if (v == null) return null;
        List<Object> values = v.values;
        if (values == null || values.isEmpty()) return Collections.emptyMap();
        Map<String, Object> rsp = new HashMap<>();
        for (int i = 0, ed = values.size(); i < ed; i += 2) {
            rsp.put(values.get(i).toString(), values.get(i + 1));
        }
        return rsp;
    }


    public static ClassLoader getAsmClassLoader() {
        return DynamicCodeGenerateBind.AsmClassLoaderV.INSTANCE;
    }

    public static class ModifyBind extends Transformer {
        @Override
        public void process(KamiloplyTransform.TransformContext context) throws Exception {
            AnnotationNode modifyNode = findAnnotation(
                    getAnnotations(context),
                    "Lcom/kasukusakura/kamiloply/Modify;"
            );
            if (modifyNode == null) return;
            Map<String, Object> vtx = convertToMap(modifyNode);
            if (vtx.isEmpty()) {
                throw new IllegalArgumentException("No configuration on @Modify");
            }
            getAnnotations(context).remove(modifyNode);
            {
                Object directDelete = vtx.get("directDelete");
                if (directDelete != null) {
                    context.shouldDelete = true;
                    return;
                }
            }
            modifiers:
            {
                Integer setModifiers = (Integer) vtx.get("setModifiers");
                Integer addModifiers = (Integer) vtx.get("addModifiers");
                Integer dropModifiers = (Integer) vtx.get("dropModifiers");
                Object markAsHide = vtx.get("markAsHide");
                if (setModifiers == null && addModifiers == null && dropModifiers == null && markAsHide == null)
                    break modifiers;

                if (setModifiers != null && (addModifiers != null || dropModifiers != null)) {
                    throw new IllegalArgumentException(
                            "Cannot use setModifiers with addModifiers/dropModifiers in same time"
                    );
                }
                int modifiers = 0;
                switch (context.step) {
                    case CLASS:
                        modifiers = context.currentClass.access;
                        break;
                    case FIELD:
                        modifiers = context.currentField.access;
                        break;
                    case METHOD:
                        modifiers = context.currentMethod.access;
                        break;
                }
                if (setModifiers != null) {
                    modifiers = setModifiers;
                }
                if (addModifiers != null) {
                    modifiers |= addModifiers;
                }
                if (dropModifiers != null) {
                    modifiers &= ~dropModifiers;
                }
                if (markAsHide != null && (Boolean) markAsHide) {
                    modifiers |= Opcodes.ACC_SYNTHETIC;
                    if (context.step == KamiloplyTransform.TransformStep.METHOD) {
                        modifiers |= Opcodes.ACC_BRIDGE;
                    } else if (context.step == KamiloplyTransform.TransformStep.CLASS) {
                        for (MethodNode subm : context.currentClass.methods) {
                            subm.access |= Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE;
                        }
                        for (FieldNode subm : context.currentClass.fields) {
                            subm.access |= Opcodes.ACC_SYNTHETIC;
                        }
                    }
                }

                switch (context.step) {
                    case CLASS:
                        context.currentClass.access = modifiers;
                        break;
                    case FIELD:
                        context.currentField.access = modifiers;
                        break;
                    case METHOD:
                        context.currentMethod.access = modifiers;
                        break;
                }
            }
            {
                Object ren = vtx.get("rename");
                if (ren != null) {
                    switch (context.step) {
                        case CLASS: {
                            String coldname = context.currentClass.name;
                            int idx = coldname.lastIndexOf('/');
                            String srx = ren.toString();
                            if (idx == -1 || srx.indexOf('/') != -1) {
                                context.currentClass.name = srx;
                            } else {
                                context.currentClass.name = coldname.substring(0, idx + 1) + srx;
                            }
                            break;
                        }
                        case FIELD:
                            context.currentField.name = ren.toString();
                            break;
                        case METHOD:
                            context.currentMethod.name = ren.toString();
                            break;
                    }
                }
            }
        }
    }

    public static class CallSiteBindBind extends Transformer {
        @Override
        public void process(KamiloplyTransform.TransformContext context) throws Exception {
            if (context.step != KamiloplyTransform.TransformStep.METHOD) return;
            MethodNode methodNode = context.currentMethod;

            AnnotationNode annotation = findAnnotation(
                    methodNode.invisibleAnnotations,
                    "Lcom/kasukusakura/kamiloply/CallSiteBind;"
            );
            if (annotation == null) return;
            if ((methodNode.access & Opcodes.ACC_NATIVE) == 0) {
                throw new IllegalArgumentException("@CallSiteBind can only be applied on native methods");
            }
            methodNode.invisibleAnnotations.remove(annotation);

            Map<String, Object> argx = convertToMap(annotation);
            String bootstrap = (String) argx.get("bootstrapClass");
            if (bootstrap != null) {
                bootstrap = bootstrap.replace('.', '/');
            } else {
                Type bootstrapT = (Type) argx.get("bootstrap");
                if (bootstrapT == null) {
                    throw new IllegalArgumentException("No bootstrap() or bootstrapClass() found in @CallSiteBind");
                }
                if (bootstrapT.getSort() != Type.OBJECT) {
                    throw new IllegalArgumentException("bootstrap(" + bootstrapT + ") not a valid class");
                }
                bootstrap = bootstrapT.getInternalName();
            }
            String bootstrapMethodName = (String) argx.get("bootstrapName");
            String methodName = (String) argx.get("methodName");
            String methodType = (String) argx.get("methodType");
            if (methodType == null) {
                methodType = methodNode.desc;
            }

            Type metT = Type.getMethodType(context.currentMethod.desc);
            List<Type> bootstrapArgs = new ArrayList<>();

            bootstrapArgs.add(Type.getObjectType("java/lang/invoke/MethodHandles$Lookup"));
            bootstrapArgs.add(Type.getObjectType("java/lang/String"));
            bootstrapArgs.add(Type.getObjectType("java/lang/invoke/MethodType"));

            List<?> annoBootstrapArgs = (List<?>) argx.get("bootstrapArgs");
            Object[] bsmargs;
            if (annoBootstrapArgs != null) {
                bsmargs = new Object[annoBootstrapArgs.size()];
                int idx = -1;
                for (Object abx : annoBootstrapArgs) {
                    idx++;

                    Map<String, Object> bsma = convertToMap((AnnotationNode) abx);
                    if (bsma.isEmpty()) {
                        throw new IllegalArgumentException("Invalid argument of index " + idx + ": " + bsma + ": Empty arg");
                    }
                    if (bsma.size() != 1) {
                        throw new IllegalArgumentException("Invalid argument of index " + idx + ": " + bsma + ": Only one value can be used.");
                    }
                    if (bsma.containsKey("strv")) {
                        bootstrapArgs.add(Type.getObjectType("java/lang/String"));
                        bsmargs[idx] = bsma.get("strv");
                        continue;
                    }
                    if (bsma.containsKey("intv")) {
                        bootstrapArgs.add(Type.INT_TYPE);
                        bsmargs[idx] = bsma.get("intv");
                        continue;
                    }
                    if (bsma.containsKey("typev")) {
                        bootstrapArgs.add(Type.getObjectType("java/lang/Class"));
                        bsmargs[idx] = Type.getObjectType(bsma.get("typev").toString().replace('.', '/'));
                        continue;
                    }
                    if (bsma.containsKey("typev2")) {
                        bootstrapArgs.add(Type.getObjectType("java/lang/Class"));
                        bsmargs[idx] = bsma.get("typev");
                        continue;
                    }
                    if (bsma.containsKey("methodType")) {
                        bootstrapArgs.add(Type.getObjectType("java/lang/invoke/MethodType"));
                        bsmargs[idx] = Type.getMethodType(bsma.get("methodType").toString());
                        continue;
                    }
                    if (bsma.containsKey("mhv")) {
                        bootstrapArgs.add(Type.getObjectType("java/lang/invoke/MethodHandle"));
                        bsmargs[idx] = parseAsMethodHandle(bsma.get("mhv"));
                        continue;
                    }

                    throw new AssertionError("Unknown boot arg: " + bsma);
                }
            } else {
                bsmargs = new Object[0];
            }

            methodNode.access &= ~Opcodes.ACC_NATIVE;

            int usedSlots = 0;
            if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
                usedSlots++;
            }
            for (Type argt : metT.getArgumentTypes()) {
                methodNode.visitVarInsn(argt.getOpcode(Opcodes.ILOAD), usedSlots);
                usedSlots += argt.getSize();
            }
            methodNode.visitInvokeDynamicInsn(
                    methodName, methodType, new Handle(
                            Opcodes.H_INVOKESTATIC, bootstrap, bootstrapMethodName,
                            Type.getMethodDescriptor(
                                    Type.getObjectType("java/lang/invoke/CallSite"),
                                    bootstrapArgs.toArray(new Type[0])
                            ),
                            false
                    ), bsmargs
            );
            methodNode.visitInsn(metT.getReturnType().getOpcode(Opcodes.IRETURN));
            methodNode.visitMaxs(usedSlots, usedSlots);
        }
    }

    @SuppressWarnings("deprecation")
    public static class DynamicCodeGenerateBind extends Transformer {
        private static class AsmClassLoaderV extends ClassLoader {
            private static final ClassLoader CWX = ScopedClassLoader.class.getClassLoader();
            private static final ClassLoader PLATFORM = ClassLoader.getSystemClassLoader().getParent();
            private static final ClassLoader INSTANCE = new AsmClassLoaderV();

            private AsmClassLoaderV() {
                super(PLATFORM);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.objectweb.asm.")) return CWX.loadClass(name);
                return super.loadClass(name, resolve);
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.startsWith("org/objectweb/asm/")) return CWX.getResources(name);
                return PLATFORM.getResources(name);
            }

            @Override
            public URL getResource(String name) {
                if (name.startsWith("org/objectweb/asm/")) return CWX.getResource(name);
                return PLATFORM.getResource(name);
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                if (name.startsWith("org/objectweb/asm/")) return CWX.getResourceAsStream(name);
                return PLATFORM.getResourceAsStream(name);
            }
        }

        public static class ScopedClassLoader extends ClassLoader {

            public Map<String, byte[]> klasses = new HashMap<>();

            public ScopedClassLoader(ClassLoader parent) {
                super(parent);
            }

            public void put(Collection<ClassNode> cn) {
                for (ClassNode cnode : cn) {
                    ClassWriter cw = new ClassWriter(0);
                    cnode.accept(cw);
                    klasses.put(cnode.name.replace('/', '.'), cw.toByteArray());
                }
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.objectweb.asm.")) {
                    return AsmClassLoaderV.CWX.loadClass(name);
                }
                if (klasses.containsKey(name)) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded != null) return loaded;
                        return findClass(name);
                    }
                }
                return super.loadClass(name, resolve);
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] data = klasses.get(name);
                if (data != null) return defineClass(name, data, 0, data.length);
                throw new ClassNotFoundException(name);
            }
        }

        @Override
        public void process(KamiloplyTransform.TransformContext context) throws Exception {
            if (context.step != KamiloplyTransform.TransformStep.METHOD) return;
            MethodNode methodNode = context.currentMethod;

            AnnotationNode annotation = findAnnotation(
                    methodNode.invisibleAnnotations,
                    "Lcom/kasukusakura/kamiloply/DynamicCodeGenerate;"
            );
            if (annotation == null) return;
            if (methodNode.instructions.size() == 0) {
                throw new IllegalArgumentException("Marked @DynamicCodeGenerate on native/abstract methods.");
            }

            List<ClassNode> scope = new ArrayList<>();
            Map<String, Object> argx = convertToMap(annotation);
            List<Type> libraries = (List<Type>) argx.get("libraries");

            if (libraries != null) {
                for (Type t : libraries) {
                    scope.add(context.transform.load(t.getInternalName()));
                }
            }
            Boolean onlyLoadMarkedMethod = (Boolean) argx.get("onlyLoadMarkedMethod");
            boolean loadAll = false;
            if (onlyLoadMarkedMethod != null) {
                loadAll = !onlyLoadMarkedMethod;
            }
            if (loadAll) {
                scope.add(context.currentClass);
            } else {
                ClassNode cnnx = new ClassNode();
                cnnx.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, context.currentClass.name, null, "java/lang/Object", null);
                cnnx.methods.add(methodNode);

                cnnx.sourceFile = context.currentClass.sourceFile;
                cnnx.sourceDebug = context.currentClass.sourceDebug;

                MethodVisitor initx = cnnx.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                initx.visitVarInsn(Opcodes.ALOAD, 0);
                initx.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                initx.visitInsn(Opcodes.RETURN);
                initx.visitMaxs(2, 2);
                scope.add(cnnx);
            }

            ScopedClassLoader classLoader = new ScopedClassLoader(
                    context.transform.attribute(
                            BuiltinAttributes.DYNAMIC_CODE_CLASS_LOADER_PARENT,
                            Function.identity()
                    ).apply(AsmClassLoaderV.INSTANCE)
            );

            classLoader.put(scope);
            {
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/kasukusakura/kamiloply/KamiloplyTransformStub", null, "java/lang/Object", null);

                cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "acquireClassNode", "Ljava/lang/Object;", null, null);
                cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "acquireMethodNode", "Ljava/lang/Object;", null, null);

                MethodVisitor mv;

                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "acquireClassNode", "()Ljava/lang/Object;", null, null);
                mv.visitFieldInsn(Opcodes.GETSTATIC, "com/kasukusakura/kamiloply/KamiloplyTransformStub", "acquireClassNode", "Ljava/lang/Object;");
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);


                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "acquireMethodNode", "()Ljava/lang/Object;", null, null);
                mv.visitFieldInsn(Opcodes.GETSTATIC, "com/kasukusakura/kamiloply/KamiloplyTransformStub", "acquireMethodNode", "Ljava/lang/Object;");
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);

                mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "rewriteMethodNode", "()Ljava/lang/Object;", null, null);
                mv.visitFieldInsn(Opcodes.GETSTATIC, "com/kasukusakura/kamiloply/KamiloplyTransformStub", "acquireMethodNode", "Ljava/lang/Object;");

                mv.visitInsn(Opcodes.DUP);
                mv.visitTypeInsn(Opcodes.CHECKCAST, "org/objectweb/asm/tree/MethodNode");


                mv.visitInsn(Opcodes.DUP);
                mv.visitFieldInsn(Opcodes.GETFIELD, "org/objectweb/asm/tree/MethodNode", "instructions", "Lorg/objectweb/asm/tree/InsnList;");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/objectweb/asm/tree/InsnList", "clear", "()V", false);

                if ((methodNode.access & Opcodes.ACC_ABSTRACT) == 0) {

                    mv.visitInsn(Opcodes.DUP);
                    mv.visitFieldInsn(Opcodes.GETFIELD, "org/objectweb/asm/tree/MethodNode", "localVariables", "Ljava/util/List;");
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", true);

                }

                mv.visitInsn(Opcodes.DUP);
                mv.visitFieldInsn(Opcodes.GETFIELD, "org/objectweb/asm/tree/MethodNode", "tryCatchBlocks", "Ljava/util/List;");
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "clear", "()V", true);


                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);

                classLoader.klasses.put("com.kasukusakura.kamiloply.KamiloplyTransformStub", cw.toByteArray());

                Class<?> ccxc = classLoader.loadClass("com.kasukusakura.kamiloply.KamiloplyTransformStub");
                ccxc.getDeclaredField("acquireClassNode").set(null, context.currentClass);
                ccxc.getDeclaredField("acquireMethodNode").set(null, methodNode);
            }
            Class<?> targetClass = classLoader.loadClass(context.currentClass.name.replace('/', '.'));

            Method met = targetClass.getDeclaredMethod(methodNode.name, MethodType.fromMethodDescriptorString(
                    methodNode.desc, classLoader
            ).parameterArray());
            Object instance = Modifier.isStatic(met.getModifiers()) ? null : targetClass.newInstance();
            Object[] args = new Object[met.getParameterCount()];
            Class<?>[] parameterTypes = met.getParameterTypes();
            for (int i = 0, ed = parameterTypes.length; i < ed; i++) {
                Class<?> kc = parameterTypes[i];
                if (kc == byte.class) args[i] = (byte) 0;
                else if (kc == char.class) args[i] = '\u0000';
                else if (kc == short.class) args[i] = (short) 0;
                else if (kc == int.class) args[i] = 0;
                else if (kc == long.class) args[i] = 0L;
                else if (kc == float.class) args[i] = 0.0f;
                else if (kc == double.class) args[i] = 0.0d;
                else if (kc == boolean.class) args[i] = false;
            }
            try {
                met.setAccessible(true);
            } catch (Exception ignored) {
            }
            met.invoke(instance, args);
        }
    }
}
