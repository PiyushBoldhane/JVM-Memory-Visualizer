package com.jvmvisualizer.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Playground source text into a list of {@link ParsedStatement}s
 * (FR-2). Deliberately line-oriented and regex-based: the SRS scope note is
 * explicit that this simulates a small, predefined set of statement shapes
 * rather than executing arbitrary Java, so a full parser/compiler is out of
 * scope. One statement per line, each line ending in {@code ;}.
 *
 * Supported shapes (see SRS FR-2):
 *   Type name = new Class(args);     declaration + object creation
 *   Type name = other;               declaration + reference/primitive copy
 *   Type name = "literal"|123|true;  declaration + literal
 *   Type name = null;                declaration + null init
 *   Type name;                       bare declaration
 *   static Type name = ...;          static field (FR-9)
 *   name = new Class(args);          reassignment to a new object
 *   name = other;  name = "lit";     assignment
 *   name = null;                     null assignment
 *   owner.field = value;             field assignment
 *   name();  owner.method();         bare method call
 *   name = owner.method();           method call with captured result
 *   Type name = owner.method();      declared + captured result
 */
public class CodeParser {

    private static final String ID = "[A-Za-z_]\\w*";
    private static final String TYPE = "[A-Za-z_]\\w*(?:\\[\\])?";
    private static final String LITERAL = "(\"[^\"]*\"|\\d+\\.\\d+|\\d+|true|false)";

    private static final Pattern DECL_NEW_OBJECT = Pattern.compile(
            "^(?:(static)\\s+)?(" + TYPE + ")\\s+(" + ID + ")\\s*=\\s*new\\s+(" + ID + ")\\s*\\((.*)\\)\\s*;$");
    private static final Pattern DECL_METHOD_CALL = Pattern.compile(
            "^(?:(static)\\s+)?(" + TYPE + ")\\s+(" + ID + ")\\s*=\\s*(?:(" + ID + ")\\.)?(" + ID + ")\\s*\\((.*)\\)\\s*;$");
    private static final Pattern DECL_NULL = Pattern.compile(
            "^(?:(static)\\s+)?(" + TYPE + ")\\s+(" + ID + ")\\s*=\\s*null\\s*;$");
    private static final Pattern DECL_LITERAL = Pattern.compile(
            "^(?:(static)\\s+)?(" + TYPE + ")\\s+(" + ID + ")\\s*=\\s*" + LITERAL + "\\s*;$");
    private static final Pattern DECL_VAR_REF = Pattern.compile(
            "^(?:(static)\\s+)?(" + TYPE + ")\\s+(" + ID + ")\\s*=\\s*(" + ID + ")\\s*;$");
    private static final Pattern DECL_BARE = Pattern.compile(
            "^(?:(static)\\s+)?(" + TYPE + ")\\s+(" + ID + ")\\s*;$");

    private static final Pattern ASSIGN_NEW_OBJECT = Pattern.compile(
            "^(" + ID + ")\\s*=\\s*new\\s+(" + ID + ")\\s*\\((.*)\\)\\s*;$");
    private static final Pattern ASSIGN_METHOD_CALL = Pattern.compile(
            "^(" + ID + ")\\s*=\\s*(?:(" + ID + ")\\.)?(" + ID + ")\\s*\\((.*)\\)\\s*;$");
    private static final Pattern ASSIGN_NULL = Pattern.compile(
            "^(" + ID + ")\\s*=\\s*null\\s*;$");
    private static final Pattern ASSIGN_LITERAL = Pattern.compile(
            "^(" + ID + ")\\s*=\\s*" + LITERAL + "\\s*;$");
    private static final Pattern ASSIGN_VAR_REF = Pattern.compile(
            "^(" + ID + ")\\s*=\\s*(" + ID + ")\\s*;$");

    private static final Pattern FIELD_ASSIGN_NULL = Pattern.compile(
            "^(" + ID + ")\\.(" + ID + ")\\s*=\\s*null\\s*;$");
    private static final Pattern FIELD_ASSIGN_LITERAL = Pattern.compile(
            "^(" + ID + ")\\.(" + ID + ")\\s*=\\s*" + LITERAL + "\\s*;$");
    private static final Pattern FIELD_ASSIGN_VAR_REF = Pattern.compile(
            "^(" + ID + ")\\.(" + ID + ")\\s*=\\s*(" + ID + ")\\s*;$");

    private static final Pattern BARE_METHOD_CALL = Pattern.compile(
            "^(?:(" + ID + ")\\.)?(" + ID + ")\\s*\\((.*)\\)\\s*;$");

    /** Parses every statement in the source. Throws on the first unrecognized line. */
    public List<ParsedStatement> parse(String source) throws ParseException {
        List<ParsedStatement> statements = new ArrayList<>();
        String[] rawLines = source.split("\\r?\\n", -1);
        for (int i = 0; i < rawLines.length; i++) {
            int lineNumber = i + 1;
            String line = rawLines[i].trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            statements.add(parseLine(lineNumber, line));
        }
        return statements;
    }

