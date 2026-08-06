package com.jvmvisualizer.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An object allocated on the simulated heap (FR-4 Heap Visualization).
 * Fields are kept in insertion order so the UI renders them predictably.
 */
public class HeapObject {
    private final int id;
    private final String className;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final int allocatedAtStep;

    /** Reachability, computed fresh each time GC runs (FR-7). Not persisted between GC runs. */
    private boolean reachable = true;
    /** Marks this object as garbage that is about to be swept, for the "before GC" highlight frame. */
    private boolean markedForCollection = false;

    public HeapObject(int id, String className, int allocatedAtStep) {
        this.id = id;
        this.className = className;
        this.allocatedAtStep = allocatedAtStep;
    }

    public int getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setField(String name, Object value) {
        fields.put(name, value);
    }

    public int getAllocatedAtStep() {
        return allocatedAtStep;
    }

    public boolean isReachable() {
        return reachable;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public boolean isMarkedForCollection() {
        return markedForCollection;
    }

    public void setMarkedForCollection(boolean markedForCollection) {
        this.markedForCollection = markedForCollection;
    }

    public String simulatedAddress() {
        return new HeapRef(id).simulatedAddress();
    }

    public HeapObject copy() {
        HeapObject clone = new HeapObject(id, className, allocatedAtStep);
        clone.fields.putAll(this.fields);
        clone.reachable = this.reachable;
        clone.markedForCollection = this.markedForCollection;
        return clone;
    }
}
