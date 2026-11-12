/**
 * NSPanel Nexus — HA sidebar manager.
 *
 * Layout inspired by the NSPanelManager web app: dark theme, panel cards with
 * a green/gray status bar, top navigation. NSPanel Nexus logo, text and logic.
 *
 * Renders immediately (never blank) and surfaces errors in the panel.
 */
(function () {
  const CSS = `
    :host{display:block;min-height:100%;background:#181a1f;
          font-family:Roboto,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;
          color:#e8ebef}
    .topbar{display:flex;align-items:center;gap:14px;padding:0 18px;height:80px;flex-wrap:wrap;
            background:#111318;border-bottom:1px solid #22262e;position:sticky;top:0;z-index:5}
    .topbar img{height:64px;width:64px;border-radius:14px;background:#0d1016;padding:4px}
    .topbar .name{font-size:22px;font-weight:700;color:#fff;letter-spacing:.3px;white-space:nowrap;line-height:1}
    .topbar .name b{color:#FF8C28}
    .topbar .ver{font-size:11px;color:#8d97a3;margin-top:2px}
    .tabs{display:flex;gap:2px;margin-left:auto;flex-wrap:wrap}
    .tab{padding:10px 18px;cursor:pointer;font-size:14px;color:#aab2bd;border-radius:0}
    .tab:hover{color:#fff}
    .tab.active{color:#fff;border-bottom:2px solid #FF8C28}
    .body{padding:16px 16px;max-width:1500px}
    h2{font-size:26px;font-weight:400;margin:4px 0 22px;color:#e8ebef}
    .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px}
    .card{background:#22252b;border-radius:4px;border-top:3px solid #757575;
          padding:14px 16px 12px;color:#dfe4ea;position:relative;
          box-shadow:0 1px 3px rgba(0,0,0,.35)}
    .card.on{border-top-color:#2e9e44}
    .card .pname{font-size:17px;font-weight:500;margin-bottom:10px;color:#f2f4f7}
    .stat{display:flex;align-items:center;gap:8px;font-size:13px;margin:7px 0;color:#c3cad3}
    .stat .ic{width:16px;text-align:center;color:#9aa2ad}
    .gear{position:absolute;bottom:10px;right:12px;border:0;background:transparent;
          color:#9aa2ad;font-size:17px;cursor:pointer;padding:4px}
    .gear:hover{color:#fff}
    .actions{display:none;margin-top:12px;padding-top:10px;border-top:1px solid #2c323b;
             flex-wrap:wrap;gap:6px}
    .card.open .actions{display:flex}
    .actions button,.btn{border:0;border-radius:3px;padding:8px 14px;cursor:pointer;
           font-size:13px;background:#2c323b;color:#dfe4ea}
    .actions button:hover,.btn:hover{background:#363d47}
    .actions button.primary{background:#FF8C28;color:#111318;font-weight:600}
    .actions button.warn{background:#5d1d1d;color:#ff9b9b}
    input[type=range]{width:100%;max-width:220px;accent-color:#FF8C28;vertical-align:middle}
    select{padding:8px;border-radius:3px;background:#232830;color:#dfe4ea;border:1px solid #2c323b}
    .rdimg{width:100%;border-radius:6px;background:#0d1016;min-height:120px;display:block;cursor:pointer}
    .rdbtnrow{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}
    .modal-veil{position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:50;display:flex;align-items:flex-start;justify-content:center;padding:24px 12px;overflow:auto}
    .modal{background:#22252b;border-radius:8px;max-width:640px;width:100%;padding:18px 20px;color:#dfe4ea;box-shadow:0 4px 24px rgba(0,0,0,.5)}
    .modal h3{margin:0 0 12px;font-size:18px;color:#fff}
    .modal .x{float:right;border:0;background:transparent;color:#9aa2ad;font-size:18px;cursor:pointer}
    .field{margin:10px 0}
    .lbl{font-size:12px;color:#9aa2ad;margin-bottom:4px}
    input.txt{width:100%;max-width:420px;box-sizing:border-box;padding:8px 10px;border-radius:3px;background:#232830;color:#dfe4ea;border:1px solid #2c323b;font-size:13px}
    .err{color:#ff8a8a;font-size:13px;white-space:pre-wrap}
    .about{font-size:13px;color:#aab2bd;line-height:1.6}
    a{color:#FF8C28;text-decoration:none}
    @media (max-width:768px){
      .topbar{height:auto;padding:12px 14px;gap:10px}
      .topbar img{height:48px;width:48px;padding:3px}
      .topbar .name{font-size:18px}
      .topbar .ver{font-size:10px}
      .tabs{margin-left:0;width:100%;justify-content:flex-start}
      .tab{padding:8px 12px;font-size:13px}
      .body{padding:12px 12px}
      h2{font-size:20px;margin-bottom:14px}
      .grid{grid-template-columns:1fr;gap:12px}
      .card{padding:12px}
      input[type=range]{max-width:100%}
    }
    @media (max-width:480px){
      .topbar img{height:40px;width:40px}
      .topbar .name{font-size:16px}
      .tab{padding:6px 10px;font-size:12px}
    }
  `;

  // which entity unique_ids map to which row in a panel card
  const OBJ_IDS = ["screen", "brightness", "module_remote_desktop",
    "sensor_illuminance", "sensor_uptime", "sensor_ip_address", "sensor_firmware",
    "event_touch", "event_proximity", "event_light", "presence",
    "module_voice", "module_zigbee"];

  class NexusPanelManager extends HTMLElement {
    constructor() {
      super();
      this.attachShadow({ mode: "open" });
      this._hass = null;
      this._tab = "panels";
      this._entries = null;
      this._registry = null;
      this._error = null;
      this._loading = false;
      this._addForm = null;   // config-flow state when adding a panel
      this._modal = null;     // open modal overlay (suspends refresh like _addForm)
      this._lastRefresh = 0;
      this._t = null;
      this._rendered = false;
      this._skeleton();      // visible immediately — the panel is never blank
    }

    setConfig() { /* not needed */ }

    set panel(panel) {
      this._panel = panel;
      if (this._rendered && this._hass) this._render();
    }

    get panel() {
      return this._panel;
    }

    get _version() {
      const v = this._panel && this._panel.config && this._panel.config.version;
      return v || "v0.4.42";
    }

    connectedCallback() {
      // Never leave the sidebar empty, even if hass is late or absent.
      if (!this.shadowRoot.innerHTML) this._skeleton();
    }

    set hass(hass) {
      this._hass = hass;
      if (!this._rendered) this._skeleton();
      this._rendered = true;
      this._schedule();
    }

    _schedule(force) {
      if (this._addForm || this._modal) return; // don't wipe open form/modal with background refresh
      const now = Date.now();
      if (!force && now - this._lastRefresh < 5000) {
        clearTimeout(this._t);
        this._t = setTimeout(() => this._schedule(true), 5000 - (now - this._lastRefresh));
        return;
      }
      clearTimeout(this._t);
      this._t = setTimeout(() => this._refresh(), 120);
    }

    _skeleton() {
      this.shadowRoot.innerHTML = `<style>${CSS}</style>
        <div class="topbar"></div><div class="body">Loading NSPanel Nexus…</div>`;
    }

    async _refresh() {
      const h = this._hass;
      if (!h) return;
      this._lastRefresh = Date.now();
      try {
        try {
          const ws = await h.callWS({ type: "config_entries/get", include_data: true });
          this._entries = (ws || []).filter((e) => e.domain === "nspanel_nexus");
        } catch (_) {
          const entries = await h.callApi("GET", "config/config_entries/entry");
          this._entries = (entries || []).filter((e) => e.domain === "nspanel_nexus");
        }
        try {
          // `config/entity_registry/list` is a WebSocket command, not a REST
          // endpoint — calling it over REST always returned 404.
          this._registry = await h.callWS({ type: "config/entity_registry/list" });
        } catch (_) { this._registry = []; }
        this._entries.forEach((e) => {
          if (!e.data) {
            const mine = (this._registry || []).filter((r) => r.config_entry_id === e.entry_id);
            for (const r of mine) {
              const uid = r.unique_id || "";
              if (uid.startsWith("nexus_")) {
                const rest = uid.slice(6);
                const hit = OBJ_IDS.find((o) => rest.endsWith("_" + o) || rest === o);
                if (hit) {
                  e.data = { device_name: rest.slice(0, rest.length - hit.length - 1), topic_prefix: "nspanelpro" };
                  break;
                }
              }
            }
          }
        });
        this._error = null;
      } catch (e) {
        this._error = "Could not read panels: " + (e && e.message ? e.message : e);
        console.error("Nexus refresh error:", e);
      }
      this._render();
    }

    _esc(v) {
      return String(v == null ? "" : v).replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
    }

    // hass.states is a Proxy — Object.keys() does NOT work on it.
    // Entity ids are deterministic: <domain>.nspanel_<dev>_<obj>
    // Registry fallback: if user customized entity_ids, resolve via unique_id.
    _entityMap(entry, dev) {
      const h = this._hass, out = {};
      const slug = String(dev).toLowerCase().replace(/[^a-z0-9_]+/g, "_");
      const base = "nspanel_" + slug + "_";
      // key -> [domain, suffix, object_id]
      const candidates = {
        screen: ["switch", "screen", "screen"],
        brightness: ["number", "brightness", "brightness"],
        remote_desktop_switch: ["switch", "remote_desktop", "module_remote_desktop"],
        module_voice: ["switch", "voice_satellite", "module_voice"],
        module_zigbee: ["switch", "zigbee", "module_zigbee"],
        module_sensors: ["switch", "sensors", "module_sensors"],
        module_display: ["switch", "display", "module_display"],
        module_dashboard: ["switch", "dashboard", "module_dashboard"],
        module_firmware: ["switch", "firmware", "module_firmware"],
        module_fleet: ["switch", "fleet", "module_fleet"],
        query_button: ["button", "re_read_config", "query_config"],
        cfg_zigbee_role: ["select", "zigbee_role", "config_zigbee_role"],
        illuminance: ["sensor", "illuminance", "sensor_illuminance"],
        ip_address: ["sensor", "ip_address", "sensor_ip_address"],
        uptime: ["sensor", "uptime", "sensor_uptime"],
        firmware: ["sensor", "firmware", "sensor_firmware"],
        touch: ["event", "touch", "event_touch"],
        proximity: ["event", "proximity", "event_proximity"],
        light_event: ["event", "light_event", "event_light"],
        presence: ["binary_sensor", "presence", "presence"],
      };
      // Build registry lookup once: unique_id -> entity_id for this entry
      let regByUid = null;
      try {
        regByUid = {};
        (this._registry || []).forEach((r) => {
          if (r.config_entry_id === entry.entry_id && r.unique_id && r.entity_id) {
            regByUid[r.unique_id] = r.entity_id;
          }
        });
      } catch (_) { regByUid = null; }
      const wantUid = (obj) => "nexus_" + dev + "_" + obj;
      ["mqtt_host","mqtt_user","mqtt_pass","topic_prefix","ha_topic_prefix","ha_url","ha_token","dashboard_url","remote_desktop_token","nexus_manifest_url","zigbee_topic_base","screen_on_weekday_begin","screen_on_weekday_end","screen_on_weekend_begin","screen_on_weekend_end"].forEach((k) => {
        candidates["cfg_" + k] = ["text", k, "config_" + k];
      });
      ["mqtt_port","remote_desktop_port","light_below","light_above","proximity_delta","brightness_on_light_below","brightness_on_light_above"].forEach((k) => {
        candidates["cfg_" + k] = ["number", k, "config_" + k];
      });
      Object.keys(candidates).forEach((key) => {
        const [domain, suffix, obj] = candidates[key];
        let eid = null;
        if (regByUid && regByUid[wantUid(obj)] && h.states[regByUid[wantUid(obj)]]) {
          eid = regByUid[wantUid(obj)];
        } else {
          const guess = domain + "." + base + suffix;
          if (h.states[guess]) eid = guess;
        }
        if (eid) out[key] = eid;
      });
      return out;
    }

    _topbar() {
      const tabs = [["panels","Panels"],["about","About"]]
        .map(([id, label]) => `<div class="tab ${this._tab === id ? "active" : ""}" data-t="${id}">${label}</div>`)
        .join("");
      return `<div class="topbar">
        <img src="/nspanel-nexus/brand/icon.png" alt="NSPanel Nexus">
        <div style="display:flex;flex-direction:column;justify-content:center">
          <span class="name">NSPanel <b>Nexus</b></span>
          <span class="ver">${this._version}</span>
        </div>
        <div class="tabs">${tabs}</div>
      </div>`;
    }

    _render() {
      const h = this._hass, root = this.shadowRoot;
      try {
      root.innerHTML = `<style>${CSS}</style>` + this._topbar();
      root.querySelectorAll(".tab").forEach((t) => {
        t.onclick = () => { this._tab = t.dataset.t; this._render(); };
      });
      const body = document.createElement("div");
      body.className = "body";
      root.appendChild(body);

      if (this._addForm) {           // an add-panel flow is open: keep it on screen
        this._renderAddFlow();
        return;
      }
      if (this._toast) {
        body.innerHTML += `<div class="card about" id="toast">${this._toast}</div>`;
        this._toast = null;
      }
      if (this._error) {
        body.innerHTML = `<div class="err">${this._error}</div>
          <div class="about">Fix the integration setup, then reload.</div>`;
        return;
      }
      if (this._tab === "about") this._renderAbout(body);
      else this._renderPanels(body);
    } catch (e) {
      if (root) root.innerHTML = `<div style="color:#ff8a8a;padding:20px">Render error: ${e && e.message ? e.message : e}</div>`;
      console.error("Nexus render error:", e);
    }
    }

    // ---- modal popups (Info / Settings per panel) ----------------------
    _openModal(title) {
      this._closeModal();
      const veil = document.createElement("div");
      veil.className = "modal-veil";
      veil.innerHTML = `<div class="modal" role="dialog"><button class="x" title="Close">\u2715</button><h3>${this._esc(title)}</h3><div class="mbody"></div></div>`;
      veil.querySelector(".x").onclick = () => this._closeModal();
      veil.addEventListener("mousedown", (e) => { if (e.target === veil) this._closeModal(); });
      veil.tabIndex = -1;
      veil.onkeydown = (e) => { if (e.key === "Escape") this._closeModal(); };
      this.shadowRoot.appendChild(veil);
      this._modal = veil;
      try { veil.focus(); } catch (_) {}
      return veil.querySelector(".mbody");
    }

    _closeModal() {
      if (this._modal) { try { this._modal.remove(); } catch (_) {} this._modal = null; }
    }

    _rdTokenKey(devSafe) { return "nexus-rd-token-" + devSafe; }
    _rdToken(devSafe) {
      try { return localStorage.getItem(this._rdTokenKey(devSafe)) || ""; } catch (_) { return ""; }
    }
    _rdSetToken(devSafe) {
      const cur = this._rdToken(devSafe);
      const v = prompt("Remote desktop token for this panel (panel Setup page → Remote desktop token):", cur || "");
      if (v == null) return null;
      try {
        if (v) localStorage.setItem(this._rdTokenKey(devSafe), v);
        else localStorage.removeItem(this._rdTokenKey(devSafe));
      } catch (_) {}
      return v || "";
    }
    _panelSvg() {
      return `<svg viewBox="0 0 480 480" width="100%" style="display:block;border-radius:6px;background:#0d1016" role="img" aria-label="NSPanel Pro (remote desktop off)">
        <rect x="110" y="20" width="260" height="440" rx="18" fill="#1c1f26" stroke="#333945" stroke-width="2"/>
        <rect x="130" y="44" width="220" height="340" rx="8" fill="#101319"/>
        <text x="240" y="150" text-anchor="middle" fill="#FF8C28" font-family="monospace" font-size="44">--:--</text>
        <text x="240" y="190" text-anchor="middle" fill="#8d97a3" font-family="monospace" font-size="16">remote desktop off</text>
        <rect x="130" y="404" width="220" height="10" rx="5" fill="#2c323b"/>
        <circle cx="240" cy="409" r="8" fill="#3a414d"/>
      </svg>`;
    }

    _rdBase(ip, portSt) {
      if (!ip || !ip.state || ["unknown","unavailable",""].includes(ip.state)) return null;
      let port = 8080;
      if (portSt && portSt.state != null && !isNaN(Number(portSt.state))) port = Number(portSt.state);
      return "http://" + ip.state + ":" + port + "/";
    }

    _openRemote(dev, devSafe, rdBase, rdToken) {
      const body = this._openModal(dev + " — remote desktop");
      if (!body) return;
      if (!rdBase) { body.innerHTML = `<div class="about">Waiting for the panel IP address…</div>`; return; }
      let token = rdToken || this._rdToken(devSafe);
      if (!token) { token = this._rdSetToken(devSafe); if (!token) { this._closeModal(); return; } }
      body.innerHTML = `
        <img class="rdimg" id="rd-live" src="${rdBase}stream?token=${encodeURIComponent(token)}" alt="live panel screen" style="cursor:crosshair;touch-action:none">
        <div class="rdbtnrow">
          <button class="btn" data-k="3">HOME</button>
          <button class="btn" data-k="4">BACK</button>
          <button class="btn" data-k="26">POWER</button>
          <button class="btn" data-s="up">↑ up</button>
          <button class="btn" data-s="down">↓ down</button>
          <button class="btn" data-tok>Set token</button>
        </div>
        <div class="about" id="rd-log">click to tap · drag to swipe · wheel/keys to scroll</div>`;
      const img = body.querySelector("#rd-live");
      const log = body.querySelector("#rd-log");
      const post = (url, bodyStr) => fetch(rdBase + url, { method: "POST", body: bodyStr, headers: { "X-Nexus-Token": token } });
      const tap = (x, y) => { post("input/tap", "x=" + x + "&y=" + y); if (log) log.textContent = "tap " + x + "," + y; };
      const swipe = (x1, y1, x2, y2, ms) => { post("input/swipe", "x1=" + x1 + "&y1=" + y1 + "&x2=" + x2 + "&y2=" + y2 + "&ms=" + ms); };
      let down = null;
      if (img) {
        img.addEventListener("pointerdown", (e) => { e.preventDefault(); try { img.setPointerCapture(e.pointerId); } catch (_) {} down = { x: e.clientX, y: e.clientY }; });
        img.addEventListener("pointerup", (e) => {
          if (!down) return;
          const r = img.getBoundingClientRect(); const dx = e.clientX - down.x, dy = e.clientY - down.y; const d0 = down; down = null;
          const px = (v) => Math.round((v - r.left) / r.width * 480);
          const py = (v) => Math.round((v - r.top) / r.height * 480);
          if (Math.hypot(dx, dy) < 6) tap(px(d0.x), py(d0.y));
          else swipe(px(d0.x), py(d0.y), px(e.clientX), py(e.clientY), Math.min(600, Math.max(250, Math.hypot(dx, dy))));
        });
        img.addEventListener("pointercancel", () => { down = null; });
      }
      body.querySelectorAll("[data-k]").forEach((b) => { b.onclick = () => post("input/key", "code=" + b.getAttribute("data-k")); });
      body.querySelectorAll("[data-s]").forEach((b) => {
        b.onclick = () => {
          const dir = b.getAttribute("data-s") === "up" ? -1 : 1;
          if (dir > 0) swipe(240, 360, 240, 140, 300); else swipe(240, 140, 240, 360, 300);
        };
      });
      const tokBtn = body.querySelector("[data-tok]");
      if (tokBtn) tokBtn.onclick = () => { const t = this._rdSetToken(devSafe); if (t != null) { const im = body.querySelector("#rd-live"); if (im) im.src = rdBase + "stream?token=" + encodeURIComponent(t); } };
      const im2 = body.querySelector("#rd-live");
      if (im2) im2.onerror = () => { if (log) log.textContent = "Live screen unavailable (wrong token or panel unreachable)."; };
    }

    _openInfoModal(dev, devSafe, ents, h) {
      const body = this._openModal(dev + " — info");
      if (!body) return;
      const renderInfo = () => {
        const cur = (id) => (ents[id] ? h.states[ents[id]] : null);
        const screen = cur("screen"); const bright = cur("brightness");
        const rdp = cur("remote_desktop_switch"); const firmware = cur("firmware");
        const lux = cur("illuminance"); const ip = cur("ip_address");
        const probe = cur("uptime") || ip;
        body.innerHTML = `
          <div class="stat"><span class="ic">⌂</span>IP: ${ip ? ip.state : "—"}</div>
          <div class="stat"><span class="ic">☀</span>Ambient: ${lux ? lux.state + " lx" : "—"}</div>
          <div class="stat"><span class="ic">⏱</span>Uptime: ${probe ? probe.state + " s" : "—"}</div>
          <div class="stat"><span class="ic">📺</span>Screen: ${screen ? screen.state : "—"}</div>
          <div class="stat"><span class="ic">☀</span>Brightness: ${bright ? bright.state : "—"}</div>
          <div class="stat"><span class="ic">📡</span>Remote desktop: ${rdp ? rdp.state : "—"}</div>
          <div class="stat"><span class="ic">🧠</span>Firmware: ${firmware ? firmware.state : "—"}</div>
          <div style="margin-top:12px;display:flex;flex-wrap:wrap;gap:6px">
            <button class="btn primary" data-act="wake">${screen && screen.state === "on" ? "Sleep" : "Wake"}</button>
            <input type="range" min="0" max="255" value="${bright ? bright.state : 128}" data-br2 title="brightness" style="max-width:180px">
            <button class="btn" data-act="fwcheck">Check firmware</button>
            <button class="btn" data-act="reboot2">Reboot</button>
            <button class="btn" data-act="unlink" style="opacity:.7">Unlink</button>
          </div>`;
          const wake = body.querySelector("[data-act=wake]");
          if (wake) wake.onclick = () => {
            const turningOff = screen && screen.state === "on";
            wake.textContent = turningOff ? "Wake" : "Sleep";
            if (ents["screen"]) h.callService("switch", turningOff ? "turn_off" : "turn_on", { entity_id: ents["screen"] }).catch(e => alert(e && e.message || e));
            setTimeout(() => this._schedule(true), 1000);
          };
          const br2 = body.querySelector("[data-br2]");
          if (br2 && ents["brightness"]) {
            br2.oninput = (e) => {
              h.callService("number", "set_value", { entity_id: ents["brightness"], value: Number(e.target.value) }).catch(err => console.error(err));
            };
          }
          const fw = body.querySelector("[data-act=fwcheck]");
          if (fw) fw.onclick = () => h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "firmware:check" }).catch(e => alert(e && e.message || e));
          const rb = body.querySelector("[data-act=reboot2]");
          if (rb) rb.onclick = () => { if (!confirm("Reboot " + dev + "?")) return; h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "reboot" }).catch(e => alert(e && e.message || e)); };
          const ul = body.querySelector("[data-act=unlink]");
          if (ul) ul.onclick = () => { if (!confirm("Unlink " + dev + "?")) return; this._deleteEntry(entry.entry_id); this._closeModal(); };
      };
      renderInfo();
      const infoTimer = setInterval(() => { if (!this._modal) { clearInterval(infoTimer); return; } renderInfo(); }, 3000);
    }

    _openSettingsModal(dev, devSafe, ents, entry, h) {
      const body = this._openModal(dev + " — settings");
      if (!body) return;
      this._renderSettingsForEntry(body, entry, dev, devSafe, ents, h);
    }

    _renderPanels(body) {
      const h = this._hass;

      body.innerHTML = `<h2>Panels</h2>
        <div class="actions" style="display:flex;flex-wrap:wrap;gap:6px;margin:-12px 0 16px">
          <button class="btn primary" id="add">+ Add panel</button>
          <span id="msg" class="about"></span>
        </div>
        <div class="grid" id="grid"></div>`;
      const grid = body.querySelector("#grid");
      body.querySelector("#add").onclick = () => this._startAddFlow();
      if (!this._entries || !this._entries.length) {
        grid.innerHTML = `<div class="card">
          No NSPanel Nexus panels are linked yet.<br>
          <span class="about">Add one with + Add panel above — no need to leave this page.</span>
        </div>`;
        return;
      }
      this._entries.forEach((entry) => {
        const merged = Object.assign({}, entry.data, entry.options, entry.raw_data);
        const data = (entry && (merged && Object.keys(merged).length ? merged : null)) || null;
        const rawDev = data ? (data.device_name || (entry.title || "").replace(/^NSPanel /, "") || "nspanel")
                            : (entry ? (entry.title || "").replace(/^NSPanel /, "") : "nspanel");
        const dev = rawDev;  // friendly name for display
        const devSafe = rawDev.replace(/[^A-Za-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "") || "nspanel";  // topic-safe for services/entities
        const ents = this._entityMap(entry, devSafe);
        const st = (id) => (ents[id] ? h.states[ents[id]] : null);
        const probe = st("uptime") || st("ip_address");
        const online = probe ? probe.state !== "unavailable" : Object.keys(ents).length > 0;
        const screen = st("screen");
        const bright = st("brightness");
        const rdp = st("remote_desktop_switch");
        const lux = st("illuminance");
        const ip = st("ip_address");
        const rdPort = st("cfg_remote_desktop_port");
        const rdBase = this._rdBase(ip, rdPort);
        const rdToken = this._rdToken(devSafe);
        const rdpOn = rdp && rdp.state === "on";

        const card = document.createElement("div");
        card.className = "card " + (online ? "on" : "off");
        let rdHtml = "";
        if (!rdp || !rdpOn) {
          rdHtml = this._panelSvg() + `<div class="about" style="margin:8px 0">Remote desktop is off — turn it on in Settings.</div>`;
        } else if (!rdBase) {
          rdHtml = `<div class="about" style="margin:8px 0">Waiting for the panel IP address…</div>`;
        } else if (!rdToken) {
          rdHtml = this._panelSvg() + `<div class="about" style="margin:8px 0">Remote desktop is on. <button class="btn" data-rd="token">Set access token</button> to show the live screen.</div>`;
        } else {
          rdHtml = `<img class="rdimg" data-rd="img" src="${rdBase}stream?token=${encodeURIComponent(rdToken)}" alt="live panel screen" title="Click to open remote desktop">`;
        }
        card.innerHTML = `
          <div data-rd="wrap">${rdHtml}</div>
          <div class="actions" style="display:flex;border:0;padding:6px 0 0;justify-content:center">
            <button class="btn" data-act="info">Info</button>
            <button class="btn" data-act="settings">Settings</button>
            ${rdpOn && rdBase ? `<button class="btn primary" data-act="remote">Remote</button>` : ""}
          </div>
`;

        const on = (sel, fn) => {
          const el = card.querySelector(sel);
          if (el) el.onclick = fn;
        };
        const brEl = card.querySelector("[data-br]");
        if (brEl && ents["brightness"]) {
          let brTimer = null;
          brEl.oninput = (e) => {
            const v = Number(e.target.value);
            clearTimeout(brTimer);
            brTimer = setTimeout(() => {
              h.callService("number", "set_value",
                { entity_id: ents["brightness"], value: v })
                .catch((err) => console.error("Nexus brightness failed:", err));
            }, 250);
          };
          brEl.onchange = (e) => {
            clearTimeout(brTimer);
            h.callService("number", "set_value",
              { entity_id: ents["brightness"], value: Number(e.target.value) })
              .catch((err) => console.error("Nexus brightness failed:", err));
          };
        }
        on("[data-act=screen]", () => {
          const btn = card.querySelector("[data-act=screen]");
          const turningOff = screen && screen.state === "on";
          if (btn) btn.textContent = turningOff ? "Wake" : "Sleep";
          if (ents["screen"]) h.callService("switch",
            turningOff ? "turn_off" : "turn_on", { entity_id: ents["screen"] })
            .catch((err) => console.error("Nexus screen failed:", err));
          setTimeout(() => this._schedule(true), 1000);
        });
        on("[data-act=rdp]", () => {
          const btn = card.querySelector("[data-act=rdp]");
          const turningOff = rdpOn;
          if (btn) btn.textContent = turningOff ? "RD off" : "RD on";
          if (ents["remote_desktop_switch"]) h.callService("switch",
            turningOff ? "turn_off" : "turn_on",
            { entity_id: ents["remote_desktop_switch"] })
            .catch((err) => console.error("Nexus rdp failed:", err));
          setTimeout(() => this._schedule(true), 1000);
        });
        on("[data-rd=img]", () => this._openRemote(dev, devSafe, rdBase, rdToken));
        on("[data-act=remote]", () => this._openRemote(dev, devSafe, rdBase, rdToken));
        on("[data-rd=token]", () => {
          const t = this._rdSetToken(devSafe);
          if (t != null) setTimeout(() => this._schedule(true), 300);
        });
        {
          const img = card.querySelector("[data-rd=img]");
          if (img) img.onerror = () => {
            const wrap = card.querySelector('[data-rd="wrap"]');
            if (wrap) wrap.innerHTML = `<div class="about" style="margin:8px 0">Live screen unavailable (wrong token or panel unreachable). <button class="btn" data-rd="token2">Set token</button></div>`;
            const b2 = card.querySelector('[data-rd="token2"]');
            if (b2) b2.onclick = () => { const t = this._rdSetToken(devSafe); if (t != null) setTimeout(() => this._schedule(true), 300); };
          };
        }
        on("[data-act=reboot]", () => {
          h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "reboot" })
            .catch((e) => { console.error("reboot failed:", e); alert("Reboot command failed: " + (e && e.message || e)); }); });
        on("[data-act=remove]", () => {
          if (!confirm(`Unlink ${dev} from Home Assistant? The panel itself is not touched.`)) return;
          this._deleteEntry(entry.entry_id);
        });

        on("[data-act=info]", () => {
          const body = this._openModal(dev + " — info");
          if (!body) return;
          const renderInfo = () => {
            const cur = (id) => (ents[id] ? h.states[ents[id]] : null);
            const screen = cur("screen"); const bright = cur("brightness");
            const rdp = cur("remote_desktop_switch"); const firmware = cur("firmware");
            const lux = cur("illuminance"); const ip = cur("ip_address");
            const probe = cur("uptime") || ip;
            body.innerHTML = `
              <div class="stat"><span class="ic">⌂</span>IP: ${ip ? ip.state : "—"}</div>
              <div class="stat"><span class="ic">☀</span>Ambient: ${lux ? lux.state + " lx" : "—"}</div>
              <div class="stat"><span class="ic">⏱</span>Uptime: ${probe ? probe.state + " s" : "—"}</div>
              <div class="stat"><span class="ic">📺</span>Screen: ${screen ? screen.state : "—"}</div>
              <div class="stat"><span class="ic">☀</span>Brightness: ${bright ? bright.state : "—"}</div>
              <div class="stat"><span class="ic">📡</span>Remote desktop: ${rdp ? rdp.state : "—"}</div>
              <div class="stat"><span class="ic">🧠</span>Firmware: ${firmware ? firmware.state : "—"}</div>
              <div style="margin-top:12px">
                <button class="btn primary" data-act="fwcheck">Check for firmware update</button>
                <button class="btn" data-act="reboot2">Reboot panel</button>
              </div>`;
            const fw = body.querySelector("[data-act=fwcheck]");
            if (fw) fw.onclick = () => h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "firmware:check" }).catch(e => alert(e && e.message || e));
            const rb = body.querySelector("[data-act=reboot2]");
            if (rb) rb.onclick = () => { if (!confirm("Reboot " + dev + "?")) return; h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "reboot" }).catch(e => alert(e && e.message || e)); };
          };
          renderInfo();
          const infoTimer = setInterval(() => { if (!this._modal) { clearInterval(infoTimer); return; } renderInfo(); }, 3000);
        });

        on("[data-act=settings]", () => {
          const body = this._openModal(dev + " — settings");
          if (!body) return;
          this._renderSettingsForEntry(body, entry, dev, devSafe, ents, h);
        });

        grid.appendChild(card);
      });
    }

    _renderFirmware(body) {
      const h = this._hass;
      body.innerHTML = `<h2>Firmware</h2><div class="grid" id="grid"></div>`;
      const grid = body.querySelector("#grid");
      this._entries.forEach((entry) => {
        const data = (entry && (entry.data || entry.raw_data)) || null;
        const merged2 = Object.assign({}, entry.data, entry.options, entry.raw_data);
        const rawDev2 = (merged2 && merged2.device_name) ? merged2.device_name : (entry.title || "").replace(/^NSPanel /, "");
        const dev = rawDev2;  // friendly name for display
        const devSafe = rawDev2.replace(/[^A-Za-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "") || "nspanel";  // topic-safe
        const ents = this._entityMap(entry, devSafe);
        const cur = ents["firmware"] ? h.states[ents["firmware"]] : null;
        const card = document.createElement("div");
        card.className = "card on";
        card.innerHTML = `
          <div class="pname">${this._esc(dev)}</div>
          <div class="stat">Current version: ${cur ? cur.state : "unknown"}</div>
          <div class="actions"><button class="primary" data-act="check">Check for update</button></div>`;
        card.querySelector("[data-act=check]").onclick = () =>
          h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "firmware:check" })
            .catch((e) => { console.error("firmware check failed:", e); alert("Firmware check failed: " + (e && e.message || e)); });
        grid.appendChild(card);
      });
    }

    // ---- firmware configuration: every panel setting, editable here ----
    // [config key, label, input type]
    get _textFields() {
      return [
        ["mqtt_host", "MQTT host", "text"],
        ["mqtt_user", "MQTT user", "text"],
        ["mqtt_pass", "MQTT password", "password"],
        ["topic_prefix", "Topic prefix", "text"],
        ["ha_topic_prefix", "HA discovery prefix", "text"],
        ["ha_url", "Home Assistant URL", "text"],
        ["ha_token", "HA long-lived token", "password"],
        ["dashboard_url", "Dashboard URL", "text"],
        ["remote_desktop_token", "Remote desktop token", "password"],
        ["nexus_manifest_url", "Firmware manifest URL", "text"],
        ["zigbee_topic_base", "Zigbee topic base", "text"],
        ["screen_on_weekday_begin", "Screen on, weekday from (HH:MM)", "text"],
        ["screen_on_weekday_end", "Screen on, weekday until (HH:MM)", "text"],
        ["screen_on_weekend_begin", "Screen on, weekend from (HH:MM)", "text"],
        ["screen_on_weekend_end", "Screen on, weekend until (HH:MM)", "text"],
      ];
    }

    // [config key, label, min, max, step]
    get _numFields() {
      return [
        ["mqtt_port", "MQTT port", 1, 65535, 1],
        ["remote_desktop_port", "Remote desktop port", 1, 65535, 1],
        ["light_below", "Light below threshold (lx, 0 = off)", 0, 100000, 1],
        ["light_above", "Light above threshold (lx, 0 = off)", 0, 100000, 1],
        ["proximity_delta", "Proximity delta", 0, 1000, 1],
        ["brightness_on_light_below", "Brightness when dark (-1 = off)", -1, 255, 1],
        ["brightness_on_light_above", "Brightness when bright (-1 = off)", -1, 255, 1],
      ];
    }

    get _moduleToggles() {
      return [
        ["module_sensors", "Sensors"], ["module_display", "Display"],
        ["module_dashboard", "Dashboard"], ["module_remote_desktop", "Remote desktop"],
        ["module_voice", "Voice satellite"], ["module_zigbee", "Zigbee"],
        ["module_firmware", "Firmware"], ["module_fleet", "Fleet"],
      ];
    }

    _renderSettingsForEntry(body, entry, dev, devSafe, ents, h) {
      body.innerHTML = `<h2>Firmware configuration</h2>
        <div class="card about" style="margin-bottom:16px">
          Every setting of the Nexus firmware on the panel, editable from here.
          <b>Load from panel</b> reads the live values first; <b>Save to panel</b>
          sends only the changed fields in one update. Password fields left empty
          keep the stored secret. The MQTT module has no switch on purpose —
          turning it off remotely would silence the panel until someone touches it.
        </div>
        <div class="grid" id="grid"></div>`;
      const grid = body.querySelector("#grid");
      if (!this._entries || !this._entries.length) {
        grid.innerHTML = `<div class="card"><span class="about">No panels linked yet — add one on the Panels tab first.</span></div>`;
        return;
      }
      this._entries.forEach((entry) => {
        const merged = Object.assign({}, entry.data, entry.options, entry.raw_data);
        const rawDev = (merged && merged.device_name) || (entry.title || "").replace(/^NSPanel /, "") || "nspanel";
        const dev = rawDev;
        const devSafe = rawDev.replace(/[^A-Za-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "") || "nspanel";
        const ents = this._entityMap(entry, devSafe);
        const st = (id) => (ents[id] ? h.states[ents[id]] : null);
        const card = document.createElement("div");
        card.className = "card on";
        const fieldId = (k) => "cfg-" + entry.entry_id + "-" + k;
        let html = `<div class="pname">${this._esc(dev)}</div>
          <div class="actions" style="display:flex;border:0;padding:6px 0 10px">
            <button class="btn" data-act="load">Load from panel</button>
            <button class="btn primary" data-act="save">Save to panel</button>
            <span class="about" data-act="msg"></span>
          </div>`;
        this._textFields.forEach(([key, label, type]) => {
          const cur = st("cfg_" + key);
          const val = cur && cur.state != null && cur.state !== "unknown" && cur.state !== "unavailable" ? cur.state : "";
          html += `<div class="field"><div class="lbl">${label}</div>
            <input class="txt" data-cfg="${key}" data-kind="text" type="${type}" value="${type === "password" ? "" : this._esc(val)}" placeholder="${type === "password" ? "(unchanged)" : ""}"></div>`;
        });
        this._numFields.forEach(([key, label, lo, hi, step]) => {
          const cur = st("cfg_" + key);
          const val = cur && cur.state != null && !isNaN(Number(cur.state)) ? Number(cur.state) : "";
          html += `<div class="field"><div class="lbl">${label}</div>
            <input class="txt" data-cfg="${key}" data-kind="number" type="number" min="${lo}" max="${hi}" step="${step}" value="${val}"></div>`;
        });
        {
          const cur = st("cfg_zigbee_role");
          const val = cur ? cur.state : "";
          html += `<div class="field"><div class="lbl">Zigbee role</div>
            <select data-cfg="zigbee_role" data-kind="select">
              <option value="coordinator" ${val === "coordinator" ? "selected" : ""}>coordinator</option>
              <option value="router" ${val === "router" ? "selected" : ""}>router</option>
            </select></div>`;
        }
        html += `<div class="field"><div class="lbl">Modules (on/off apply immediately)</div><div style="display:flex;flex-wrap:wrap;gap:6px">`;
        this._moduleToggles.forEach(([key, label]) => {
          const sw = st(key);
          const isOn = sw && sw.state === "on";
          html += `<button class="btn${isOn ? " primary" : ""}" data-mod="${key}">${label}: ${sw ? (isOn ? "on" : "off") : "?"}</button>`;
        });
        html += `</div><div class="about">MQTT has no switch on purpose — turning it off remotely would silence the panel until someone touches it.</div></div>`;
        card.innerHTML = html;
        grid.appendChild(card);

        const msg = (t) => {
          const m = card.querySelector('[data-act="msg"]');
          if (m) m.textContent = t;
        };
        const flash = (t) => this._flash(t);

        card.querySelector('[data-act="load"]').onclick = async () => {
          const q = ents["query_button"];
          if (!q) { msg("No query button entity yet — is the panel linked?"); return; }
          msg("Asking panel…");
          try {
            await h.callService("button", "press", { entity_id: q });
            setTimeout(() => this._schedule(true), 1500);
            msg("Requested — values arrive in a moment.");
          } catch (e) { msg("Load failed: " + (e && e.message || e)); }
        };

        card.querySelector('[data-act="save"]').onclick = async () => {
          const patch = {};
          card.querySelectorAll("[data-cfg]").forEach((el) => {
            const key = el.getAttribute("data-cfg");
            const kind = el.getAttribute("data-kind");
            const cur = st("cfg_" + key);
            const curVal = cur && cur.state != null ? String(cur.state) : null;
            if (kind === "number") {
              if (el.value === "" || el.value == null) return;
              if (curVal != null && Number(el.value) === Number(curVal)) return;
              patch[key] = Number(el.value);
            } else if (kind === "select") {
              if (curVal != null && el.value === curVal) return;
              patch[key] = el.value;
            } else {
              if (el.type === "password") { if (!el.value) return; }
              else if (curVal != null && el.value === curVal) return;
              patch[key] = el.value;
            }
          });
          if (!Object.keys(patch).length) { msg("Nothing changed."); return; }
          msg("Saving…");
          try {
            await h.callService("nspanel_nexus", "config_update", { device_name: devSafe, config: patch });
            flash("Saved: " + Object.keys(patch).join(", "));
            setTimeout(() => this._schedule(true), 1500);
            msg("");
          } catch (e) { msg("Save failed: " + (e && e.message || e)); }
        };

        card.querySelectorAll("[data-mod]").forEach((btn) => {
          btn.onclick = () => {
            const key = btn.getAttribute("data-mod");
            const eid = ents[key];
            if (!eid) return;
            const stt = h.states[eid];
            const turnOn = !(stt && stt.state === "on");
            btn.textContent = btn.textContent.replace(/: (on|off|\?)$/, ": " + (turnOn ? "on" : "off"));
            h.callService("switch", turnOn ? "turn_on" : "turn_off", { entity_id: eid })
              .catch((e) => { console.error("module toggle failed:", e); msg("Toggle failed: " + (e && e.message || e)); });
            setTimeout(() => this._schedule(true), 1000);
          };
        });
      });
    }

    _renderAbout(body) {
      body.innerHTML = `<h2>About</h2><div class="card about">
        <b>NSPanel Nexus</b> — unified control for Sonoff NSPanel Pro panels running
        the Nexus app and clean firmware.<br><br>
        Modules on each panel (MQTT, sensors, display, dashboard, voice, zigbee,
        firmware, remote desktop) can be switched on or off from the panel app's
        Modules page — or remotely from Home Assistant.<br><br>
        <a href="https://github.com/ALeXXBody/NSPanelNexus" target="_blank" rel="noreferrer"
           style="color:#FF8C28">Project repository</a>
      </div>`;
    }

    // ---- add panel (config flow, rendered in this panel) ----------------

    async _startAddFlow() {
      const h = this._hass;
      if (!h) return;
      try {
        // config flows run over REST (the websocket command does not exist)
        const flow = await h.callApi("POST", "config/config_entries/flow",
          { handler: "nspanel_nexus" });
        this._addForm = flow;
        this._renderAddFlow();
      } catch (e) {
        this._flash("Could not start the setup flow: " + (e && e.message || e));
      }
    }

    async _submitAddFlow(payload) {
      const h = this._hass;
      if (!h || !this._addForm) return;
      try {
        const result = await h.callApi("POST",
          "config/config_entries/flow/" + this._addForm.flow_id, payload);
        if (result.type === "create_entry") {
          this._addForm = null;
          this._flash("Panel added: " + (result.title || ""));
          await this._refresh();
        } else if (result.type === "form") {
          this._addForm = result;
          this._renderAddFlow(result.errors);
        } else if (result.type === "abort") {
          this._flash(result.reason === "already_configured"
            ? "This panel is already linked." : "Aborted: " + result.reason);
          this._addForm = null;
          this._render();
        }
      } catch (e) {
        this._renderAddFlow({ base: e && e.message || String(e) });
      }
    }

    _renderAddFlow(errors) {
      const root = this.shadowRoot.querySelector(".body") || this.shadowRoot;
      const flow = this._addForm;
      const schema = (flow.data_schema || []);
      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        <div class="pname">Add panel</div>
        ${schema.map((f) => `
          <div class="field"><div class="lbl">${f.name}</div>
            ${f.type === "select"
              ? `<select name="${f.name}">${(f.options || []).map((o) => {
                    const v = Array.isArray(o) ? o[0] : (o.value != null ? o.value : o);
                    const l = Array.isArray(o) ? o[1] : (o.label != null ? o.label : String(v));
                    return `<option value="${v}" ${String(v) === String(f.default) ? "selected" : ""}>${l}</option>`; }).join("")}</select>`
              : `<input class="txt" name="${f.name}" value="${f.default || ""}">`}
          </div>`).join("")}
        ${errors && errors.base ? `<div class="err">${errors.base}</div>` : ""}
        ${errors ? Object.keys(errors).filter((k) => k !== "base").map((k) =>
             `<div class="err">${k}: ${errors[k]}</div>`).join("") : ""}
        <div class="actions" style="display:flex">
          <button class="btn primary" id="submit">Add</button>
          <button class="btn" id="cancel">Cancel</button>
        </div>`;
      card.querySelector("#cancel").onclick = () => {
        this._addForm = null;
        this._render();
      };
      card.querySelector("#submit").onclick = () => {
        const payload = {};
        card.querySelectorAll("[name]").forEach((el) => { payload[el.name] = el.value; });
        this._submitAddFlow(payload);
      };
      root.appendChild(card);
    }

    // ---- remove panel (delete config entry) -----------------------------

    async _deleteEntry(entryId) {
      const h = this._hass;
      if (!h) return;
      try {
        await h.callApi("DELETE", "config/config_entries/entry/" + entryId);
        this._flash("Panel removed.");
        await this._refresh();
      } catch (e) {
        console.error("Delete failed:", e);
        this._flash("Delete failed: " + (e && e.message || e) + "\n\nPlease remove manually: Settings → Devices & services → NSPanel Nexus → Delete");
      }
    }

    _flash(msg) {
      const m = this.shadowRoot.querySelector("#msg");
      if (m) m.textContent = msg;
      else {
        this._toast = msg;
        this._render();
      }
    }
  }

  customElements.define("nspanel-nexus-manager", NexusPanelManager);
})();
