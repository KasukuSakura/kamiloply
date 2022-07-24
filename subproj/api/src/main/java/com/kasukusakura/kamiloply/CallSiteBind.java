package com.kasukusakura.kamiloply;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CallSiteBind {

    /**
     * The Bootstrap class of bind
     */
    Class<?> bootstrap() default void.class;

    /**
     * The Bootstrap class of bind. Using internal name
     */
    String bootstrapClass() default "";

    /**
     * The method name of bootstrap
     */
    String bootstrapName();

    /**
     * The second argument passed to bootstrap method
     */
    String methodName(); // The first arg passed to XXX

    /**
     * The third argument passed to bootstrap method.
     * Same as define method type if not set
     */
    String methodType() default "";

    /**
     * The extra arguments passed to bootstrap
     */
    BootstrapArg[] bootstrapArgs() default {};

    /**
     * A typedef of a {@link java.lang.invoke.MethodHandle}
     */
    @interface MethodHandleBind {
        Class<?> owner() default void.class;

        String ownerClass() default "";

        String name();

        String desc();

        /**
         * One of {@link SimplifyOpcodes#H_GETFIELD},{@link SimplifyOpcodes#H_GETSTATIC}
         * {@link SimplifyOpcodes#H_PUTFIELD},{@link SimplifyOpcodes#H_PUTSTATIC},
         * {@link SimplifyOpcodes#H_INVOKEINTERFACE},{@link SimplifyOpcodes#H_INVOKESPECIAL},
         * {@link SimplifyOpcodes#H_INVOKESTATIC},{@link SimplifyOpcodes#H_INVOKEVIRTUAL}
         */
        int opcode();

        /**
         * Require true when {@link #opcode()} is {@link SimplifyOpcodes#H_INVOKEINTERFACE}
         */
        boolean itf() default false;
    }

    /**
     * An argument passed to bootstrap.
     * <p>
     * Only one value can be used.
     */
    @interface BootstrapArg {
        String strv() default "";

        int intv() default 0;

        String typev() default "";

        Class<?> typev2() default Void.class;

        String methodType() default "";

        MethodHandleBind mhv() default @MethodHandleBind(desc = "", name = "", opcode = 0);
    }
}
