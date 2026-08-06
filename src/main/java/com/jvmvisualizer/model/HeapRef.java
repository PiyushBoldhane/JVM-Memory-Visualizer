package com.jvmvisualizer.model;

import java.util.Objects;

/**
 * A reference to an object living on the heap, identified by its simulated
 * memory address (id). Kept as its own type (rather than a bare Integer) so
 * that "this value is a reference" is unambiguous everywhere it is stored -
 * in a {@link Variable}, in a {@link HeapObject} field, or in the static area.
 */
public final class HeapRef {
    private final int id;

    public HeapRef(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /** Simulated hex memory address, e.g. 0x1001, used purely for display. */
    public String simulatedAddress() {
        return String.format("0x%04X", 0x1000 + id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeapRef)) return false;
        return id == ((HeapRef) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return simulatedAddress();
    }
}
