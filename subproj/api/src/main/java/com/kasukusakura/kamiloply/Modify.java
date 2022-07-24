package com.kasukusakura.kamiloply;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Modify marked element modifiers or name
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface Modify {
    /**
     * Delete marked element in compiled codes
     */
    boolean directDelete() default false;

    /**
     * Direct set current element's modifiers
     */
    int setModifiers() default 0;

    /**
     * Add masks to modifiers
     */
    int addModifiers() default 0;

    /**
     * Drop mask from modifiers
     */
    int dropModifiers() default 0;

    /**
     * Change marked element's name
     */
    String rename() default "";

    /**
     * Hide marked element in other applications' compile time
     */
    boolean markAsHide() default false;
}
