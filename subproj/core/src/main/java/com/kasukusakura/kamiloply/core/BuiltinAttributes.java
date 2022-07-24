package com.kasukusakura.kamiloply.core;

import java.util.function.Function;

public class BuiltinAttributes {
    public static final AttributeKey<Function<ClassLoader, ClassLoader>> DYNAMIC_CODE_CLASS_LOADER_PARENT = new AttributeKey<>("dynamic-code-generate-parent");
}
