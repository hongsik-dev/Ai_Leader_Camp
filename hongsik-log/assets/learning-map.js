(function () {
  const dataElement = document.getElementById("hongsik-learning-map-data");
  const canvas = document.getElementById("learning-map-canvas");

  if (!dataElement || !canvas) {
    return;
  }

  let graphData;

  try {
    graphData = JSON.parse(dataElement.textContent);
  } catch (error) {
    return;
  }

  const context = canvas.getContext("2d");
  const hint = document.getElementById("learning-map-hint");
  const filters = Array.from(document.querySelectorAll("[data-map-filter]"));
  const palette = {
    root: "#101820",
    category: "#2f78b7",
    post: "#cf7746",
    tag: "#6f757c",
  };
  const labels = {
    root: "캠프",
    category: "카테고리",
    post: "글",
    tag: "태그",
  };
  const radii = {
    root: 22,
    category: 18,
    post: 13,
    tag: 9,
  };

  let width = 960;
  let height = 580;
  let selectedFilter = "all";
  let visibleNodes = [];
  let visibleEdges = [];
  let hoveredNode = null;
  let draggedNode = null;
  let dragStart = null;
  let pointerMoved = false;
  let animationFrame = null;

  const nodes = (graphData.nodes || []).map((node, index) => {
    const angle = index * 2.399963229728653;
    const spread = 88 + index * 7;

    return {
      ...node,
      radius: radii[node.type] || 11,
      x: width / 2 + Math.cos(angle) * spread,
      y: height / 2 + Math.sin(angle) * spread,
      vx: 0,
      vy: 0,
    };
  });
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const edges = graphData.edges || [];

  function visibleIdSet() {
    const ids = new Set();

    if (selectedFilter === "all") {
      nodes.forEach((node) => ids.add(node.id));
      return ids;
    }

    ids.add("root");
    ids.add(selectedFilter);

    nodes.forEach((node) => {
      if (node.type !== "post") {
        return;
      }

      if (!Array.isArray(node.categoryIds) || !node.categoryIds.includes(selectedFilter)) {
        return;
      }

      ids.add(node.id);

      edges.forEach((edge) => {
        if (edge.source === node.id) {
          ids.add(edge.target);
        }
      });
    });

    return ids;
  }

  function updateVisibleGraph() {
    const ids = visibleIdSet();

    visibleNodes = nodes.filter((node) => ids.has(node.id));
    visibleEdges = edges
      .map((edge) => ({
        source: nodeById.get(edge.source),
        target: nodeById.get(edge.target),
      }))
      .filter((edge) => edge.source && edge.target && ids.has(edge.source.id) && ids.has(edge.target.id));

    seedVisibleLayout();
    warmup();
  }

  function seedVisibleLayout() {
    const centerX = width / 2;
    const centerY = height / 2;
    const base = Math.min(width, height);
    const rings = {
      root: 0,
      category: Math.max(78, base * 0.18),
      post: Math.max(142, base * 0.32),
      tag: Math.max(205, base * 0.43),
    };
    const offsets = {
      root: -Math.PI / 2,
      category: -Math.PI / 2,
      post: Math.PI / 12,
      tag: Math.PI / 4,
    };

    ["root", "category", "post", "tag"].forEach((type) => {
      const group = visibleNodes.filter((node) => node.type === type);

      group.forEach((node, index) => {
        const angle = offsets[type] + (Math.PI * 2 * index) / Math.max(1, group.length);
        const radius = rings[type] || 130;

        node.x = centerX + Math.cos(angle) * radius;
        node.y = centerY + Math.sin(angle) * radius;
        node.vx = 0;
        node.vy = 0;
      });
    });
  }

  function resizeCanvas() {
    const rect = canvas.getBoundingClientRect();
    const pixelRatio = window.devicePixelRatio || 1;

    width = Math.max(320, Math.round(rect.width));
    height = Math.max(360, Math.round(rect.height));
    canvas.width = Math.round(width * pixelRatio);
    canvas.height = Math.round(height * pixelRatio);
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);

    nodes.forEach((node) => {
      node.x = Math.min(width - node.radius, Math.max(node.radius, node.x));
      node.y = Math.min(height - node.radius, Math.max(node.radius, node.y));
    });
  }

  function warmup() {
    for (let index = 0; index < 80; index += 1) {
      stepPhysics();
    }
  }

  function stepPhysics() {
    const centerX = width / 2;
    const centerY = height / 2;

    visibleEdges.forEach((edge) => {
      const dx = edge.target.x - edge.source.x;
      const dy = edge.target.y - edge.source.y;
      const distance = Math.max(1, Math.hypot(dx, dy));
      const targetDistance = edge.source.type === "root" || edge.target.type === "root" ? 168 : 122;
      const force = (distance - targetDistance) * 0.005;
      const fx = (dx / distance) * force;
      const fy = (dy / distance) * force;

      if (edge.source !== draggedNode) {
        edge.source.vx += fx;
        edge.source.vy += fy;
      }
      if (edge.target !== draggedNode) {
        edge.target.vx -= fx;
        edge.target.vy -= fy;
      }
    });

    for (let firstIndex = 0; firstIndex < visibleNodes.length; firstIndex += 1) {
      for (let secondIndex = firstIndex + 1; secondIndex < visibleNodes.length; secondIndex += 1) {
        const first = visibleNodes[firstIndex];
        const second = visibleNodes[secondIndex];
        const dx = second.x - first.x;
        const dy = second.y - first.y;
        const distance = Math.max(1, Math.hypot(dx, dy));
        const strength = Math.min(2.4, 1800 / (distance * distance));
        const fx = (dx / distance) * strength;
        const fy = (dy / distance) * strength;

        if (first !== draggedNode) {
          first.vx -= fx;
          first.vy -= fy;
        }
        if (second !== draggedNode) {
          second.vx += fx;
          second.vy += fy;
        }
      }
    }

    visibleNodes.forEach((node) => {
      if (node === draggedNode) {
        return;
      }

      const centerStrength = node.type === "root" ? 0.03 : 0.003;
      node.vx += (centerX - node.x) * centerStrength;
      node.vy += (centerY - node.y) * centerStrength;
      node.vx *= 0.84;
      node.vy *= 0.84;
      node.x += node.vx;
      node.y += node.vy;
      node.x = Math.min(width - node.radius - 12, Math.max(node.radius + 12, node.x));
      node.y = Math.min(height - node.radius - 12, Math.max(node.radius + 12, node.y));
    });
  }

  function truncate(text, maxLength) {
    if (!text || text.length <= maxLength) {
      return text || "";
    }

    return `${text.slice(0, maxLength - 1)}…`;
  }

  function drawLabel(node) {
    if (width < 520 && node.type === "tag") {
      return;
    }

    const maxLengthByType = width < 520
      ? { root: 9, category: 9, post: 12, tag: 8 }
      : { root: 12, category: 14, post: 17, tag: 10 };
    const maxLength = maxLengthByType[node.type] || 12;
    const text = truncate(node.label, maxLength);
    const isMobilePost = width < 520 && node.type === "post";
    const labelX = isMobilePost
      ? node.x + (node.x < width / 2 ? -node.radius - 7 : node.radius + 7)
      : node.x;
    const labelY = isMobilePost ? node.y : node.y + node.radius + 7;

    context.font = node.type === "root" ? "700 14px sans-serif" : "650 12px sans-serif";
    context.textAlign = isMobilePost ? (node.x < width / 2 ? "right" : "left") : "center";
    context.textBaseline = isMobilePost ? "middle" : "top";
    context.lineWidth = 4;
    context.strokeStyle = "rgba(248, 245, 239, 0.94)";
    context.fillStyle = node.type === "root" ? "#101820" : "#3f464c";
    context.strokeText(text, labelX, labelY);
    context.fillText(text, labelX, labelY);
  }

  function draw() {
    context.clearRect(0, 0, width, height);

    context.lineCap = "round";
    visibleEdges.forEach((edge) => {
      context.beginPath();
      context.moveTo(edge.source.x, edge.source.y);
      context.lineTo(edge.target.x, edge.target.y);
      context.lineWidth = edge.source.type === "root" ? 1.9 : 1.15;
      context.strokeStyle = "rgba(47, 61, 76, 0.18)";
      context.stroke();
    });

    visibleNodes.forEach((node) => {
      const color = palette[node.type] || palette.post;

      context.beginPath();
      context.arc(node.x, node.y, node.radius + 4, 0, Math.PI * 2);
      context.fillStyle = node === hoveredNode ? "rgba(47, 120, 183, 0.18)" : "rgba(255, 254, 250, 0.86)";
      context.fill();

      context.beginPath();
      context.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
      context.fillStyle = color;
      context.fill();

      if (node.type === "post") {
        context.beginPath();
        context.arc(node.x, node.y, Math.max(4, node.radius - 6), 0, Math.PI * 2);
        context.fillStyle = "rgba(255, 254, 250, 0.9)";
        context.fill();
      }

      drawLabel(node);
    });
  }

  function animate() {
    for (let index = 0; index < 2; index += 1) {
      stepPhysics();
    }

    draw();
    animationFrame = window.requestAnimationFrame(animate);
  }

  function pointFromEvent(event) {
    const rect = canvas.getBoundingClientRect();

    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };
  }

  function hitTest(point) {
    for (let index = visibleNodes.length - 1; index >= 0; index -= 1) {
      const node = visibleNodes[index];
      const distance = Math.hypot(point.x - node.x, point.y - node.y);

      if (distance <= node.radius + 11) {
        return node;
      }
    }

    return null;
  }

  function updateHint(node) {
    if (!hint) {
      return;
    }

    if (!node) {
      hint.textContent = "노드를 클릭하면 해당 글이나 목록으로 이동합니다.";
      return;
    }

    const typeLabel = labels[node.type] || "노드";
    const date = node.date ? ` · ${node.date}` : "";
    hint.textContent = `${typeLabel}: ${node.label}${date}`;
  }

  canvas.addEventListener("pointermove", (event) => {
    const point = pointFromEvent(event);

    if (draggedNode) {
      draggedNode.x = Math.min(width - draggedNode.radius - 12, Math.max(draggedNode.radius + 12, point.x));
      draggedNode.y = Math.min(height - draggedNode.radius - 12, Math.max(draggedNode.radius + 12, point.y));
      draggedNode.vx = 0;
      draggedNode.vy = 0;
      pointerMoved = pointerMoved || Math.hypot(point.x - dragStart.x, point.y - dragStart.y) > 4;
      return;
    }

    hoveredNode = hitTest(point);
    canvas.classList.toggle("is-clickable", Boolean(hoveredNode && hoveredNode.url));
    updateHint(hoveredNode);
  });

  canvas.addEventListener("pointerleave", () => {
    if (!draggedNode) {
      hoveredNode = null;
      canvas.classList.remove("is-clickable");
      updateHint(null);
    }
  });

  canvas.addEventListener("pointerdown", (event) => {
    const point = pointFromEvent(event);
    const node = hitTest(point);

    if (!node) {
      return;
    }

    draggedNode = node;
    hoveredNode = node;
    dragStart = point;
    pointerMoved = false;
    canvas.setPointerCapture(event.pointerId);
  });

  canvas.addEventListener("pointerup", (event) => {
    if (!draggedNode) {
      return;
    }

    const node = draggedNode;
    draggedNode = null;
    canvas.releasePointerCapture(event.pointerId);

    if (!pointerMoved && node.url) {
      window.location.href = node.url;
    }
  });

  filters.forEach((button) => {
    button.addEventListener("click", () => {
      selectedFilter = button.dataset.mapFilter || "all";
      filters.forEach((filterButton) => filterButton.classList.toggle("is-active", filterButton === button));
      hoveredNode = null;
      updateHint(null);
      updateVisibleGraph();
    });
  });

  window.addEventListener("resize", () => {
    resizeCanvas();
    updateVisibleGraph();
  });

  resizeCanvas();
  updateVisibleGraph();
  animationFrame = window.requestAnimationFrame(animate);

  window.addEventListener("beforeunload", () => {
    if (animationFrame) {
      window.cancelAnimationFrame(animationFrame);
    }
  });
})();
