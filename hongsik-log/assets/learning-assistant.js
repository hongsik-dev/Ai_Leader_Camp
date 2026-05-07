(function () {
  const form = document.getElementById("learning-assistant-form");

  if (!form) {
    return;
  }

  const fields = {
    topic: document.getElementById("assistant-topic"),
    summary: document.getElementById("assistant-summary"),
    problem: document.getElementById("assistant-problem"),
    solution: document.getElementById("assistant-solution"),
    reflection: document.getElementById("assistant-reflection"),
    style: document.getElementById("assistant-style"),
    date: document.getElementById("assistant-date"),
    next: document.getElementById("assistant-next"),
  };
  const draftOutput = document.getElementById("assistant-draft");
  const promptOutput = document.getElementById("assistant-prompt");
  const tagsOutput = document.getElementById("assistant-tags");
  const planOutput = document.getElementById("assistant-plan");
  const saveState = document.getElementById("assistant-save-state");
  const clearButton = document.getElementById("assistant-clear");
  const downloadButton = document.getElementById("assistant-download");
  const storageKey = "hongsik.learningAssistant.v1";

  function todayValue() {
    const date = new Date();
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 10);
  }

  function valueOf(name) {
    return fields[name] ? fields[name].value.trim() : "";
  }

  function collect() {
    return {
      topic: valueOf("topic") || "오늘의 AI 리더 캠프 학습 기록",
      summary: valueOf("summary"),
      problem: valueOf("problem"),
      solution: valueOf("solution"),
      reflection: valueOf("reflection"),
      style: valueOf("style") || "빠른 실행형 실험가",
      date: valueOf("date") || todayValue(),
      next: valueOf("next"),
    };
  }

  function sentence(text, fallback) {
    return text || fallback;
  }

  function keywordTokens(topic) {
    return topic
      .split(/[,#\s/]+/)
      .map((token) => token.trim())
      .filter((token) => token.length >= 2)
      .slice(0, 5);
  }

  function recommendTags(data) {
    const baseTags = ["AI리더캠프", "학습기록"];
    const lower = `${data.topic} ${data.summary} ${data.problem} ${data.solution}`.toLowerCase();
    const rules = [
      ["mvp", "MVP"],
      ["prd", "PRD"],
      ["프롬프트", "프롬프트"],
      ["chatgpt", "ChatGPT"],
      ["경쟁", "경쟁분석"],
      ["마케팅", "마케팅"],
      ["메타인지", "메타인지"],
      ["습관", "습관"],
      ["자동화", "자동화"],
    ];
    const tags = new Set(baseTags);

    keywordTokens(data.topic).forEach((token) => tags.add(token.replace(/^#/, "")));
    rules.forEach(([needle, tag]) => {
      if (lower.includes(needle)) {
        tags.add(tag);
      }
    });

    return Array.from(tags).slice(0, 8);
  }

  function buildDraft(data) {
    return [
      `# ${data.topic}`,
      "",
      "## 오늘의 학습 키워드",
      "",
      keywordTokens(data.topic).length
        ? keywordTokens(data.topic).map((token) => `- ${token}`).join("\n")
        : "- 오늘 배운 핵심 개념을 정리한다.",
      "",
      "## 내가 이해한 개념",
      "",
      sentence(
        data.summary,
        "오늘 배운 내용을 아직 한 문장으로 완전히 정리하긴 어렵지만, 핵심은 내가 직접 실행 가능한 형태로 이해하는 것이다."
      ),
      "",
      "## 실습하며 겪은 문제",
      "",
      sentence(
        data.problem,
        "아직 큰 문제를 만나지는 않았지만, 개념을 실제 결과물로 옮기는 과정에서 더 확인해야 할 부분이 있다."
      ),
      "",
      "## 해결 과정",
      "",
      sentence(
        data.solution,
        "먼저 작게 시도하고, 결과를 확인한 뒤 부족한 부분을 다시 수정하는 방식으로 접근했다."
      ),
      "",
      "## 오늘의 회고",
      "",
      `${data.style} 관점에서 보면, 오늘의 핵심은 완벽하게 이해한 뒤 움직이는 것이 아니라 작게라도 결과물을 만들어보는 것이었다.`,
      "",
      sentence(
        data.reflection,
        "기록을 남기면서 내가 무엇을 알고 있고 무엇을 더 배워야 하는지 조금 더 선명해졌다."
      ),
      "",
      "## 다음 액션",
      "",
      sentence(data.next, "내일은 오늘 정리한 내용을 바탕으로 작은 실습 결과물을 하나 더 만들어본다."),
    ].join("\n");
  }

  function buildPrompt(data) {
    return [
      "너는 AI 리더 캠프 학습자의 블로그 작성 비서야.",
      "내 목표는 오늘 배운 내용을 내 언어로 정리하고, 실제 실행 기록으로 남기는 것이야.",
      "",
      `내 업무 스타일은 '${data.style}'이야.`,
      "너무 일반적인 강의 요약처럼 쓰지 말고, 내가 직접 배우고 시행착오를 겪은 사람처럼 자연스럽게 다듬어줘.",
      "",
      "아래 초안을 바탕으로 블로그 글을 보강해줘.",
      "",
      buildDraft(data),
      "",
      "보강 기준:",
      "- 제목은 너무 과장하지 말 것",
      "- 오늘 배운 개념을 쉽게 설명할 것",
      "- 문제와 해결 과정이 구체적으로 보이게 할 것",
      "- 마지막에는 다음 액션을 1개만 제안할 것",
      "- 문체는 담백하고 솔직하게 유지할 것",
    ].join("\n");
  }

  function buildPlan(data, tags) {
    const date = data.date.replaceAll("-", ".");

    return [
      `<table><tbody>`,
      `<tr><th>발행 카테고리</th><td>학습 AI트랙</td></tr>`,
      `<tr><th>추천 발행일</th><td>${date}</td></tr>`,
      `<tr><th>핵심 사용자</th><td>AI 리더 캠프 학습 기록을 꾸준히 남기려는 학습자</td></tr>`,
      `<tr><th>검증 질문</th><td>이 글이 오늘 배운 내용을 내 언어로 설명하고 있는가?</td></tr>`,
      `</tbody></table>`,
      `<ol class="assistant-checklist">`,
      `<li>초안에서 내 경험이 들어간 문장을 1개 이상 추가한다.</li>`,
      `<li>태그는 ${tags.slice(0, 5).join(", ")} 중심으로 정리한다.</li>`,
      `<li>발행 전 제목, 카테고리, 태그, 다음 액션을 확인한다.</li>`,
      `<li>발행 후 학습맵에서 글이 연결되는지 확인한다.</li>`,
      `</ol>`,
    ].join("");
  }

  function render() {
    const data = collect();
    const tags = recommendTags(data);
    const draft = buildDraft(data);

    draftOutput.value = draft;
    promptOutput.value = buildPrompt(data);
    tagsOutput.innerHTML = tags.map((tag) => `<span>${tag}</span>`).join("");
    planOutput.innerHTML = buildPlan(data, tags);
  }

  function save() {
    const payload = {};
    Object.keys(fields).forEach((name) => {
      payload[name] = fields[name].value;
    });
    localStorage.setItem(storageKey, JSON.stringify(payload));

    if (saveState) {
      saveState.textContent = "입력 내용이 이 브라우저에 자동 저장되었습니다.";
    }
  }

  function load() {
    const raw = localStorage.getItem(storageKey);

    if (!fields.date.value) {
      fields.date.value = todayValue();
    }

    if (!raw) {
      render();
      return;
    }

    try {
      const payload = JSON.parse(raw);
      Object.keys(fields).forEach((name) => {
        if (typeof payload[name] === "string") {
          fields[name].value = payload[name];
        }
      });
    } catch (error) {
      localStorage.removeItem(storageKey);
    }

    render();
  }

  function copyText(targetId) {
    const target = document.getElementById(targetId);

    if (!target) {
      return;
    }

    target.select();
    target.setSelectionRange(0, target.value.length);

    if (navigator.clipboard) {
      navigator.clipboard.writeText(target.value);
      return;
    }

    document.execCommand("copy");
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    render();
    save();
  });

  Object.values(fields).forEach((field) => {
    field.addEventListener("input", () => {
      render();
      save();
    });
  });

  document.querySelectorAll("[data-assistant-tab]").forEach((button) => {
    button.addEventListener("click", () => {
      const tabName = button.dataset.assistantTab;

      document.querySelectorAll("[data-assistant-tab]").forEach((tab) => {
        tab.classList.toggle("is-active", tab === button);
      });
      document.querySelectorAll("[data-assistant-panel]").forEach((panel) => {
        panel.classList.toggle("is-active", panel.dataset.assistantPanel === tabName);
      });
    });
  });

  document.querySelectorAll("[data-copy-target]").forEach((button) => {
    button.addEventListener("click", () => {
      copyText(button.dataset.copyTarget);
      button.textContent = "복사됨";
      window.setTimeout(() => {
        button.textContent = "복사";
      }, 1200);
    });
  });

  clearButton.addEventListener("click", () => {
    Object.keys(fields).forEach((name) => {
      fields[name].value = name === "date" ? todayValue() : "";
    });
    localStorage.removeItem(storageKey);
    render();
    if (saveState) {
      saveState.textContent = "입력을 비웠습니다.";
    }
  });

  downloadButton.addEventListener("click", () => {
    const data = collect();
    const markdown = `${draftOutput.value}\n\n---\n\n추천 태그: ${recommendTags(data).join(", ")}\n발행 카테고리: 학습 AI트랙\n희망 발행일: ${data.date}\n`;
    const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
    const link = document.createElement("a");
    const filename = `${data.date}-${data.topic.replace(/[\\/:*?"<>|]+/g, "").replace(/\s+/g, "-")}.md`;

    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
  });

  load();
})();
