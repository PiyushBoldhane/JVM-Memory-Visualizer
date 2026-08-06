package com.jvmvisualizer.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A single, fully independent snapshot of simulated JVM memory at one point
 * in execution. The {@link com.jvmvisualizer.engine.SimulationEngine}
 * produces one of these per executed statement; the UI's Timeline Control
 * (FR-11) simply steps through the resulting list, re-rendering each one.
 */
public class MemoryState {
    private final Deque<StackFrame> stack = new ArrayDeque<>();
    private final Map<Integer, HeapObject> heap = new LinkedHashMap<>();
    private final Map<String, Object> staticArea = new LinkedHashMap<>();
    private final Set<String> stringPool = new LinkedHashSet<>();
    private int nextHeapId = 1;

    /** Human-readable narration of what this step did, shown under the visualization. */
    private String stepDescription = "";
    /** The source line this step corresponds to, for highlighting in the code editor. */
    private int sourceLine = -1;

    public Deque<StackFrame> getStack() {
        return stack;
    }

    /** Stack frames ordered top-of-stack first (most recently pushed call first). */
    public Iterable<StackFrame> framesTopFirst() {
        return stack;
    }

    public void pushFrame(StackFrame frame) {
        stack.push(frame);
    }

    public StackFrame popFrame() {
        return stack.poll();
    }

    public StackFrame currentFrame() {
        return stack.peek();
    }

    public Map<Integer, HeapObject> getHeap() {
        return heap;
    }

    public Map<String, Object> getStaticArea() {
        return staticArea;
    }

    public Set<String> getStringPool() {
        return stringPool;
    }

    public int allocateHeapId() {
        return nextHeapId++;
    }

    /** Total objects ever allocated so far (FR-13), independent of how many are still on the heap. */
    public int allocatedCount() {
        return nextHeapId - 1;
    }

    public String getStepDescription() {
        return stepDescription;
    }

    public void setStepDescription(String stepDescription) {
        this.stepDescription = stepDescription;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }

    /** Resolves a variable by name, searching from the top of the stack down (innermost scope wins). */
    public Variable resolveVariable(String name) {
        for (StackFrame frame : stack) {
            Variable v = frame.findLocal(name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public HeapObject getObject(HeapRef ref) {
        return ref == null ? null : heap.get(ref.id());
    }

    /** Deep copy so that past timeline steps are never mutated by later ones. */
    public MemoryState copy() {
        MemoryState clone = new MemoryState();
        Iterator<StackFrame> it = stack.descendingIterator();
        while (it.hasNext()) {
            clone.stack.push(it.next().copy());
        }
        for (Map.Entry<Integer, HeapObject> e : heap.entrySet()) {
            clone.heap.put(e.getKey(), e.getValue().copy());
        }
        clone.staticArea.putAll(this.staticArea);
        clone.stringPool.addAll(this.stringPool);
        clone.nextHeapId = this.nextHeapId;
        clone.stepDescription = this.stepDescription;
        clone.sourceLine = this.sourceLine;
        return clone;
    }
}
