package com.kasukusakura.kamiloply.core;

@SuppressWarnings("unused")
public class AttributeKey<T> {
    private final String name;

    public AttributeKey(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "AttributeKey[" + name + "]";
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public final boolean equals(Object obj) {
        return obj == this;
    }
}
