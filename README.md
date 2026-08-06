# JVM Memory Visualizer

An educational JavaFX desktop application that visually demonstrates how Java
programs execute inside the Java Virtual Machine — Stack, Heap, Static Area,
String Pool, references, and Garbage Collection — one statement at a time.

Built from the project's Software Requirements Specification (SRS, kept
locally, not in this repo). This is the **Phase 1 MVP**: the core
visualization loop (FR-2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14). Dashboard,
Learning Mode, and Quiz Module (FR-1, 12, 15) are planned for later phases.

## Features

- **Code Playground** — write a small, supported subset of Java and press
  *Run Simulation* to visualize it.
- **Stack Visualization** — method frames, local variables, parameters.
- **Heap Visualization** — allocated objects with simulated memory addresses
  and fields.
- **Reference Arrows** — live arrows from variables/fields to the objects or
  pooled strings they point to.
- **Method Call Animation** — each call pushes a stack frame; each return
  pops it.
- **Garbage Collection Simulation** — real reachability analysis (BFS from
  stack + static roots, following nested object references); unreachable
  objects are highlighted red, then swept, via the *Run GC* button.
- **String Pool Visualization** — string literals are interned; two
  variables assigned the same literal point at one pooled entry.
- **Static Memory** — `static` fields live in their own area for the
  program's lifetime.
- **Timeline Control** — Restart / Prev / Next / Auto Play through every
  step, with a plain-English description of what just happened.
- **Memory Statistics** — objects created, objects alive, garbage objects,
  stack depth, simulated heap usage.
- **Session Save/Load** — save Playground code to a local JSON file and
  reload it later.
- **Preset Examples** — five ready-to-run scripts covering references, GC,
  string pool, method calls, and statics.
- **Dark theme** UI.

## Tech Stack

- Java 17+
- JavaFX 21 (UI + Canvas-based rendering — no external diagramming tool)
- Maven
- Gson (session persistence as local JSON; no database required for v1)

## Getting Started

### Prerequisites

- JDK 17 or newer
- Maven 3.9+

### Run

```bash
mvn javafx:run
```

### Build a jar

```bash
mvn package
```

## How to use it

1. Pick a preset from the **Load Example** dropdown, or write your own code
   in the Playground.
2. Click **▶ Run Simulation**.
3. Step through with **Prev / Next**, or click **▶ Auto Play** to watch it
   run automatically.
4. Click **🗑 Run GC** at any point to trigger garbage collection on the
   current memory state — unreachable objects are marked, then swept.
5. Use **Save Session / Load Session** to keep and reopen Playground scripts.

## Supported statement syntax

The Playground accepts a deliberately small, predefined subset of Java —
one statement per line, each ending in `;` — rather than executing
arbitrary Java programs:

```java
Type name = new ClassName();   // object creation
Type name;                     // bare declaration
Type name = otherName;         // reference / primitive copy
Type name = "literal";         // literal init (String literals are pooled)
Type name = null;               // null init
static Type name = value;       // static field
name = new ClassName();         // reassignment to a new object
name = otherName;               // assignment
name = null;                    // null assignment
owner.field = value;            // field assignment
name();  owner.method();        // method call
name = owner.method();          // method call with captured result
```

Loops, conditionals, expressions, and real method bodies are not supported
in this phase.

## Project structure

```
src/main/java/com/jvmvisualizer/
  app/            Application entry point
  model/          Memory model: HeapObject, Variable, StackFrame, MemoryState, ...
  parser/         CodeParser — turns source text into ParsedStatement objects
  engine/         SimulationEngine — executes statements into a timeline of
                  MemoryState snapshots; GC reachability analysis
  persistence/    Session + SessionStore (Gson-based local JSON save/load)
  examples/       Preset demo scripts
  ui/             MainView (layout), MainController (wiring), 
                  MemoryCanvasRenderer (draws each snapshot)
src/main/resources/css/dark-theme.css
sessions/         Saved sessions (created at runtime)
```

## Architecture

```
User → JavaFX UI → Controller → Simulation Engine → Memory Model → Visualization Engine (Canvas)
```

Each executed statement produces an independent, deep-copied `MemoryState`
snapshot appended to a timeline list. Stepping back and forth is just
indexing into that list — nothing is re-executed or undone.

## Roadmap

- FR-1 Dashboard
- FR-12 Learning Mode (per-topic explanation + animation + quiz)
- FR-15 Quiz Module
- Broader syntax support (see project discussion — a real Java grammar via
  a parser library, or JDI-based execution of real code, are the two
  serious options for accepting arbitrary Java)
- Arrays/collections, multithreading, generational GC (SRS §9 Future
  Enhancements)
