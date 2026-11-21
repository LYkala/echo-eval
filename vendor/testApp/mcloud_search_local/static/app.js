(() => {
  const $ = (id) => document.getElementById(id);
  const API =
    (typeof window !== "undefined" && window.NETDISK_API_BASE) || "/api/netdisk";
  const api = (path) => API + path;
  const AGENT = "http://127.0.0.1:18766";
  const AGENT_ZIP = "/static/netdisk/NetdiskPrepareAgent.zip";
  const AGENT_BAT = "/static/netdisk/start_prepare_agent.bat";
  // 页面可选的额外执行状态（如移动网盘「无结果」）
  const EXTRA_STATUSES =
    (typeof window !== "undefined" && window.EXTRA_STATUSES) || [];
  const LOCAL_USB = !!(typeof window !== "undefined" && window.LOCAL_USB);

  let offD = 0;
  let offR = 0;
  let pageSize = 30;
  /** @type {Set<string>} */
  const selectedIds = new Set();
  let lastDatasetRows = [];

  function currentPageSize() {
    return pageSize;
  }

  function setPageSize(n, which) {
    const v = Number(n) || 50;
    pageSize = [30, 50, 100].includes(v) ? v : 30;
    if ($("pageSizeD") && which !== "R") $("pageSizeD").value = String(pageSize);
    if ($("pageSizeR") && which !== "D") $("pageSizeR").value = String(pageSize);
  }

  function pageLabel(offset, total) {
    const size = currentPageSize();
    if (!total) return "第 0/0 页";
    const page = Math.floor(offset / size) + 1;
    const pages = Math.max(1, Math.ceil(total / size));
    return `第 ${page}/${pages} 页`;
  }

  const PREFER = [
    "用例编号",
    "执行状态",
    "原始输入",
    "UserId",
    "预期图片数量",
    "预期fid",
    "预期function",
    "高权重预期fid",
    "低权重fid",
    "非预期fid",
    "搜索时延(ms)",
    "实际搜索数量",
    "实际fid",
    "实际图片名称",
  ];

  function caseStatus(row) {
    return String((row && row["执行状态"]) || "").trim();
  }

  function isDoneRow(row) {
    return caseStatus(row) === "完成";
  }

  function esc(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function isLongCol(h) {
    const s = String(h || "");
    return /fid/i.test(s) || s.includes("图片名称");
  }

  function previewText(val, col) {
    const s = val == null ? "" : String(val);
    if (!s) return "";
    const limit = isLongCol(col) ? 48 : 40;
    if (s.length <= limit) return s;
    const extra = isLongCol(col) ? `…(${s.split(",").filter(Boolean).length})` : "…";
    return s.slice(0, limit) + extra;
  }

  function openCellModal(title, text) {
    $("modalTitle").textContent = title;
    $("modalBody").textContent = text == null ? "" : String(text);
    $("cellModal").classList.add("open");
  }

  function closeCellModal() {
    $("cellModal").classList.remove("open");
  }

  function syncSelectedHint() {
    const n = selectedIds.size;
    $("selectedHint").textContent =
      n > 0
        ? `已选 ${n} 条：${[...selectedIds].slice(0, 8).join(", ")}${n > 8 ? "…" : ""}`
        : "已选 0 条用例（在数据集预览中勾选）";
    // 勾选变化时同步编号框；取消全选 / 清空勾选时一并清空
    if ($("caseIds")) {
      $("caseIds").value = n > 0 ? [...selectedIds].join(",") : "";
    }
  }

  function renderResultsTable(table, headers, rows) {
    const cols = PREFER.filter((h) => headers.includes(h)).concat(
      headers.filter((h) => !PREFER.includes(h))
    );
    let html = "<thead><tr>" + cols.map((h) => `<th>${esc(h)}</th>`).join("") + "</tr></thead><tbody>";
    for (const r of rows) {
      html += "<tr>";
      for (const h of cols) {
        const full = r[h] == null ? "" : String(r[h]);
        html += `<td class="cell-click" data-col="${esc(h)}" data-full="${esc(full)}" title="点击查看全文">${esc(
          previewText(full, h)
        )}</td>`;
      }
      html += "</tr>";
    }
    html += "</tbody>";
    table.innerHTML = html || "<tbody><tr><td>无数据</td></tr></tbody>";
    table.querySelectorAll("td.cell-click").forEach((td) => {
      td.addEventListener("click", () => openCellModal(td.dataset.col || "内容", td.dataset.full || ""));
    });
  }

  function renderDatasetTable(table, headers, rows) {
    lastDatasetRows = rows;
    const cols = PREFER.filter((h) => headers.includes(h)).concat(
      headers.filter((h) => !PREFER.includes(h))
    );
    let html = "<thead><tr>";
    html += `<th class="col-check"><input type="checkbox" id="chkAllPage" title="本页全选" /></th>`;
    for (const h of cols) {
      html += `<th>${esc(h)}</th>`;
    }
    html += "</tr></thead><tbody>";

    for (const r of rows) {
      const cid = String(r["用例编号"] || "");
      const done = isDoneRow(r);
      const st = caseStatus(r);
      const checked = selectedIds.has(cid) ? " checked" : "";
      html += `<tr class="${done ? "row-done" : st === "中断" ? "row-partial" : ""}">`;
      html += `<td class="col-check"><input type="checkbox" class="chk-case" data-id="${esc(
        cid
      )}"${checked} title="${done ? "已完成；勾选后开始=强制重跑" : ""}" /></td>`;
      for (const h of cols) {
        let full = r[h] == null ? "" : String(r[h]);
        let cell = esc(previewText(full, h));
        if (h === "执行状态") {
          const opts = [
            ["", "未跑"],
            ["完成", "完成"],
            ["中断", "中断"],
            ["失败", "失败"],
            ...EXTRA_STATUSES.filter((s) => s && s !== "未跑").map((s) => [s, s]),
          ];
          cell = `<select class="status-edit" data-id="${esc(cid)}" title="编辑执行状态">`;
          for (const [val, label] of opts) {
            const sel = st === val || (!st && val === "") ? " selected" : "";
            cell += `<option value="${esc(val)}"${sel}>${esc(label)}</option>`;
          }
          cell += `</select>`;
        }
        const clickable = h === "执行状态" ? "" : " cell-click";
        const title = h === "执行状态" ? "" : ' title="点击查看全文"';
        html += `<td class="${clickable.trim()}" data-col="${esc(h)}" data-full="${esc(full)}"${title}>${cell}</td>`;
      }
      html += "</tr>";
    }
    html += "</tbody>";
    table.innerHTML = html || "<tbody><tr><td>无数据</td></tr></tbody>";

    const pageIds = rows.map((r) => String(r["用例编号"] || "")).filter(Boolean);
    const chkAll = $("chkAllPage");
    if (chkAll) {
      chkAll.checked = pageIds.length > 0 && pageIds.every((id) => selectedIds.has(id));
      chkAll.indeterminate =
        pageIds.some((id) => selectedIds.has(id)) && !chkAll.checked;
      chkAll.onchange = () => {
        if (chkAll.checked) pageIds.forEach((id) => selectedIds.add(id));
        else pageIds.forEach((id) => selectedIds.delete(id));
        renderDatasetTable(table, headers, rows);
        syncSelectedHint();
      };
    }

    table.querySelectorAll(".chk-case").forEach((chk) => {
      chk.addEventListener("click", (e) => e.stopPropagation());
      chk.addEventListener("change", () => {
        const id = chk.dataset.id;
        if (!id) return;
        if (chk.checked) selectedIds.add(id);
        else selectedIds.delete(id);
        syncSelectedHint();
        if (chkAll) {
          chkAll.checked = pageIds.length > 0 && pageIds.every((id) => selectedIds.has(id));
          chkAll.indeterminate =
            pageIds.some((id) => selectedIds.has(id)) && !chkAll.checked;
        }
      });
    });

    table.querySelectorAll("select.status-edit").forEach((sel) => {
      sel.addEventListener("click", (e) => e.stopPropagation());
      sel.addEventListener("change", async () => {
        const id = sel.dataset.id;
        const status = sel.value;
        sel.disabled = true;
        try {
          const res = await fetch(api("/case/status"), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ case_id: id, status }),
          }).then((r) => r.json());
          if (!res.ok) {
            alert(res.error || "状态保存失败");
            return;
          }
          // update local row + restyle
          const row = lastDatasetRows.find((r) => String(r["用例编号"] || "") === id);
          if (row) row["执行状态"] = status || null;
          renderDatasetTable(table, headers, lastDatasetRows);
        } catch (err) {
          alert("状态保存异常: " + (err && err.message ? err.message : err));
          sel.disabled = false;
        }
      });
    });

    table.querySelectorAll("td.cell-click").forEach((td) => {
      td.addEventListener("click", () => openCellModal(td.dataset.col || "内容", td.dataset.full || ""));
    });
  }

  async function refreshDevices() {
    const res = await fetch(api("/devices")).then((r) => r.json());
    const sel = $("serial");
    sel.innerHTML = "";
    const list = (res.devices || []).filter((d) => d.state === "device");
    const preferAgent = ($("agentSelect") && $("agentSelect").value) || localStorage.getItem("netdisk_agent_id") || "";
    const mine = preferAgent
      ? list.filter((d) => d.agent_id === preferAgent)
      : list;
    const show = mine.length ? mine : list;
    if (!show.length) {
      sel.innerHTML = '<option value="">无在线设备</option>';
      $("devHint").textContent =
        res.error ||
        (LOCAL_USB
          ? "未检测到 USB 设备。请打开手机调试，确认 adb devices 可见后点「刷新设备」"
          : "未检测到 device。请先选自己的电脑并点「一键准备」");
      return;
    }
    for (const d of show) {
      const opt = document.createElement("option");
      opt.value = d.serial;
      const name = d.label || d.marketname || d.model || "未知机型";
      const ch = d.channel_label || d.channel || "";
      opt.textContent = ch ? `${name}  (${d.serial}) · ${ch}` : `${name}  (${d.serial})`;
      sel.appendChild(opt);
    }
    $("devHint").textContent = show
      .map(
        (d) =>
          `${d.label || d.model || "设备"} · ${d.state}` +
          (d.channel_label ? ` · ${d.channel_label}` : "")
      )
      .join("；");
  }

  async function refreshAgents() {
    if (LOCAL_USB) return;
    const sel = $("agentSelect");
    if (!sel) return;
    let rows = [];
    try {
      const res = await fetch(api("/prepare/agents"), { cache: "no-store" }).then((r) =>
        r.json()
      );
      rows = res.agents || [];
    } catch (_) {
      rows = [];
    }
    const prev = sel.value || localStorage.getItem("netdisk_agent_id") || "";
    sel.innerHTML = "";
    if (!rows.length) {
      sel.innerHTML = '<option value="">无在线助手 — 请先运行 StartAgent.bat</option>';
      return;
    }
    for (const a of rows) {
      const opt = document.createElement("option");
      opt.value = a.agent_id;
      const n = a.device_count || 0;
      opt.textContent = `${a.hostname || a.agent_id} · 桥:${a.bridge_port || "?"} · 设备${n}`;
      sel.appendChild(opt);
    }
    if (prev && [...sel.options].some((o) => o.value === prev)) sel.value = prev;
    localStorage.setItem("netdisk_agent_id", sel.value);
  }

  function setPrepareUI({ pct, message, phase, hintHtml }) {
    const box = $("prepareBox");
    const bar = $("prepareBar");
    const pctEl = $("preparePct");
    const msgEl = $("prepareMsg");
    const hint = $("prepareHint");
    if (pctEl) pctEl.textContent = `${Math.max(0, Math.min(100, pct | 0))}%`;
    if (bar) bar.style.width = `${Math.max(0, Math.min(100, pct | 0))}%`;
    if (msgEl) msgEl.textContent = message || "";
    if (hint && hintHtml !== undefined) hint.innerHTML = hintHtml;
    if (box) {
      box.classList.toggle("is-run", phase === "run");
      box.classList.toggle("is-ok", phase === "ok");
      box.classList.toggle("is-err", phase === "err");
    }
  }

  async function pollPrepareJob(jobId, agentId, maxMs) {
    const deadline = Date.now() + (maxMs || 180000);
    while (Date.now() < deadline) {
      const u =
        api("/prepare/status") +
        `?job_id=${encodeURIComponent(jobId || "")}&agent_id=${encodeURIComponent(
          agentId || ""
        )}`;
      const st = await fetch(u, { cache: "no-store" }).then((r) => r.json());
      const phase =
        st.status === "done" || st.done_ok === true
          ? "ok"
          : st.status === "error" || st.done_ok === false
            ? "err"
            : "run";
      let hint = "";
      if (!st.agent_online && (st.status === "queued" || st.status === "idle")) {
        hint = `所选电脑助手未上线：请在该电脑运行 <b>StartAgent.bat</b>。`;
      } else if (st.error) {
        hint = esc(st.error);
      } else if (st.status === "done") {
        hint = "已绑定到你选的电脑，可直接选设备开跑。";
      }
      setPrepareUI({
        pct: st.pct || 0,
        message: st.message || "…",
        phase,
        hintHtml: hint,
      });
      if (st.status === "done" || st.done_ok === true) return st;
      if (st.status === "error" || st.done_ok === false) {
        throw new Error(st.error || st.message || "准备失败");
      }
      await new Promise((r) => setTimeout(r, 900));
    }
    throw new Error("准备超时：请确认该电脑上 StartAgent.bat 仍在运行后重试");
  }

  async function oneClickPrepare() {
    const btn = $("btnPrepare");
    if (btn) btn.disabled = true;
    await refreshAgents();
    const agentId = ($("agentSelect") && $("agentSelect").value) || "";
    if (!agentId) {
      setPrepareUI({
        pct: 0,
        message: "请先运行本机助手",
        phase: "err",
        hintHtml: `正在下载 <a href="${AGENT_ZIP}">NetdiskPrepareAgent.zip</a>：解压后双击 <b>StartAgent.bat</b>。若弹出「智能应用控制已阻止」：右键 exe → 属性 → 勾选「解除锁定」→ 确定，再运行；或暂时关闭智能应用控制。`,
      });
      try {
        const a = document.createElement("a");
        a.href = AGENT_ZIP;
        a.download = "NetdiskPrepareAgent.zip";
        document.body.appendChild(a);
        a.click();
        a.remove();
      } catch (_) {}
      if (btn) btn.disabled = false;
      return;
    }
    localStorage.setItem("netdisk_agent_id", agentId);
    setPrepareUI({
      pct: 1,
      message: "向 9526 提交准备任务（仅你的电脑）…",
      phase: "run",
      hintHtml: "",
    });
    try {
      const start = await fetch(api("/prepare/request"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ agent_id: agentId }),
        cache: "no-store",
      }).then((r) => r.json());
      if (!start.ok) throw new Error(start.error || "提交失败");

      const st = await pollPrepareJob(start.job_id, agentId, 180000);
      setPrepareUI({
        pct: 100,
        message: "准备完成",
        phase: "ok",
        hintHtml: (st.serials || []).length
          ? `本机设备：${(st.serials || []).join(", ")}`
          : "桥已就绪，正在刷新设备列表…",
      });
      await refreshAgents();
      await refreshDevices();
    } catch (e) {
      setPrepareUI({
        pct: 0,
        message: "准备失败",
        phase: "err",
        hintHtml: esc((e && e.message) || String(e)),
      });
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  function countFilterParams() {
    const minV = ($("countMin") && $("countMin").value.trim()) || "";
    const maxV = ($("countMax") && $("countMax").value.trim()) || "";
    return { minV, maxV };
  }

  // 执行状态筛选：netdisk 为原生单选 select；mcloud 为复选框多选下拉（容器 id 同为 statusFilter）
  function statusFilterValue() {
    const el = $("statusFilter");
    if (!el) return "";
    if (el.tagName === "SELECT") return el.value || "";
    return [...el.querySelectorAll('input[type="checkbox"]:checked')]
      .map((c) => c.value)
      .filter(Boolean)
      .join(",");
  }

  function syncStatusFilterUI() {
    const el = $("statusFilter");
    if (!el || el.tagName === "SELECT") return;
    const btn = el.querySelector(".ms-btn");
    if (!btn) return;
    const vals = [...el.querySelectorAll('input[type="checkbox"]:checked')].map((c) => c.value);
    btn.textContent = vals.length ? vals.join("、") : "全部";
    btn.classList.toggle("has-sel", vals.length > 0);
  }

  function setupStatusMenu() {
    const el = $("statusFilter");
    if (!el || el.tagName === "SELECT") return;
    const btn = el.querySelector(".ms-btn");
    if (btn) {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const open = el.classList.toggle("open");
        btn.setAttribute("aria-expanded", open ? "true" : "false");
      });
    }
    document.addEventListener("click", (e) => {
      if (el.classList.contains("open") && !el.contains(e.target)) {
        el.classList.remove("open");
        if (btn) btn.setAttribute("aria-expanded", "false");
      }
    });
    syncStatusFilterUI();
  }

  function fillDatasetQuery(u, which, q) {
    if (q) u.searchParams.set("q", q);
    if (which !== "cases") return;
    const { minV, maxV } = countFilterParams();
    if (minV !== "") u.searchParams.set("count_min", minV);
    if (maxV !== "") u.searchParams.set("count_max", maxV);
    const st = statusFilterValue();
    if (st) u.searchParams.set("status", st);
  }

  function reloadCases() {
    return loadDataset(
      "cases",
      offD,
      ($("qDataset") && $("qDataset").value.trim()) || "",
      $("tblDataset"),
      $("datasetMeta")
    );
  }

  async function loadDataset(which, offset, q, table, metaEl) {
    try {
      const u = new URL(api("/dataset"), location.origin);
      u.searchParams.set("which", which);
      u.searchParams.set("offset", String(offset));
      u.searchParams.set("limit", String(pageSize));
      fillDatasetQuery(u, which, q);
      const data = await fetch(u).then((r) => r.json());
      if (!data.ok) {
        metaEl.textContent = data.error || "加载失败";
        table.innerHTML = "<tbody><tr><td>加载失败</td></tr></tbody>";
        return data;
      }
      if (which === "cases") {
        renderDatasetTable(table, data.headers || [], data.rows || []);
        const { minV, maxV } = countFilterParams();
        let filterInfo = "";
        if (minV !== "" || maxV !== "") {
          filterInfo = ` · 预期数量 ${minV || "不限"}–${maxV || "不限"}`;
        }
        const st = statusFilterValue();
        if (st) filterInfo += ` · 执行状态 ${st.split(",").join("、")}`;
        metaEl.textContent = `${data.path || ""} · ${pageLabel(offset, data.total || 0)} · ${
          offset + 1
        }-${Math.min(offset + pageSize, data.total || 0)} / ${data.total || 0}${filterInfo}`;
      } else {
        renderResultsTable(table, data.headers || [], data.rows || []);
        const qInfo = q ? ` · 搜索「${q}」` : "";
        metaEl.textContent = `${data.path || ""} · ${pageLabel(offset, data.total || 0)} · ${
          offset + 1
        }-${Math.min(offset + pageSize, data.total || 0)} / ${data.total || 0}${qInfo}`;
      }
      return data;
    } catch (err) {
      metaEl.textContent = "加载异常: " + (err && err.message ? err.message : err);
      table.innerHTML = "<tbody><tr><td>加载异常，请点筛选/刷新</td></tr></tbody>";
      return { ok: false, error: String(err) };
    }
  }

  async function pollStatus() {
    const st = await fetch(api("/run/status")).then((r) => r.json());
    const badge = $("runBadge");
    if (st.running) {
      badge.textContent = "运行中";
      badge.className = "badge run";
    } else if (st.error) {
      badge.textContent = "出错";
      badge.className = "badge err";
    } else {
      badge.textContent = "空闲";
      badge.className = "badge idle";
    }
    $("progress").textContent =
      st.running || st.total
        ? `进度 ${st.progress || 0}/${st.total || 0}` +
          (st.current_case ? ` · ${st.current_case.id} ${st.current_case.query || ""}` : "") +
          (st.result_xlsx ? ` · ${st.result_xlsx}` : "")
        : "进度 —";
    $("logs").textContent = (st.logs || []).join("\n");
    $("logs").scrollTop = $("logs").scrollHeight;

    const cases = st.cases || [];
    let html =
      "<thead><tr><th>用例</th><th>输入</th><th>状态</th><th>OK</th><th>时延ms</th><th>张数</th><th>缺映射</th><th>实际图片名称</th><th>实际fid预览</th><th>错误</th></tr></thead><tbody>";
    for (const c of cases.slice().reverse()) {
      const n = (c.order || []).length;
      const miss = (c.missing || []).length;
      const fid = c.actual_fid || "";
      const names = c.actual_names || (c.order || []).map((x) => x.name).join(",");
      const lat = c.search_latency_ms == null ? "" : String(c.search_latency_ms);
      const stLabel = c.status || "";
      html += `<tr>
        <td>${esc(c.id)}</td>
        <td>${esc(c.query)}</td>
        <td>${esc(stLabel)}</td>
        <td>${c.ok ? "是" : "否"}</td>
        <td>${esc(lat)}</td>
        <td>${n}</td>
        <td>${miss}</td>
        <td title="${esc(names)}">${esc(names.slice(0, 60))}${names.length > 60 ? "…" : ""}</td>
        <td title="${esc(fid)}">${esc(fid.slice(0, 80))}${fid.length > 80 ? "…" : ""}</td>
        <td>${esc(c.error || "")}</td>
      </tr>`;
    }
    html += "</tbody>";
    $("tblCases").innerHTML = html;
  }

  function on(id, ev, fn) {
    const el = $(id);
    if (el) el.addEventListener(ev, fn);
  }
  function click(id, fn) {
    const el = $(id);
    if (el) el.onclick = fn;
  }

  function openShotModal() {
    const mask = $("shotModal");
    if (mask) mask.classList.add("open");
  }

  function closeShotModal() {
    const mask = $("shotModal");
    if (mask) mask.classList.remove("open");
  }

  async function captureScreen() {
    const serial = ($("serial") && $("serial").value) || "";
    const img = $("screenImg");
    const msg = $("shotStatus");
    const btn = $("btnShot");
    const again = $("btnShotAgain");
    openShotModal();
    if (img) {
      img.hidden = true;
      img.removeAttribute("src");
    }
    if (!serial) {
      if (msg) msg.textContent = "请先选择手机设备";
      return;
    }
    if (btn) {
      btn.disabled = true;
      btn.textContent = "截取中…";
    }
    if (again) again.disabled = true;
    if (msg) msg.textContent = "正在截取当前手机画面…";
    try {
      const url =
        api("/screenshot") +
        "?serial=" +
        encodeURIComponent(serial) +
        "&t=" +
        Date.now();
      const res = await fetch(url, { cache: "no-store" });
      const ct = (res.headers.get("content-type") || "").toLowerCase();
      if (!res.ok || ct.indexOf("image") < 0) {
        let err = "截图失败";
        try {
          const j = await res.json();
          err = j.error || err;
        } catch (_) {
          try {
            err = (await res.text()).slice(0, 240) || err;
          } catch (_) {}
        }
        if (msg) msg.textContent = err;
        return;
      }
      const blob = await res.blob();
      if (blob.size < 100) {
        if (msg) msg.textContent = "截图数据为空，桥可能已断开";
        return;
      }
      if (img) {
        const prev = img.dataset.objurl || "";
        if (prev) URL.revokeObjectURL(prev);
        const obj = URL.createObjectURL(blob);
        img.dataset.objurl = obj;
        img.src = obj;
        img.hidden = false;
      }
      if (msg) msg.textContent = "截取于 " + new Date().toLocaleTimeString();
    } catch (err) {
      if (img) {
        img.hidden = true;
        img.removeAttribute("src");
      }
      if (msg) msg.textContent = "截图异常: " + (err && err.message ? err.message : err);
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "截取屏幕";
      }
      if (again) again.disabled = false;
    }
  }

  document.querySelectorAll(".tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".pane").forEach((p) => p.classList.remove("active"));
      btn.classList.add("active");
      const pane = $("pane-" + btn.dataset.tab);
      if (pane) pane.classList.add("active");
    });
  });

  // load data first so table is not empty if later handlers fail
  syncSelectedHint();
  (LOCAL_USB ? Promise.resolve() : refreshAgents())
    .then(() => refreshDevices())
    .catch(() => refreshDevices().catch(() => {}));
  loadDataset("cases", 0, "", $("tblDataset"), $("datasetMeta"));
  loadDataset("results", 0, "", $("tblResults"), $("resultsMeta"));
  pollStatus().catch(() => {});
  setInterval(() => pollStatus().catch(() => {}), 2000);
  if (!LOCAL_USB) {
    setInterval(() => {
      refreshAgents().catch(() => {});
    }, 5000);
  }

  click("btnDev", async () => {
    if (!LOCAL_USB) await refreshAgents();
    await refreshDevices();
  });
  click("btnShot", () => captureScreen());
  click("btnShotAgain", () => captureScreen());
  click("btnCloseShot", () => closeShotModal());
  if ($("agentSelect") && !LOCAL_USB) {
    $("agentSelect").addEventListener("change", () => {
      localStorage.setItem("netdisk_agent_id", $("agentSelect").value || "");
      refreshDevices().catch(() => {});
    });
  }
  if ($("btnPrepare") && !LOCAL_USB) click("btnPrepare", () => oneClickPrepare());
  click("btnRefreshAll", async () => {
    const btn = $("btnRefreshAll");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "刷新中…";
    }
    try {
      await refreshDevices();
      await pollStatus();
      await loadDataset(
        "cases",
        offD,
        ($("qDataset") && $("qDataset").value.trim()) || "",
        $("tblDataset"),
        $("datasetMeta")
      );
      await loadDataset(
        "results",
        offR,
        ($("qResults") && $("qResults").value.trim()) || "",
        $("tblResults"),
        $("resultsMeta")
      );
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "刷新";
      }
    }
  });
  click("btnDataset", () => {
    offD = 0;
    loadDataset("cases", offD, $("qDataset").value.trim(), $("tblDataset"), $("datasetMeta"));
  });
  click("btnClearFilter", () => {
    if ($("countMin")) $("countMin").value = "";
    if ($("countMax")) $("countMax").value = "";
    if ($("qDataset")) $("qDataset").value = "";
    const sfEl = $("statusFilter");
    if (sfEl) {
      if (sfEl.tagName === "SELECT") sfEl.value = "";
      else sfEl.querySelectorAll('input[type="checkbox"]').forEach((c) => { c.checked = false; });
    }
    syncStatusFilterUI();
    offD = 0;
    loadDataset("cases", offD, "", $("tblDataset"), $("datasetMeta"));
  });
  click("btnPrevD", async () => {
    offD = Math.max(0, offD - pageSize);
    await loadDataset("cases", offD, $("qDataset").value.trim(), $("tblDataset"), $("datasetMeta"));
  });
  click("btnNextD", async () => {
    offD += pageSize;
    const d = await loadDataset("cases", offD, $("qDataset").value.trim(), $("tblDataset"), $("datasetMeta"));
    if (d && offD >= d.total) offD = Math.max(0, d.total - pageSize);
  });
  on("pageSizeD", "change", () => {
    setPageSize($("pageSizeD").value, "D");
    if ($("pageSizeR")) $("pageSizeR").value = String(pageSize);
    offD = 0;
    loadDataset("cases", offD, ($("qDataset") && $("qDataset").value.trim()) || "", $("tblDataset"), $("datasetMeta"));
  });
  on("pageSizeR", "change", () => {
    setPageSize($("pageSizeR").value, "R");
    if ($("pageSizeD")) $("pageSizeD").value = String(pageSize);
    offR = 0;
    loadDataset("results", offR, ($("qResults") && $("qResults").value.trim()) || "", $("tblResults"), $("resultsMeta"));
  });
  click("btnResults", () => {
    offR = 0;
    loadDataset("results", offR, $("qResults").value.trim(), $("tblResults"), $("resultsMeta"));
  });
  click("btnClearResultsQ", () => {
    if ($("qResults")) $("qResults").value = "";
    offR = 0;
    loadDataset("results", offR, "", $("tblResults"), $("resultsMeta"));
  });
  on("qResults", "keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      offR = 0;
      loadDataset("results", offR, $("qResults").value.trim(), $("tblResults"), $("resultsMeta"));
    }
  });
  on("qDataset", "keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      offD = 0;
      loadDataset("cases", offD, $("qDataset").value.trim(), $("tblDataset"), $("datasetMeta"));
    }
  });
  click("btnPrevR", async () => {
    offR = Math.max(0, offR - pageSize);
    await loadDataset("results", offR, $("qResults").value.trim(), $("tblResults"), $("resultsMeta"));
  });
  click("btnNextR", async () => {
    offR += pageSize;
    const d = await loadDataset("results", offR, $("qResults").value.trim(), $("tblResults"), $("resultsMeta"));
    if (d && offR >= d.total) offR = Math.max(0, d.total - pageSize);
  });

  click("btnClearSel", () => {
    selectedIds.clear();
    if ($("caseIds")) $("caseIds").value = "";
    syncSelectedHint();
    reloadCases();
  });
  click("btnUnselectAll", () => {
    selectedIds.clear();
    if ($("caseIds")) $("caseIds").value = "";
    syncSelectedHint();
    reloadCases();
  });
  click("btnSelectAll", async () => {
    const btn = $("btnSelectAll");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "选中中…";
    }
    try {
      const u = new URL(api("/dataset"), location.origin);
      u.searchParams.set("which", "cases");
      u.searchParams.set("offset", "0");
      u.searchParams.set("limit", "1");
      u.searchParams.set("ids", "1");
      fillDatasetQuery(u, "cases", ($("qDataset") && $("qDataset").value.trim()) || "");
      const data = await fetch(u).then((r) => r.json());
      if (!data.ok) {
        alert(data.error || "全选失败");
        return;
      }
      for (const id of data.ids || []) {
        if (id) selectedIds.add(String(id));
      }
      syncSelectedHint();
      await reloadCases();
    } catch (err) {
      alert("全选异常: " + (err && err.message ? err.message : err));
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "全部选中";
      }
    }
  });
  on("statusFilter", "change", () => {
    syncStatusFilterUI();
    offD = 0;
    reloadCases();
  });
  setupStatusMenu();
  click("btnUseSelected", () => {
    if ($("caseIds")) $("caseIds").value = [...selectedIds].join(",");
    syncSelectedHint();
  });

  click("btnStart", async () => {
    const serial = $("serial").value;
    if (!serial) {
      alert("请选择设备");
      return;
    }
    const limitRaw = $("limit").value.trim();
    const idsRaw = $("caseIds").value.trim();
    const force = !!($("forceRerun") && $("forceRerun").checked);
    const body = {
      serial,
      max_images: Number($("maxImages").value || 60),
      force,
    };
    if (selectedIds.size > 0) {
      body.case_ids = [...selectedIds];
    } else if (idsRaw) {
      body.case_ids = idsRaw.split(/[,，\s]+/).filter(Boolean);
    }
    if (limitRaw) body.limit = Number(limitRaw);
    if (!body.case_ids && !body.limit) {
      if (
        !confirm(
          force
            ? "未勾选用例且未设上限：将重跑全部（含已完成），确认？"
            : "未勾选用例且未设上限：将跳过已「完成」，跑剩余全部，确认？"
        )
      )
        return;
    } else if (force && body.case_ids) {
      if (!confirm(`强制重跑指定 ${body.case_ids.length} 条（含已完成）。确认？`)) return;
    }
    const res = await fetch(api("/run/start"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }).then((r) => r.json());
    if (!res.ok) alert(res.error || "启动失败");
    pollStatus();
  });
  click("btnStop", async () => {
    await fetch(api("/run/stop"), { method: "POST" });
    pollStatus();
  });

  click("btnCloseModal", closeCellModal);
  on("cellModal", "click", (e) => {
    if (e.target === $("cellModal")) closeCellModal();
  });
  click("btnCopyCell", async () => {
    const text = ($("modalBody") && $("modalBody").textContent) || "";
    try {
      await navigator.clipboard.writeText(text);
      $("btnCopyCell").textContent = "已复制";
      setTimeout(() => ($("btnCopyCell").textContent = "复制"), 1200);
    } catch (_) {
      alert("复制失败，请手动全选复制");
    }
  });
})();
