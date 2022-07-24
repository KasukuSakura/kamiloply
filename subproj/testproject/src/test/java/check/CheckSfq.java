package check;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import pkg.TestClassEmtx;

import java.util.function.Consumer;

@SuppressWarnings({"NewClassNamingConvention", "unchecked"})
public class CheckSfq {
    @Test
    public void checkNormalMethodExists() {
        TestClassEmtx.normalMethod();
    }

    @Test
    public void testHideMethod() throws Throwable {
        var met = TestClassEmtx.class.getMethod("hello");
        System.out.println(met);
        Assertions.assertTrue(met.isSynthetic());
        Assertions.assertTrue(met.isBridge());
    }

    @SuppressWarnings("Convert2MethodRef")
    @Test
    public void checkMethodRemoved() {
        Assertions.assertThrows(NoSuchMethodException.class, () -> {
            TestClassEmtx.class.getMethod("testwwxf", Consumer.class);
        });
        Assertions.assertThrows(NoSuchMethodException.class, () -> {
            TestClassEmtx.class.getMethod("fuck");
        });
    }

    @Test
    public void testCallSiteBind() throws Exception {
        var stxw = new Consumer<>() {
            Object value;

            @Override
            public void accept(Object o) {
                System.out.println("SADIWEEXC!!@" + o);
                value = o;
            }
        };
        Consumer<Object> newConsumer = TestClassEmtx.sprintln(stxw);
        Assertions.assertNotEquals(stxw, newConsumer);
        newConsumer.accept(newConsumer);
        Assertions.assertSame(newConsumer, stxw.value);
    }
}
