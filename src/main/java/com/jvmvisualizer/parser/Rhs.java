package com.jvmvisualizer.parser;

/**
 * The right-hand side of a declaration/assignment, or the call description
 * for a method-call statement. Only one "kind" is ever populated.
 */
public class Rhs {
    public enum Kind { NONE, NULL, LITERAL, VAR_REF, NEW_OBJECT, METHOD_CALL }

    public final Kind kind;

    // LITERAL
    public Object literalValue;
    public String literalType; // "String" | "int" | "double" | "boolean"

    // VAR_REF
    public String varRefName;

    // NEW_OBJECT
    public String className;
    public String constructorArgs;

    // METHOD_CALL
    public String receiver; // null => call is on the current context (e.g. main() calling display())
    public String methodName;
    public String callArgs;

    private Rhs(Kind kind) {
        this.kind = kind;
    }

    public static Rhs none() {
        return new Rhs(Kind.NONE);
    }

    public static Rhs ofNull() {
        return new Rhs(Kind.NULL);
    }

    public static Rhs ofLiteral(Object value, String type) {
        Rhs r = new Rhs(Kind.LITERAL);
        r.literalValue = value;
        r.literalType = type;
        return r;
    }

    public static Rhs ofVarRef(String name) {
        Rhs r = new Rhs(Kind.VAR_REF);
        r.varRefName = name;
        return r;
    }

    public static Rhs ofNewObject(String className, String args) {
        Rhs r = new Rhs(Kind.NEW_OBJECT);
        r.className = className;
        r.constructorArgs = args;
        return r;
    }

    public static Rhs ofMethodCall(String receiver, String methodName, String args) {
        Rhs r = new Rhs(Kind.METHOD_CALL);
        r.receiver = receiver;
        r.methodName = methodName;
        r.callArgs = args;
        return r;
    }
}
