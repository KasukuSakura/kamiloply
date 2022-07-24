package com.kasukusakura.kamiloply;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Run marked method on compile-time.
 *
 * @see KamiloplyTransformStub
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface DynamicCodeGenerate {
    boolean onlyLoadMarkedMethod() default true;

    Class<?>[] libraries() default {};
}
