/**
 * QA Test Checklist Renderer
 * 테스트 데이터를 읽어 UI에 렌더링하고 상태를 localStorage에 저장합니다.
 */

const STORAGE_KEY = "qa-test-results";

class TestRenderer {
  constructor(containerId) {
    this.container = document.getElementById(containerId);
    this.testModules = [];
    this.currentFeature = null;
    this.statusFilter = "all";
    this.results = this.loadResults();
  }

  /** localStorage에서 저장된 결과 로드 */
  loadResults() {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      return data ? JSON.parse(data) : {};
    } catch {
      return {};
    }
  }

  /** localStorage에 결과 저장 */
  saveResults() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(this.results));
  }

  /** 테스트 모듈 등록 */
  registerModule(module) {
    this.testModules.push(module);
  }

  /** 케이스 상태 업데이트 */
  updateStatus(caseId, status) {
    if (status === "pending") {
      delete this.results[caseId];
    } else {
      this.results[caseId] = { status, updatedAt: new Date().toISOString() };
    }
    this.saveResults();
    this.render();
  }

  /** 현재 기능의 결과 초기화 */
  resetCurrentFeature() {
    if (!this.currentFeature) return;
    const module = this.testModules.find(m => m.feature === this.currentFeature);
    if (module) {
      module.cases.forEach(c => delete this.results[c.id]);
      this.saveResults();
      this.render();
    }
  }

  /** 전체 결과 초기화 */
  resetAll() {
    if (confirm("모든 테스트 결과를 초기화하시겠습니까?")) {
      this.results = {};
      this.saveResults();
      this.render();
    }
  }

  /** 케이스의 현재 상태 조회 */
  getStatus(caseId) {
    return this.results[caseId]?.status || "pending";
  }

  /** 통계 계산 */
  getStats(cases) {
    const stats = { total: cases.length, pass: 0, fail: 0, skip: 0, pending: 0 };
    cases.forEach(c => {
      const status = this.getStatus(c.id);
      stats[status] = (stats[status] || 0) + 1;
    });
    return stats;
  }

  /** 전체 렌더링 */
  render() {
    const module = this.testModules.find(m => m.feature === this.currentFeature);
    const allCases = this.testModules.flatMap(m => m.cases);
    const globalStats = this.getStats(allCases);

    let html = "";

    // 헤더
    html += `
      <div class="app-header">
        <h1>🧪 QA Test Checklist</h1>
        <div class="stats">
          <span class="stat-badge pass">✅ ${globalStats.pass}</span>
          <span class="stat-badge fail">❌ ${globalStats.fail}</span>
          <span class="stat-badge skip">⏭️ ${globalStats.skip}</span>
          <span class="stat-badge pending">⬜ ${globalStats.pending}</span>
        </div>
      </div>
    `;

    // 컨트롤
    html += `
      <div class="controls">
        <select id="feature-select">
          <option value="">전체 기능 (${allCases.length}개 케이스)</option>
          ${this.testModules.map(m => {
            const s = this.getStats(m.cases);
            return `<option value="${m.feature}" ${m.feature === this.currentFeature ? "selected" : ""}>
              ${m.feature} — ${m.screen} (${s.pass}/${s.total})
            </option>`;
          }).join("")}
        </select>
        <select id="status-filter">
          <option value="all" ${this.statusFilter === "all" ? "selected" : ""}>상태: 전체</option>
          <option value="pending" ${this.statusFilter === "pending" ? "selected" : ""}>⬜ 미진행</option>
          <option value="pass" ${this.statusFilter === "pass" ? "selected" : ""}>✅ Pass</option>
          <option value="fail" ${this.statusFilter === "fail" ? "selected" : ""}>❌ Fail</option>
          <option value="skip" ${this.statusFilter === "skip" ? "selected" : ""}>⏭️ Skip</option>
        </select>
        <button class="btn-reset" onclick="renderer.resetCurrentFeature()">현재 기능 초기화</button>
        <button class="btn-reset" onclick="renderer.resetAll()">전체 초기화</button>
      </div>
    `;

    // 케이스 목록
    const displayCases = module ? module.cases : allCases;
    const filteredCases = this.statusFilter === "all"
      ? displayCases
      : displayCases.filter(c => this.getStatus(c.id) === this.statusFilter);

    if (filteredCases.length === 0) {
      html += `<div class="empty-state"><p>표시할 테스트 케이스가 없습니다.</p></div>`;
    } else {
      html += `<div class="case-list">`;
      filteredCases.forEach(c => {
        const status = this.getStatus(c.id);
        html += this.renderCase(c, status);
      });
      html += `</div>`;
    }

    this.container.innerHTML = html;
    this.bindEvents();
  }

  /** 단일 케이스 렌더링 */
  renderCase(c, status) {
    const stepsHtml = c.steps && c.steps.length > 0
      ? `<ol class="case-steps">${c.steps.map(s => `<li>${s}</li>`).join("")}</ol>`
      : "";

    return `
      <div class="case-card status-${status}" data-id="${c.id}">
        <div class="case-header">
          <span class="case-id">${c.id}</span>
        </div>
        <div class="case-title">${c.title}</div>
        ${c.precondition ? `<div class="case-detail"><strong>전제:</strong> ${c.precondition}</div>` : ""}
        ${stepsHtml}
        ${c.expected ? `<div class="case-expected">✅ 기대: ${c.expected}</div>` : ""}
        <div class="status-buttons">
          <button class="status-btn pass ${status === "pass" ? "active" : ""}" data-id="${c.id}" data-status="pass">Pass</button>
          <button class="status-btn fail ${status === "fail" ? "active" : ""}" data-id="${c.id}" data-status="fail">Fail</button>
          <button class="status-btn skip ${status === "skip" ? "active" : ""}" data-id="${c.id}" data-status="skip">Skip</button>
        </div>
      </div>
    `;
  }

  /** 이벤트 바인딩 */
  bindEvents() {
    // 기능 선택
    const featureSelect = document.getElementById("feature-select");
    if (featureSelect) {
      featureSelect.addEventListener("change", (e) => {
        this.currentFeature = e.target.value || null;
        this.render();
      });
    }

    // 상태 필터
    const statusFilter = document.getElementById("status-filter");
    if (statusFilter) {
      statusFilter.addEventListener("change", (e) => {
        this.statusFilter = e.target.value;
        this.render();
      });
    }

    // 상태 버튼
    document.querySelectorAll(".status-btn").forEach(btn => {
      btn.addEventListener("click", (e) => {
        const id = e.target.dataset.id;
        const newStatus = e.target.dataset.status;
        const currentStatus = this.getStatus(id);
        // 같은 버튼 다시 클릭 시 pending으로 초기화
        this.updateStatus(id, currentStatus === newStatus ? "pending" : newStatus);
      });
    });
  }
}

export default TestRenderer;
