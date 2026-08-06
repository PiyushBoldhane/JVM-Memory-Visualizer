package com.jvmvisualizer.model;

/**
 * A local variable or parameter living in a {@link StackFrame} (FR-3).
 * {@code value} is either:
 *  - {@code null}                -> the variable holds null
 *  - a {@link HeapRef}           -> the variable references a heap object
 *  - a {@link StringPoolRef}     -> the variable references an interned string literal
 *  - any other object (String/Integer/etc.) -> a primitive/literal value stored directly
 */
public class Variable {
    private final String name;
    private final String type;
    private Object value;

    public Variable(String name, String type, Object value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Variable copy() {
        return new Variable(name, type, value);
    }
}
