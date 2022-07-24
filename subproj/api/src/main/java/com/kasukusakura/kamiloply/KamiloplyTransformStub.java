package com.kasukusakura.kamiloply;

/**
 * Used with {@link DynamicCodeGenerate}
 */
public class KamiloplyTransformStub {
    /**
     * @return Current class node. {@code org.objectweb.asm.tree.ClassNode}
     */
    public static native Object acquireClassNode();

    /**
     * @return Current method node. {@code org.objectweb.asm.tree.MethodNode}
     */
    public static native Object acquireMethodNode();

    /**
     * Current class node. {@code org.objectweb.asm.tree.MethodNode}
     * <p>
     * Will clear current method node's codes
     */
    public static native Object rewriteMethodNode();
}
