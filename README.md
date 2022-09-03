# Kamiloply

-----------

WIP

Kamiloply is a system to direct linking magic java-bytecodes like `invokeDynamic` in java source-code.

## Using Kamiloply

## Using in Gradle

Add following content in your `build.gradle`

```groovy

plugins {
    id 'com.kasukusakura.kamiloply' version '0.0.7'
}

dependencies {
    compileOnly 'com.kasukusakura.kamiloply:kamiloply-api:0.0.7'
    // If @DynamicCodeGenerate used
    compileOnly 'org.ow2.asm:asm-tree:9.3'
}
```

## Using in Maven

TODO

----------------------------------

## Syntax

### Bind as invokeDynamic

```java
public class MyClass {
    @CallSiteBind(
            bootstrap = LambdaMetafactory.class,
            bootstrapName = "metafactory",
            methodName = "accept",
            bootstrapArgs = {
                    @CallSiteBind.BootstrapArg(methodType = "(Ljava/lang/Object;)V"),
                    @CallSiteBind.BootstrapArg(mhv = @CallSiteBind.MethodHandleBind(
                            opcode = SimplifyOpcodes.H_INVOKEINTERFACE,
                            owner = Consumer.class, name = "accept", desc = "(Ljava/lang/Object;)V",
                            itf = true
                    )),
                    @CallSiteBind.BootstrapArg(methodType = "(Ljava/lang/Object;)V"),
            }
    )
    public static native <T> Consumer<T> testlambda(Consumer<T> ps);

    // Same as
    public static <T> Consumer<T> testlambda(Consumer<T> ps) {
        return ps::apply;
    }
}
```

### Write asm code in compile-time

```java
public class MyClass {
    @DynamicCodeGenerate
    public static void myMethod() {
        // need add
        // dependencies { compileOnly 'org.ow2.asm:asm-tree' }
        // in your `build.gradle`
        MethodNode myMethod = (MethodNode) KamiloplyTransformStub.rewriteMethodNode();
        myMethod.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "dumpStack", "()V", false);
        myMethod.visitInsn(Opcodes.RETURN);
        myMethod.visitMaxs(0, 0);
    }
}
```
