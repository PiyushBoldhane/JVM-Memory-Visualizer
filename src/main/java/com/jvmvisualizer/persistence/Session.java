package com.jvmvisualizer.persistence;

import java.time.LocalDateTime;

/** A saved Playground session (FR-14): just the source code plus a bit of metadata. */
public class Session {
    public String name;
    public String sourceCode;
    public String savedAt;

    public Session() {
    }

    public Session(String name, String sourceCode) {
        this.name = name;
        this.sourceCode = sourceCode;
        this.savedAt = LocalDateTime.now().toString();
    }
}
