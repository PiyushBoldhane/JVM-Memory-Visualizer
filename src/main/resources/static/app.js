// Port of com.jvmvisualizer.ui.MainController: owns the timeline state and
// wires the DOM controls to the /api/simulate endpoints instead of to the
// SimulationEngine directly (that now runs server-side).

const codeArea = document.getElementById("codeArea");
const exampleSelect = document.getElementById("exampleSelect");
const runBtn = document.getElementById("runBtn");
const gcBtn = document.getElementById("gcBtn");
const errorLabel = document.getElementById("errorLabel");
const vizStage = document.getElementById("vizStage");
const restartBtn = document.getElementById("restartBtn");
const prevBtn = document.getElementById("prevBtn");
const nextBtn = document.getElementById("nextBtn");
const playPauseBtn = document.getElementById("playPauseBtn");
const stepLabel = document.getElementById("stepLabel");
const descriptionLabel = document.getElementById("descriptionLabel");
const statsLabel = document.getElementById("statsLabel");

let timeline = [];
let currentIndex = -1;
let playing = false;
let autoPlayTimer = null;

function init() {
  for (const name of Object.keys(EXAMPLES)) {
    const opt = document.createElement("option");
    opt.value = name;
    opt.textContent = name;
    exampleSelect.appendChild(opt);
  }
  exampleSelect.value = "Object Creation & References";
  codeArea.value = EXAMPLES[exampleSelect.value];

  exampleSelect.addEventListener("change", () => {
    codeArea.value = EXAMPLES[exampleSelect.value] || "";
  });

  runBtn.addEventListener("click", runSimulation);
  gcBtn.addEventListener("click", runGC);
  restartBtn.addEventListener("click", () => goToStep(0));
  prevBtn.addEventListener("click", () => goToStep(currentIndex - 1));
  nextBtn.addEventListener("click", () => goToStep(currentIndex + 1));
  playPauseBtn.addEventListener("click", toggleAutoPlay);

  updateButtonStates();
}

async function runSimulation() {
  stopAutoPlay();
  errorLabel.textContent = "";
  try {
    const res = await fetch("/api/simulate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code: codeArea.value }),
    });
    if (!res.ok) {
      const err = await res.json();
      errorLabel.textContent = err.message || "Parse error";
      return;
    }
    timeline = await res.json();
    currentIndex = 0;
    render();
  } catch (e) {
    errorLabel.textContent = "Could not reach the server: " + e.message;
  }
}

async function runGC() {
  if (timeline.length === 0) return;
  stopAutoPlay();
  try {
    const res = await fetch("/api/simulate-with-gc", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code: codeArea.value }),
    });
    if (!res.ok) {
      const err = await res.json();
      errorLabel.textContent = err.message || "Parse error";
      return;
    }
    timeline = await res.json();
    // Land on the "before sweep" step first, same as the desktop app.
    goToStep(timeline.length - 2);
  } catch (e) {
    errorLabel.textContent = "Could not reach the server: " + e.message;
  }
}

function goToStep(index) {
  if (timeline.length === 0) return;
  currentIndex = Math.max(0, Math.min(index, timeline.length - 1));
  render();
}

function advanceAutoPlay() {
  if (currentIndex >= timeline.length - 1) {
    stopAutoPlay();
    return;
  }
  goToStep(currentIndex + 1);
}

function toggleAutoPlay() {
  if (timeline.length === 0) return;
  if (playing) {
    stopAutoPlay();
  } else {
    if (currentIndex >= timeline.length - 1) currentIndex = 0;
    playing = true;
    playPauseBtn.textContent = "⏸ Pause";
    autoPlayTimer = setInterval(advanceAutoPlay, 900);
  }
}

function stopAutoPlay() {
  playing = false;
  if (autoPlayTimer) clearInterval(autoPlayTimer);
  autoPlayTimer = null;
  playPauseBtn.textContent = "▶ Auto Play";
}

function render() {
  if (timeline.length === 0 || currentIndex < 0) return;
  const state = timeline[currentIndex];
  renderState(vizStage, state);
  descriptionLabel.textContent = state.stepDescription;
  stepLabel.textContent = `Step ${currentIndex + 1} / ${timeline.length}`;
  updateStats(state);
  updateButtonStates();
}

function updateStats(state) {
  const alive = state.heap.length;
  const garbage = state.heap.filter((o) => o.markedForCollection).length;
  const stackDepth = state.stack.length;
  statsLabel.textContent =
    `Objects Created: ${state.allocatedCount}    Objects Alive: ${alive}    ` +
    `Garbage Objects: ${garbage}    Stack Depth: ${stackDepth}    ` +
    `Heap Usage (simulated): ${alive * 16} bytes`;
}

function updateButtonStates() {
  const has = timeline.length > 0;
  prevBtn.disabled = !has || currentIndex <= 0;
  nextBtn.disabled = !has || currentIndex >= timeline.length - 1;
  restartBtn.disabled = !has;
  playPauseBtn.disabled = !has;
  gcBtn.disabled = !has;
}

init();
