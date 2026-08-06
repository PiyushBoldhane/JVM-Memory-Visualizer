package com.jvmvisualizer.parser;

/**
 * Thrown when a line of Playground code (FR-2) doesn't match any supported
 * statement shape. Carries the 1-based source line number so the UI can
 * point the user at the offending line.
 */
public class ParseException extends Exception {
    private final int lineNumber;
    private final String sourceLine;

    public ParseException(int lineNumber, String sourceLine, String message) {
        super(message);
        this.lineNumber = lineNumber;
        this.sourceLine = sourceLine;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getSourceLine() {
        return sourceLine;
    }

    @Override
    public String getMessage() {
        return "Line " + lineNumber + ": " + super.getMessage() + "  →  \"" + sourceLine.trim() + "\"";
    }
}
