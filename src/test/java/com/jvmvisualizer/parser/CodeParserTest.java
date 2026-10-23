package com.jvmvisualizer.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeParserTest {

    private final CodeParser parser = new CodeParser();

    private ParsedStatement parseOne(String line) throws ParseException {
        List<ParsedStatement> statements = parser.parse(line);
        assertEquals(1, statements.size());
        return statements.get(0);
    }

    // -- declarations ----------------------------------------------------

    @Test
    void declarationWithNewObject() throws ParseException {
        ParsedStatement s = parseOne("Student s = new Student();");
        assertTrue(s.isDeclaration);
        assertFalse(s.isStatic);
        assertEquals("Student", s.declaredType);
        assertEquals("s", s.targetVar);
        assertEquals(Rhs.Kind.NEW_OBJECT, s.rhs.kind);
        assertEquals("Student", s.rhs.className);
    }

    @Test
    void declarationWithVarRef() throws ParseException {
        ParsedStatement s = parseOne("Student t = s;");
        assertTrue(s.isDeclaration);
        assertEquals(Rhs.Kind.VAR_REF, s.rhs.kind);
        assertEquals("s", s.rhs.varRefName);
    }

    @Test
    void declarationWithNull() throws ParseException {
        ParsedStatement s = parseOne("Student s = null;");
        assertEquals(Rhs.Kind.NULL, s.rhs.kind);
    }

    @Test
    void bareDeclaration() throws ParseException {
        ParsedStatement s = parseOne("int x;");
        assertTrue(s.isDeclaration);
        assertEquals(Rhs.Kind.NONE, s.rhs.kind);
    }

    @Test
    void staticFieldDeclaration() throws ParseException {
        ParsedStatement s = parseOne("static int totalStudents = 0;");
        assertTrue(s.isStatic);
        assertTrue(s.isDeclaration);
        assertEquals(0, s.rhs.literalValue);
    }

    @Test
    void declaredMethodCallCapturesReceiverAndMethod() throws ParseException {
        ParsedStatement s = parseOne("int x = owner.compute();");
        assertEquals(Rhs.Kind.METHOD_CALL, s.rhs.kind);
        assertEquals("owner", s.rhs.receiver);
        assertEquals("compute", s.rhs.methodName);
    }

    // -- literal type inference / coercion (regression: double/float bug) --

    @Test
    void intLiteralInfersIntType() throws ParseException {
        ParsedStatement s = parseOne("int x = 5;");
        assertEquals(5, s.rhs.literalValue);
        assertEquals("int", s.rhs.literalType);
    }

    @Test
    void wholeNumberLiteralDeclaredAsDoubleCoercesToDouble() throws ParseException {
        ParsedStatement s = parseOne("double d = 5;");
        assertEquals(5.0, s.rhs.literalValue);
        assertEquals("double", s.rhs.literalType);
    }

    @Test
    void wholeNumberLiteralDeclaredAsFloatCoercesToFloat() throws ParseException {
        ParsedStatement s = parseOne("float f = 3;");
        assertEquals(3.0, s.rhs.literalValue);
        assertEquals("float", s.rhs.literalType);
    }

    @Test
    void decimalLiteralIsAlwaysDoubleRegardlessOfDeclaredType() throws ParseException {
        ParsedStatement s = parseOne("double e = 5.5;");
        assertEquals(5.5, s.rhs.literalValue);
        assertEquals("double", s.rhs.literalType);
    }

    @Test
    void stringLiteralStripsQuotes() throws ParseException {
        ParsedStatement s = parseOne("String name = \"Rahul\";");
        assertEquals("Rahul", s.rhs.literalValue);
        assertEquals("String", s.rhs.literalType);
    }

    @Test
    void booleanLiteral() throws ParseException {
        ParsedStatement s = parseOne("boolean flag = true;");
        assertEquals(true, s.rhs.literalValue);
        assertEquals("boolean", s.rhs.literalType);
    }

    // -- plain assignment --------------------------------------------------

    @Test
    void reassignmentToNewObject() throws ParseException {
        ParsedStatement s = parseOne("s = new Student();");
        assertFalse(s.isDeclaration);
        assertEquals("s", s.targetVar);
        assertEquals(Rhs.Kind.NEW_OBJECT, s.rhs.kind);
    }

    @Test
    void assignmentToNull() throws ParseException {
        ParsedStatement s = parseOne("s = null;");
        assertEquals(Rhs.Kind.NULL, s.rhs.kind);
    }

    @Test
    void assignmentToLiteral() throws ParseException {
        ParsedStatement s = parseOne("x = 42;");
        assertEquals(42, s.rhs.literalValue);
    }

    // -- field assignment (regression: field-assign-to-new-object bug) -----

    @Test
    void fieldAssignmentToNewObjectIsSupported() throws ParseException {
        ParsedStatement s = parseOne("node.next = new Node();");
        assertTrue(s.isFieldAssignment);
        assertEquals("node", s.fieldOwner);
        assertEquals("next", s.fieldName);
        assertEquals(Rhs.Kind.NEW_OBJECT, s.rhs.kind);
        assertEquals("Node", s.rhs.className);
    }

    @Test
    void fieldAssignmentToLiteral() throws ParseException {
        ParsedStatement s = parseOne("s.age = 20;");
        assertTrue(s.isFieldAssignment);
        assertEquals(20, s.rhs.literalValue);
    }

    @Test
    void fieldAssignmentToNull() throws ParseException {
        ParsedStatement s = parseOne("s.name = null;");
        assertTrue(s.isFieldAssignment);
        assertEquals(Rhs.Kind.NULL, s.rhs.kind);
    }

    @Test
    void fieldAssignmentToVarRef() throws ParseException {
        ParsedStatement s = parseOne("a.next = b;");
        assertTrue(s.isFieldAssignment);
        assertEquals(Rhs.Kind.VAR_REF, s.rhs.kind);
        assertEquals("b", s.rhs.varRefName);
    }

    // -- method calls --------------------------------------------------------

    @Test
    void bareMethodCallNoReceiver() throws ParseException {
        ParsedStatement s = parseOne("display();");
        assertTrue(s.isBareMethodCall());
        assertNull(s.rhs.receiver);
        assertEquals("display", s.rhs.methodName);
    }

    @Test
    void bareMethodCallWithReceiver() throws ParseException {
        ParsedStatement s = parseOne("owner.greet();");
        assertTrue(s.isBareMethodCall());
        assertEquals("owner", s.rhs.receiver);
        assertEquals("greet", s.rhs.methodName);
    }

    // -- whole-script parsing -------------------------------------------------

    @Test
    void parseIgnoresBlankLinesAndComments() throws ParseException {
        String script = String.join("\n",
                "Student s = new Student();",
                "",
                "// a comment",
                "s.name = \"A\";");
        List<ParsedStatement> statements = parser.parse(script);
        assertEquals(2, statements.size());
    }

    @Test
    void parseTracksOneBasedLineNumbersAcrossBlankLines() throws ParseException {
        String script = String.join("\n",
                "",
                "int x = 1;");
        List<ParsedStatement> statements = parser.parse(script);
        assertEquals(2, statements.get(0).sourceLine);
    }

    // -- errors -------------------------------------------------------------

    @Test
    void unsupportedStatementThrowsParseExceptionWithLineNumber() {
        ParseException ex = assertThrows(ParseException.class, () -> parser.parse("this is not valid;"));
        assertEquals(1, ex.getLineNumber());
    }

    @Test
    void unterminatedStatementIsRejected() {
        assertThrows(ParseException.class, () -> parser.parse("int x = 5"));
    }
}
