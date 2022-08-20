package pkg;

import com.kasukusakura.kamiloply.*;

import java.lang.invoke.LambdaMetafactory;
import java.util.function.Consumer;

public class TestClassEmtx {
    @Modify(rename = "hello", markAsHide = true)
    public static void fuck() {
        Runnable test = System.out::println;
        System.out.println(test);
    }

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
    @Modify(rename = "sprintln", dropModifiers = SimplifyOpcodes.ACC_PRIVATE, addModifiers = SimplifyOpcodes.ACC_PUBLIC)
    public static native <T> Consumer<T> testwwxf(Consumer<T> ps);

    public static void normalMethod() {
    }

    @DynamicCodeGenerate
    @Modify(directDelete = true)
    public static void testDynamicCodeGenerate() {
        KamiloplyTransformStub.acquireClassNode();
        KamiloplyTransformStub.acquireClassNode();
    }
}
