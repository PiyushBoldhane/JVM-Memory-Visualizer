package com.jvmvisualizer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One stack frame pushed per method call (FR-3, FR-6). {@code main()} is the
 * permanent bottom frame of the stack.
 */
public class StackFrame {
    private final String methodName;
    private final List<Variable> locals = new ArrayList<>();

    public StackFrame(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<Variable> getLocals() {
        return locals;
    }

    public Variable findLocal(String name) {
        for (Variable v : locals) {
            if (v.getName().equals(name)) {
                return v;
            }
        }
        return null;
    }

    public void addLocal(Variable variable) {
        locals.add(variable);
    }

    public StackFrame copy() {
        StackFrame clone = new StackFrame(methodName);
        for (Variable v : locals) {
            clone.locals.add(v.copy());
        }
        return clone;
    }
}
