// Renders one MemoryStateView snapshot (see com.jvmvisualizer.web.MemoryStateView)
// as real HTML elements for the stack/heap/static/pool panels, with an SVG
// overlay for the reference arrows. Swapped out from an earlier <canvas>
// version because canvas text renders as a fixed-resolution bitmap and goes
// soft on high-DPI/zoomed displays; HTML text is always rendered natively
// sharp by the browser at whatever the actual screen resolution is.

function valueLabel(v) {
  if (!v || v.kind === "null") return "null";
  if (v.kind === "ref") return v.address;
  if (v.kind === "string") return `"${v.value}"`;
  return String(v.value);
}

function clearChildren(el) {
  while (el.firstChild) el.removeChild(el.firstChild);
}

/** Renders one MemoryStateView snapshot into the DOM, then draws reference arrows over it. */
function renderState(stage, state) {
  const stackColumn = stage.querySelector("#stackColumn");
  const heapColumn = stage.querySelector("#heapColumn");
  const staticArea = stage.querySelector("#staticArea");
  const staticAreaContent = stage.querySelector("#staticAreaContent");
  const stringPool = stage.querySelector("#stringPool");
  const stringPoolContent = stage.querySelector("#stringPoolContent");
  const arrowLayer = stage.querySelector("#arrowLayer");

  // -- stack ---------------------------------------------------------
  clearChildren(stackColumn);
  const title1 = document.createElement("div");
  title1.className = "viz-column-title";
  title1.textContent = "STACK";
  stackColumn.appendChild(title1);

  const varRowEls = new Map(); // "frameIndex:varName" -> row element (for arrow anchors)

  state.stack.forEach((frame, frameIndex) => {
    const top = frameIndex === 0;
    const box = document.createElement("div");
    box.className = "viz-box stack-frame" + (top ? " top" : "");

    const titleEl = document.createElement("div");
    titleEl.className = "viz-box-title";
    titleEl.textContent = frame.methodName + "()" + (top ? "  ◀ top of stack" : "");
    box.appendChild(titleEl);

    if (frame.locals.length === 0) {
      const row = document.createElement("div");
      row.className = "viz-row no-fields";
      row.textContent = "(no locals)";
      box.appendChild(row);
    }
    for (const v of frame.locals) {
      const row = document.createElement("div");
      row.className = "viz-row";
      row.textContent = `${v.name} : ${v.type} = ${valueLabel(v.value)}`;
      box.appendChild(row);
      varRowEls.set(frameIndex + ":" + v.name, { row, value: v.value });
    }
    stackColumn.appendChild(box);
  });

  // -- heap ------------------------------------------------------------
  clearChildren(heapColumn);
  const title2 = document.createElement("div");
  title2.className = "viz-column-title";
  title2.textContent = "HEAP";
  heapColumn.appendChild(title2);

  const heapBoxEls = new Map(); // id -> box element (for arrow anchors)
  const heapFieldRowEls = []; // { row, value } for every heap object field, in draw order

  for (const obj of state.heap) {
    const garbage = obj.markedForCollection;
    const box = document.createElement("div");
    box.className = "viz-box heap-object" + (garbage ? " garbage" : "");

    const titleEl = document.createElement("div");
    titleEl.className = "viz-box-title";
    titleEl.textContent = obj.className + " @ " + obj.address + (garbage ? "  ✕ GARBAGE" : "");
    box.appendChild(titleEl);

    const fieldEntries = Object.entries(obj.fields);
    if (fieldEntries.length === 0) {
      const row = document.createElement("div");
      row.className = "viz-row no-fields";
      row.textContent = "(no fields set)";
      box.appendChild(row);
    }
    for (const [name, value] of fieldEntries) {
      const row = document.createElement("div");
      row.className = "viz-row";
      row.textContent = `${name} = ${valueLabel(value)}`;
      box.appendChild(row);
      heapFieldRowEls.push({ row, value });
    }

    heapColumn.appendChild(box);
    heapBoxEls.set(obj.id, box);
  }

  // -- static area -------------------------------------------------------
  const staticEntries = Object.entries(state.staticArea || {});
  staticArea.hidden = staticEntries.length === 0;
  clearChildren(staticAreaContent);
  for (const [key, value] of staticEntries) {
    const span = document.createElement("span");
    span.textContent = `${key} = ${valueLabel(value)}`;
    staticAreaContent.appendChild(span);
  }

  // -- string pool -------------------------------------------------------
  const pool = state.stringPool || [];
  stringPool.hidden = pool.length === 0;
  clearChildren(stringPoolContent);
  const poolChipEls = new Map(); // literal -> chip element

  for (const literal of pool) {
    const chip = document.createElement("span");
    chip.className = "pool-chip";
    chip.textContent = `"${literal}"`;
    stringPoolContent.appendChild(chip);
    poolChipEls.set(literal, chip);
  }

  // -- arrows: measure real element positions, draw on next frame --------
  // (requestAnimationFrame lets the browser finish layout of the elements
  // just inserted above before we read their bounding boxes.)
  requestAnimationFrame(() => drawArrows(stage, arrowLayer, varRowEls, heapBoxEls, heapFieldRowEls, poolChipEls));
}

function anchorFor(stageRect, el, side) {
  const r = el.getBoundingClientRect();
  const x = side === "right" ? r.right - stageRect.left : r.left - stageRect.left;
  const y = r.top - stageRect.top + r.height / 2;
  return { x, y };
}

function drawArrows(stage, arrowLayer, varRowEls, heapBoxEls, heapFieldRowEls, poolChipEls) {
  const stageRect = stage.getBoundingClientRect();
  arrowLayer.setAttribute("width", stage.scrollWidth);
  arrowLayer.setAttribute("height", stage.scrollHeight);
  clearChildren(arrowLayer);

  const defs = document.createElementNS("http://www.w3.org/2000/svg", "defs");
  const marker = document.createElementNS("http://www.w3.org/2000/svg", "marker");
  marker.setAttribute("id", "arrowHead");
  marker.setAttribute("markerWidth", "8");
  marker.setAttribute("markerHeight", "8");
  marker.setAttribute("refX", "6");
  marker.setAttribute("refY", "3");
  marker.setAttribute("orient", "auto");
  const markerPath = document.createElementNS("http://www.w3.org/2000/svg", "path");
  markerPath.setAttribute("d", "M0,0 L6,3 L0,6 Z");
  markerPath.setAttribute("fill", "#f9e2af");
  marker.appendChild(markerPath);
  defs.appendChild(marker);
  arrowLayer.appendChild(defs);

  function drawArrow(fromEl, value) {
    if (!fromEl) return;
    let toEl = null;
    if (value && value.kind === "ref") {
      toEl = heapBoxEls.get(value.value);
    } else if (value && value.kind === "string") {
      toEl = poolChipEls.get(value.value);
    }
    if (!toEl) return;

    const from = anchorFor(stageRect, fromEl, "right");
    const to = anchorFor(stageRect, toEl, "left");

    const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
    line.setAttribute("x1", from.x);
    line.setAttribute("y1", from.y);
    line.setAttribute("x2", to.x);
    line.setAttribute("y2", to.y);
    line.setAttribute("stroke", "#f9e2af");
    line.setAttribute("stroke-width", "1.6");
    line.setAttribute("marker-end", "url(#arrowHead)");
    arrowLayer.appendChild(line);
  }

  for (const { row, value } of varRowEls.values()) {
    drawArrow(row, value);
  }
  for (const { row, value } of heapFieldRowEls) {
    drawArrow(row, value);
  }
}
