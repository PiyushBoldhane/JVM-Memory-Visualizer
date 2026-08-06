package com.jvmvisualizer.engine;

import com.jvmvisualizer.model.HeapObject;
import com.jvmvisualizer.model.HeapRef;
import com.jvmvisualizer.model.MemoryState;
import com.jvmvisualizer.model.StackFrame;
import com.jvmvisualizer.model.StringPoolRef;
import com.jvmvisualizer.model.Variable;
import com.jvmvisualizer.parser.ParsedStatement;
import com.jvmvisualizer.parser.Rhs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Executes parsed statements against a {@link MemoryState}, producing one
 * timeline snapshot per visible change (FR-11 Step-by-Step Execution). A
 * bare or captured method call produces two snapshots — a "call" (frame
 * pushed) and a "return" (frame popped) — per FR-6.
 *
 * This is a simulation, not an interpreter: method bodies are never
 * actually executed (the SRS scope explicitly excludes running arbitrary
 * Java), so a captured method-call result is always modeled as an unknown
 * value rather than a computed one.
 */
public class SimulationEngine {

    /** Parses nothing; just runs already-parsed statements from a fresh main() frame. */
    public List<MemoryState> run(List<ParsedStatement> statements) {
        MemoryState state = new MemoryState();
        state.pushFrame(new StackFrame("main"));

        List<MemoryState> timeline = new ArrayList<>();
        state.setStepDescription("Program start — main() frame pushed");
        state.setSourceLine(-1);
        timeline.add(state.copy());

        for (ParsedStatement stmt : statements) {
            execute(stmt, state, timeline);
        }
        return timeline;
    }

    /** Runs GC against the last state in an existing timeline and appends the before/after steps to it. */
    public void appendGarbageCollection(List<MemoryState> timeline) {
        if (timeline.isEmpty()) {
            return;
        }
        MemoryState current = timeline.get(timeline.size() - 1);
        Set<Integer> reachable = computeReachable(current);

        MemoryState before = current.copy();
        for (HeapObject obj : before.getHeap().values()) {
            boolean isReachable = reachable.contains(obj.getId());
            obj.setReachable(isReachable);
            obj.setMarkedForCollection(!isReachable);
        }
        long garbageCount = before.getHeap().values().stream().filter(HeapObject::isMarkedForCollection).count();
        before.setStepDescription(garbageCount == 0
                ? "Garbage Collection run — no unreachable objects found"
                : "Garbage Collection — " + garbageCount + " unreachable object(s) marked (highlighted)");
        timeline.add(before);

        MemoryState after = before.copy();
        after.getHeap().values().removeIf(o -> !o.isReachable());
        after.setStepDescription(garbageCount == 0
                ? "Garbage Collection complete — heap unchanged"
                : "Garbage Collection complete — " + garbageCount + " object(s) swept");
        timeline.add(after);
    }

    // ------------------------------------------------------------------
    // Statement execution
    // ------------------------------------------------------------------

    private void execute(ParsedStatement stmt, MemoryState state, List<MemoryState> timeline) {
        if (stmt.isBareMethodCall()) {
            executeMethodCall(stmt.rhs.receiver, stmt.rhs.methodName, stmt, state, timeline);
            return;
        }

        if (stmt.isFieldAssignment) {
            executeFieldAssignment(stmt, state, timeline);
            return;
        }

        if (stmt.rhs.kind == Rhs.Kind.METHOD_CALL) {
            // Declared or plain assignment capturing a call result.
            executeMethodCall(stmt.rhs.receiver, stmt.rhs.methodName, stmt, state, timeline);
            // Return value isn't modeled (no method bodies are executed) - the variable becomes null.
            bind(state, stmt, null);
            state.setStepDescription(stmt.targetVar + " = result of " + callLabel(stmt.rhs) + "()  (return value not modeled)");
            state.setSourceLine(stmt.sourceLine);
            timeline.add(state.copy());
            return;
        }

        Object value = evaluate(stmt.rhs, state, stmt.declaredType);
        bind(state, stmt, value);
        state.setStepDescription(describeBinding(stmt, value));
        state.setSourceLine(stmt.sourceLine);
        timeline.add(state.copy());
    }

    private void executeMethodCall(String receiver, String methodName, ParsedStatement stmt,
                                    MemoryState state, List<MemoryState> timeline) {
        String label = receiver != null ? receiver + "." + methodName : methodName;
        state.pushFrame(new StackFrame(methodName));
        state.setStepDescription("Calling " + label + "()  — new stack frame pushed");
        state.setSourceLine(stmt.sourceLine);
        timeline.add(state.copy());

        state.popFrame();
        state.setStepDescription("Returning from " + label + "()  — stack frame popped");
        state.setSourceLine(stmt.sourceLine);
        timeline.add(state.copy());
    }