    private ParsedStatement parseLine(int lineNumber, String line) throws ParseException {
        Matcher m;

        if ((m = DECL_NEW_OBJECT.matcher(line)).matches()) {
            ParsedStatement s = decl(lineNumber, line, m.group(1), m.group(2), m.group(3));
            s.rhs = Rhs.ofNewObject(m.group(4), m.group(5));
            return s;
        }
        if ((m = DECL_METHOD_CALL.matcher(line)).matches()) {
            ParsedStatement s = decl(lineNumber, line, m.group(1), m.group(2), m.group(3));
            s.rhs = Rhs.ofMethodCall(m.group(4), m.group(5), m.group(6));
            return s;
        }
        if ((m = DECL_NULL.matcher(line)).matches()) {
            ParsedStatement s = decl(lineNumber, line, m.group(1), m.group(2), m.group(3));
            s.rhs = Rhs.ofNull();
            return s;
        }
        if ((m = DECL_LITERAL.matcher(line)).matches()) {
            ParsedStatement s = decl(lineNumber, line, m.group(1), m.group(2), m.group(3));
            s.rhs = literal(m.group(4));
            return s;
        }
        if ((m = DECL_VAR_REF.matcher(line)).matches()) {
            ParsedStatement s = decl(lineNumber, line, m.group(1), m.group(2), m.group(3));
            s.rhs = Rhs.ofVarRef(m.group(4));
            return s;
        }
        if ((m = DECL_BARE.matcher(line)).matches()) {
            ParsedStatement s = decl(lineNumber, line, m.group(1), m.group(2), m.group(3));
            s.rhs = Rhs.none();
            return s;
        }

        if ((m = ASSIGN_NEW_OBJECT.matcher(line)).matches()) {
            ParsedStatement s = assign(lineNumber, line, m.group(1));
            s.rhs = Rhs.ofNewObject(m.group(2), m.group(3));
            return s;
        }
        if ((m = ASSIGN_METHOD_CALL.matcher(line)).matches()) {
            ParsedStatement s = assign(lineNumber, line, m.group(1));
            s.rhs = Rhs.ofMethodCall(m.group(2), m.group(3), m.group(4));
            return s;
        }
        if ((m = ASSIGN_NULL.matcher(line)).matches()) {
            ParsedStatement s = assign(lineNumber, line, m.group(1));
            s.rhs = Rhs.ofNull();
            return s;
        }
        if ((m = ASSIGN_LITERAL.matcher(line)).matches()) {
            ParsedStatement s = assign(lineNumber, line, m.group(1));
            s.rhs = literal(m.group(2));
            return s;
        }
        if ((m = ASSIGN_VAR_REF.matcher(line)).matches()) {
            ParsedStatement s = assign(lineNumber, line, m.group(1));
            s.rhs = Rhs.ofVarRef(m.group(2));
            return s;
        }

        if ((m = FIELD_ASSIGN_NULL.matcher(line)).matches()) {
            ParsedStatement s = fieldAssign(lineNumber, line, m.group(1), m.group(2));
            s.rhs = Rhs.ofNull();
            return s;
        }
        if ((m = FIELD_ASSIGN_LITERAL.matcher(line)).matches()) {
            ParsedStatement s = fieldAssign(lineNumber, line, m.group(1), m.group(2));
            s.rhs = literal(m.group(3));
            return s;
        }
        if ((m = FIELD_ASSIGN_VAR_REF.matcher(line)).matches()) {
            ParsedStatement s = fieldAssign(lineNumber, line, m.group(1), m.group(2));
            s.rhs = Rhs.ofVarRef(m.group(3));
            return s;
        }

        if ((m = BARE_METHOD_CALL.matcher(line)).matches()) {
            ParsedStatement s = new ParsedStatement(lineNumber, line);
            s.rhs = Rhs.ofMethodCall(m.group(1), m.group(2), m.group(3));
            return s;
        }

        throw new ParseException(lineNumber, line,
                "Unsupported statement. Supported: declaration, object creation, assignment, field assignment, method call, null assignment");
    }

    private ParsedStatement decl(int lineNumber, String line, String staticKw, String type, String name) {
        ParsedStatement s = new ParsedStatement(lineNumber, line);
        s.isDeclaration = true;
        s.isStatic = staticKw != null;
        s.declaredType = type;
        s.targetVar = name;
        return s;
    }

    private ParsedStatement assign(int lineNumber, String line, String name) {
        ParsedStatement s = new ParsedStatement(lineNumber, line);
        s.targetVar = name;
        return s;
    }

    private ParsedStatement fieldAssign(int lineNumber, String line, String owner, String field) {
        ParsedStatement s = new ParsedStatement(lineNumber, line);
        s.isFieldAssignment = true;
        s.fieldOwner = owner;
        s.fieldName = field;
        return s;
    }

    private Rhs literal(String token) {
        if (token.startsWith("\"") && token.endsWith("\"")) {
            return Rhs.ofLiteral(token.substring(1, token.length() - 1), "String");
        }
        if (token.equals("true") || token.equals("false")) {
            return Rhs.ofLiteral(Boolean.parseBoolean(token), "boolean");
        }
        if (token.contains(".")) {
            return Rhs.ofLiteral(Double.parseDouble(token), "double");
        }
        return Rhs.ofLiteral(Integer.parseInt(token), "int");
    }
}
