package com.kasukusakura.kamiloply;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Remove {@code throws} after transform
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ThrowsSneaky {
}
