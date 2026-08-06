package com.jvmvisualizer.examples;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Execute predefined memory simulations" (SRS Scope / FR-1 Recent Simulations).
 * Small canned scripts that exercise each concept the SRS calls out.
 */
public final class ExampleScripts {

    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("Object Creation & References", String.join("\n",
                "Student s = new Student();",
                "Student t = s;",
                "s.name = \"Rahul\";",
                "s.age = 20;"));

        ALL.put("Garbage Collection", String.join("\n",
                "Student s = new Student();",
                "s.name = \"Ananya\";",
                "s = null;"));

        ALL.put("String Pool", String.join("\n",
                "String a = \"Java\";",
                "String b = \"Java\";",
                "String c = \"Hello\";"));

        ALL.put("Method Calls", String.join("\n",
                "display();",
                "calculate();"));

        ALL.put("Static Memory", String.join("\n",
                "static int totalStudents = 0;",
                "static int MAX_SIZE = 100;",
                "Student s = new Student();"));
    }

    private ExampleScripts() {
    }
}
