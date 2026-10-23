// Mirrors com.jvmvisualizer.examples.ExampleScripts — same canned scripts,
// kept in sync by hand since the web frontend has no access to the Java enum.
const EXAMPLES = {
  "Object Creation & References": [
    "Student s = new Student();",
    "Student t = s;",
    "s.name = \"Rahul\";",
    "s.age = 20;",
  ].join("\n"),

  "Garbage Collection": [
    "Student s = new Student();",
    "s.name = \"Ananya\";",
    "s = null;",
  ].join("\n"),

  "String Pool": [
    "String a = \"Java\";",
    "String b = \"Java\";",
    "String c = \"Hello\";",
  ].join("\n"),

  "Method Calls": [
    "display();",
    "calculate();",
  ].join("\n"),

  "Static Memory": [
    "static int totalStudents = 0;",
    "static int MAX_SIZE = 100;",
    "Student s = new Student();",
  ].join("\n"),

  "Linked Structure": [
    "Node a = new Node();",
    "Node b = new Node();",
    "a.next = new Node();",
    "a.value = 10;",
  ].join("\n"),
};