    private void executeFieldAssignment(ParsedStatement stmt, MemoryState state, List<MemoryState> timeline) {
        Variable owner = state.resolveVariable(stmt.fieldOwner);
        if (owner == null || !(owner.getValue() instanceof HeapRef)) {
            state.setStepDescription("Cannot set " + stmt.fieldOwner + "." + stmt.fieldName + " — " + stmt.fieldOwner + " is null or not an object");
            state.setSourceLine(stmt.sourceLine);
            timeline.add(state.copy());
            return;
        }
        HeapObject obj = state.getObject((HeapRef) owner.getValue());
        Object value = evaluate(stmt.rhs, state, null);
        obj.setField(stmt.fieldName, value);
        state.setStepDescription(stmt.fieldOwner + "." + stmt.fieldName + " = " + describe(value));
        state.setSourceLine(stmt.sourceLine);
        timeline.add(state.copy());
    }

    private String callLabel(Rhs rhs) {
        return rhs.receiver != null ? rhs.receiver + "." + rhs.methodName : rhs.methodName;
    }

    /** Evaluates a non-method-call RHS into a storable value. */
    private Object evaluate(Rhs rhs, MemoryState state, String declaredType) {
        switch (rhs.kind) {
            case NONE:
            case NULL:
                return null;
            case VAR_REF: {
                Variable source = state.resolveVariable(rhs.varRefName);
                return source == null ? null : source.getValue();
            }
            case NEW_OBJECT: {
                int id = state.allocateHeapId();
                HeapObject obj = new HeapObject(id, rhs.className, id);
                state.getHeap().put(id, obj);
                return new HeapRef(id);
            }
            case LITERAL:
                if ("String".equals(rhs.literalType)) {
                    String text = (String) rhs.literalValue;
                    state.getStringPool().add(text);
                    return new StringPoolRef(text);
                }
                return rhs.literalValue;
            default:
                return null;
        }
    }

    /** Stores an evaluated value into a declared local, a plain reassignment target, or a static field. */
    private void bind(MemoryState state, ParsedStatement stmt, Object value) {
        if (stmt.isStatic) {
            state.getStaticArea().put(stmt.targetVar, value);
            return;
        }
        if (stmt.isDeclaration) {
            StackFrame frame = state.currentFrame();
            Variable existing = frame.findLocal(stmt.targetVar);
            if (existing != null) {
                existing.setValue(value);
            } else {
                frame.addLocal(new Variable(stmt.targetVar, stmt.declaredType, value));
            }
            return;
        }
        Variable existing = state.resolveVariable(stmt.targetVar);
        if (existing != null) {
            existing.setValue(value);
        } else if (state.getStaticArea().containsKey(stmt.targetVar)) {
            state.getStaticArea().put(stmt.targetVar, value);
        } else {
            // Lenient fallback: an assignment to an undeclared name still creates it,
            // so a small script never hard-fails mid-demo.
            state.currentFrame().addLocal(new Variable(stmt.targetVar, "var", value));
        }
    }

    private String describeBinding(ParsedStatement stmt, Object value) {
        String prefix = stmt.isStatic ? "[static] " : "";
        switch (stmt.rhs.kind) {
            case NEW_OBJECT:
                HeapRef ref = (HeapRef) value;
                return prefix + "Allocated " + stmt.rhs.className + " object at " + ref.simulatedAddress()
                        + "  —  " + stmt.targetVar + " → " + ref.simulatedAddress();
            case NULL:
                return prefix + stmt.targetVar + " = null";
            case VAR_REF:
                return prefix + stmt.targetVar + " now " + describe(value);
            case LITERAL:
                if (value instanceof StringPoolRef) {
                    return prefix + "\"" + ((StringPoolRef) value).value() + "\" interned in String Pool  —  "
                            + stmt.targetVar + " → pool entry";
                }
                return prefix + stmt.targetVar + " = " + value + "  (primitive)";
            case NONE:
            default:
                return prefix + "Declared " + stmt.declaredType + " " + stmt.targetVar + " (uninitialized)";
        }
    }

    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof HeapRef) {
            return "references object at " + value;
        }
        if (value instanceof StringPoolRef) {
            return "references pooled string " + value;
        }
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Garbage collection reachability (FR-7)
    // ------------------------------------------------------------------

    private Set<Integer> computeReachable(MemoryState state) {
        Set<Integer> reachable = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (StackFrame frame : state.getStack()) {
            for (Variable v : frame.getLocals()) {
                enqueueIfRef(v.getValue(), reachable, queue);
            }
        }
        for (Object v : state.getStaticArea().values()) {
            enqueueIfRef(v, reachable, queue);
        }

        while (!queue.isEmpty()) {
            int id = queue.poll();
            HeapObject obj = state.getHeap().get(id);
            if (obj == null) {
                continue;
            }
            for (Object fieldValue : obj.getFields().values()) {
                enqueueIfRef(fieldValue, reachable, queue);
            }
        }
        return reachable;
    }

    private void enqueueIfRef(Object value, Set<Integer> reachable, ArrayDeque<Integer> queue) {
        if (value instanceof HeapRef) {
            int id = ((HeapRef) value).id();
            if (reachable.add(id)) {
                queue.add(id);
            }
        }
    }
}
