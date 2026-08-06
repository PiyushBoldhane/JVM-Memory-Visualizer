package com.jvmvisualizer.parser;

/**
 * One parsed line of Playground code (FR-2). The {@link com.jvmvisualizer.engine.SimulationEngine}
 * interprets these against a running {@link com.jvmvisualizer.model.MemoryState}.
 */
public class ParsedStatement {
    public final int sourceLine;
    public final String rawText;

    /** True for "Type name = ...;" / "Type name;" declarations. */
    public boolean isDeclaration;
    /** True for "static Type name = ...;" - lands in the Static Area (FR-9) instead of the stack. */
    public boolean isStatic;
    /** Declared type, only set when isDeclaration. */
    public String declaredType;

    /** The variable being declared or assigned. Null for a bare method-call statement. */
    public String targetVar;

    /** True for "owner.field = rhs;" field assignment. */
    public boolean isFieldAssignment;
    public String fieldOwner;
    public String fieldName;

    /** Right-hand side; Kind.NONE for a bare declaration with no initializer. */
    public Rhs rhs = Rhs.none();

    public ParsedStatement(int sourceLine, String rawText) {
        this.sourceLine = sourceLine;
        this.rawText = rawText;
    }

    /** A bare "method();" or "obj.method();" statement with no result captured. */
    public boolean isBareMethodCall() {
        return !isDeclaration && !isFieldAssignment && targetVar == null && rhs.kind == Rhs.Kind.METHOD_CALL;
    }
}
