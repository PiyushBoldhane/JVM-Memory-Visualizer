package com.jvmvisualizer.engine;

import com.jvmvisualizer.model.HeapObject;
import com.jvmvisualizer.model.HeapRef;
import com.jvmvisualizer.model.MemoryState;
import com.jvmvisualizer.model.StringPoolRef;
import com.jvmvisualizer.model.Variable;
import com.jvmvisualizer.parser.CodeParser;
import com.jvmvisualizer.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    private final CodeParser parser = new CodeParser();
    private final SimulationEngine engine = new SimulationEngine();

    private MemoryState lastStateOf(String script) throws ParseException {
        List<MemoryState> timeline = engine.run(parser.parse(script));
        return timeline.get(timeline.size() - 1);
    }

    @Test
    void runProducesInitialMainFrameAsFirstSnapshot() throws ParseException {
        List<MemoryState> timeline = engine.run(parser.parse("int x = 1;"));
        MemoryState first = timeline.get(0);
        assertEquals(1, first.getStack().size());
        assertEquals("main", first.currentFrame().getMethodName());
        assertTrue(first.getHeap().isEmpty());
    }

    @Test
    void oneTimelineSnapshotPerStatement() throws ParseException {
        List<MemoryState> timeline = engine.run(parser.parse(String.join("\n",
                "int a = 1;",
                "int b = 2;")));
        // initial snapshot + one per statement
        assertEquals(3, timeline.size());
    }

    @Test
    void objectCreationAllocatesHeapObjectAndBindsReference() throws ParseException {
        MemoryState state = lastStateOf("Student s = new Student();");
        assertEquals(1, state.getHeap().size());
        Variable s = state.resolveVariable("s");
        assertTrue(s.getValue() instanceof HeapRef);
        assertEquals(1, ((HeapRef) s.getValue()).id());
        assertEquals("Student", state.getHeap().get(1).getClassName());
    }

    @Test
    void aliasingSharesTheSameHeapObject() throws ParseException {
        MemoryState state = lastStateOf(String.join("\n",
                "Student s = new Student();",
                "Student t = s;",
                "s.name = \"Rahul\";"));

        Variable s = state.resolveVariable("s");
        Variable t = state.resolveVariable("t");
        assertEquals(s.getValue(), t.getValue());

        HeapObject obj = state.getObject((HeapRef) t.getValue());
        assertEquals("Rahul", ((StringPoolRef) obj.getFields().get("name")).value());
    }

    @Test
    void fieldAssignmentToNewObjectLinksTwoHeapObjects() throws ParseException {
        MemoryState state = lastStateOf(String.join("\n",
                "Node a = new Node();",
                "a.next = new Node();"));

        assertEquals(2, state.getHeap().size());
        Variable a = state.resolveVariable("a");
        HeapObject aObj = state.getObject((HeapRef) a.getValue());
        Object next = aObj.getFields().get("next");
        assertTrue(next instanceof HeapRef);
        assertEquals(2, ((HeapRef) next).id());
    }

    @Test
    void stringLiteralsAreInterned() throws ParseException {
        MemoryState state = lastStateOf(String.join("\n",
                "String a = \"Java\";",
                "String b = \"Java\";"));

        assertEquals(1, state.getStringPool().size());
        Variable a = state.resolveVariable("a");
        Variable b = state.resolveVariable("b");
        assertEquals(a.getValue(), b.getValue());
    }

    @Test
    void doubleLiteralDeclaredAsWholeNumberIsStoredAsDouble() throws ParseException {
        MemoryState state = lastStateOf("double price = 5;");
        Variable price = state.resolveVariable("price");
        assertEquals(5.0, price.getValue());
    }

    @Test
    void staticFieldLandsInStaticAreaNotOnStack() throws ParseException {
        MemoryState state = lastStateOf("static int MAX_SIZE = 100;");
        assertEquals(100, state.getStaticArea().get("MAX_SIZE"));
        assertNull(state.currentFrame().findLocal("MAX_SIZE"));
    }

    @Test
    void methodCallPushesThenPopsAFrame() throws ParseException {
        List<MemoryState> timeline = engine.run(parser.parse("display();"));
        // initial + call (pushed) + return (popped)
        assertEquals(3, timeline.size());

        MemoryState afterCall = timeline.get(1);
        assertEquals(2, afterCall.getStack().size());
        assertEquals("display", afterCall.currentFrame().getMethodName());

        MemoryState afterReturn = timeline.get(2);
        assertEquals(1, afterReturn.getStack().size());
        assertEquals("main", afterReturn.currentFrame().getMethodName());
    }

    @Test
    void capturedMethodCallResultIsNullSinceBodiesAreNotExecuted() throws ParseException {
        MemoryState state = lastStateOf("int x = compute();");
        Variable x = state.resolveVariable("x");
        assertNull(x.getValue());
    }

    @Test
    void settingFieldOnNullOwnerIsReportedWithoutCrashing() throws ParseException {
        MemoryState state = lastStateOf(String.join("\n",
                "Student s = null;",
                "s.age = 20;"));
        assertTrue(state.getStepDescription().contains("Cannot set"));
    }

    // -- garbage collection --------------------------------------------------

    @Test
    void unreachableObjectIsMarkedThenSweptAfterGc() throws ParseException {
        List<MemoryState> timeline = new ArrayList<>(engine.run(parser.parse(String.join("\n",
                "Student s = new Student();",
                "s = null;"))));
        engine.appendGarbageCollection(timeline);

        MemoryState beforeSweep = timeline.get(timeline.size() - 2);
        assertEquals(1, beforeSweep.getHeap().size());
        assertTrue(beforeSweep.getHeap().get(1).isMarkedForCollection());
        assertFalse(beforeSweep.getHeap().get(1).isReachable());

        MemoryState afterSweep = timeline.get(timeline.size() - 1);
        assertTrue(afterSweep.getHeap().isEmpty());
    }

    @Test
    void objectReachableThroughAliasSurvivesGc() throws ParseException {
        List<MemoryState> timeline = new ArrayList<>(engine.run(parser.parse(String.join("\n",
                "Student s = new Student();",
                "Student t = s;",
                "s = null;"))));
        engine.appendGarbageCollection(timeline);

        MemoryState afterSweep = timeline.get(timeline.size() - 1);
        assertEquals(1, afterSweep.getHeap().size());
    }

    @Test
    void objectReachableThroughHeapFieldChainSurvivesGc() throws ParseException {
        // a -> a.next (b). Dropping the direct "a" root but keeping a field
        // chain reachable from a still-rooted variable must keep both alive.
        List<MemoryState> timeline = new ArrayList<>(engine.run(parser.parse(String.join("\n",
                "Node a = new Node();",
                "a.next = new Node();"))));
        engine.appendGarbageCollection(timeline);

        MemoryState afterSweep = timeline.get(timeline.size() - 1);
        assertEquals(2, afterSweep.getHeap().size());
    }

    @Test
    void objectReachableOnlyThroughStaticAreaSurvivesGc() throws ParseException {
        List<MemoryState> timeline = new ArrayList<>(engine.run(parser.parse(String.join("\n",
                "static Student current = new Student();"))));
        engine.appendGarbageCollection(timeline);

        MemoryState afterSweep = timeline.get(timeline.size() - 1);
        assertEquals(1, afterSweep.getHeap().size());
    }

    @Test
    void gcOnTimelineWithNoUnreachableObjectsReportsHeapUnchanged() throws ParseException {
        List<MemoryState> timeline = new ArrayList<>(engine.run(parser.parse("Student s = new Student();")));
        engine.appendGarbageCollection(timeline);

        MemoryState afterSweep = timeline.get(timeline.size() - 1);
        assertEquals(1, afterSweep.getHeap().size());
        assertTrue(afterSweep.getStepDescription().contains("unchanged"));
    }

    @Test
    void appendGarbageCollectionOnEmptyTimelineIsANoOp() {
        List<MemoryState> timeline = new ArrayList<>();
        engine.appendGarbageCollection(timeline);
        assertTrue(timeline.isEmpty());
    }

    // -- timeline immutability -------------------------------------------

    @Test
    void earlierTimelineSnapshotsAreNotMutatedByLaterStatements() throws ParseException {
        List<MemoryState> timeline = engine.run(parser.parse(String.join("\n",
                "Student s = new Student();",
                "s.name = \"Rahul\";")));

        MemoryState afterCreation = timeline.get(1);
        // The snapshot taken right after creation must still show no fields,
        // even though a later statement sets one on the live object.
        HeapObject obj = afterCreation.getHeap().get(1);
        assertTrue(obj.getFields().isEmpty());
    }
}
