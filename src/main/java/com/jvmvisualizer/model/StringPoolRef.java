package com.jvmvisualizer.model;

import java.util.Objects;

/**
 * A reference to an interned string literal in the String Pool (FR-8).
 * Two variables assigned the same literal ("Java") both hold a
 * StringPoolRef with equal {@code value}, so the UI can draw both arrows
 * pointing at the single pooled entry.
 */
public final class StringPoolRef {
    private final String value;

    public StringPoolRef(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringPoolRef)) return false;
        return Objects.equals(value, ((StringPoolRef) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}
