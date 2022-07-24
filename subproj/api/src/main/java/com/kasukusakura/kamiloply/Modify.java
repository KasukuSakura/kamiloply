package com.kasukusakura.kamiloply;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface Modify {
    boolean directDelete() default false;

    int setModifiers() default 0;

    int addModifiers() default 0;

    int dropModifiers() default 0;

    String rename() default "";

    boolean markAsHide() default false;
}
