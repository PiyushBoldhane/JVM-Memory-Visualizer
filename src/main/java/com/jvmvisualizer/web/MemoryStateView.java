package com.jvmvisualizer.web;

import com.jvmvisualizer.model.HeapObject;
import com.jvmvisualizer.model.HeapRef;
import com.jvmvisualizer.model.MemoryState;
import com.jvmvisualizer.model.StackFrame;
import com.jvmvisualizer.model.StringPoolRef;
import com.jvmvisualizer.model.Variable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link MemoryState} into a plain, explicitly-tagged JSON shape
 * for the web frontend. {@code MemoryState} itself is left untouched — its
 * {@code Object}-typed values (null / HeapRef / StringPoolRef / primitive)
 * are unambiguous in Java via {@code instanceof}, but would serialize to
 * ambiguous plain JSON objects by default. Every value here is instead
 * rendered as {@code {"kind": ..., ...}} so the frontend never has to guess.
 */
public final class MemoryStateView {

    public String stepDescription;
    public int sourceLine;
    public List<FrameView> stack = new ArrayList<>();
    public List<HeapObjectView> heap = new ArrayList<>();
    public Map<String, ValueView> staticArea = new LinkedHashMap<>();
    public List<String> stringPool = new ArrayList<>();
    public int allocatedCount;

    public static class FrameView {
        public String methodName;
        public List<VariableView> locals = new ArrayList<>();
    }

    public static class VariableView {
        public String name;
        public String type;
        public ValueView value;
    }

    public static class HeapObjectView {
        public int id;
        public String className;
        public String address;
        public boolean reachable;
        public boolean markedForCollection;
        public Map<String, ValueView> fields = new LinkedHashMap<>();
    }

    /** kind: "null" | "ref" (heap object) | "string" (pooled literal) | "primitive" */
    public static class ValueView {
        public String kind;
        public Object value;
        public String address;

        static ValueView ofNull() {
            ValueView v = new ValueView();
            v.kind = "null";
            return v;
        }

        static ValueView ofRef(HeapRef ref) {
            ValueView v = new ValueView();
            v.kind = "ref";
            v.value = ref.id();
            v.address = ref.simulatedAddress();
            return v;
        }

        static ValueView ofString(StringPoolRef ref) {
            ValueView v = new ValueView();
            v.kind = "string";
            v.value = ref.value();
            return v;
        }

        static ValueView ofPrimitive(Object raw) {
            ValueView v = new ValueView();
            v.kind = "primitive";
            v.value = raw;
            return v;
        }
    }

    public static MemoryStateView from(MemoryState state) {
        MemoryStateView view = new MemoryStateView();
        view.stepDescription = state.getStepDescription();
        view.sourceLine = state.getSourceLine();
        view.allocatedCount = state.allocatedCount();

        for (StackFrame frame : state.getStack()) {
            FrameView fv = new FrameView();
            fv.methodName = frame.getMethodName();
            for (Variable v : frame.getLocals()) {
                VariableView vv = new VariableView();
                vv.name = v.getName();
                vv.type = v.getType();
                vv.value = toValueView(v.getValue());
                fv.locals.add(vv);
            }
            view.stack.add(fv);
        }

        for (HeapObject obj : state.getHeap().values()) {
            HeapObjectView hv = new HeapObjectView();
            hv.id = obj.getId();
            hv.className = obj.getClassName();
            hv.address = obj.simulatedAddress();
            hv.reachable = obj.isReachable();
            hv.markedForCollection = obj.isMarkedForCollection();
            for (Map.Entry<String, Object> field : obj.getFields().entrySet()) {
                hv.fields.put(field.getKey(), toValueView(field.getValue()));
            }
            view.heap.add(hv);
        }

        for (Map.Entry<String, Object> e : state.getStaticArea().entrySet()) {
            view.staticArea.put(e.getKey(), toValueView(e.getValue()));
        }

        view.stringPool.addAll(state.getStringPool());
        return view;
    }

    private static ValueView toValueView(Object raw) {
        if (raw == null) {
            return ValueView.ofNull();
        }
        if (raw instanceof HeapRef) {
            return ValueView.ofRef((HeapRef) raw);
        }
        if (raw instanceof StringPoolRef) {
            return ValueView.ofString((StringPoolRef) raw);
        }
        return ValueView.ofPrimitive(raw);
    }
}
