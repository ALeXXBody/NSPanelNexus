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
  const OBJ_IDS = ["screen", "brightness", "module_remote_desktop", "sensor_remote_desktop",
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
    }

    setConfig() { /* not needed */ }

    set hass(hass) {
      this._hass = hass;
      if (!this._rendered) this._skeleton();
      this._rendered = true;
      this._schedule();
    }

    _schedule(force) {
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
        <div class="topbar"></div><div class="body">Loading…</div>`;
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
          this._registry = await h.callApi("GET", "config/entity_registry/list");
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
      }
      this._render();
    }

    // hass.states is a Proxy — Object.keys() does NOT work on it.
    // Entity ids are deterministic: <domain>.nspanel_<dev>_<obj>
    _entityMap(entry, dev) {
      const h = this._hass, out = {};
      const slug = String(dev).toLowerCase().replace(/[^a-z0-9_]+/g, "_");
      const base = "nspanel_" + slug + "_";
      // key -> [domain, suffix]
      const candidates = {
        screen: ["switch", "screen"],
        brightness: ["number", "brightness"],
        remote_desktop_switch: ["switch", "module_remote_desktop"],
        remote_desktop_url: ["sensor", "remote_desktop"],
        illuminance: ["sensor", "illuminance"],
        ip_address: ["sensor", "ip_address"],
        uptime: ["sensor", "uptime"],
        firmware: ["sensor", "firmware"],
        touch: ["event", "touch"],
        proximity: ["event", "proximity"],
        light_event: ["event", "light_event"],
        presence: ["binary_sensor", "presence"],
      };
      Object.keys(candidates).forEach((key) => {
        const [domain, suffix] = candidates[key];
        const eid = domain + "." + base + suffix;
        if (h.states[eid]) out[key] = eid;
      });
      return out;
    }

    _topbar() {
      const tabs = [["panels","Panels"],["firmware","Firmware"],["settings","Settings"],["about","About"]]
        .map(([id, label]) => `<div class="tab ${this._tab === id ? "active" : ""}" data-t="${id}">${label}</div>`)
        .join("");
      return `<div class="topbar">
        <img src="/nspanel-nexus/brand/icon.png" alt="NSPanel Nexus">
        <div style="display:flex;flex-direction:column;justify-content:center">
          <span class="name">NSPanel <b>Nexus</b></span>
          <span class="ver">v0.4.10</span>
        </div>
        <div class="tabs">${tabs}</div>
      </div>`;
    }

    _render() {
      const h = this._hass, root = this.shadowRoot;
      root.innerHTML = `<style>${CSS}</style>` + this._topbar();
      root.querySelectorAll(".tab").forEach((t) => {
        t.onclick = () => { this._tab = t.dataset.t; this._render(); };
      });
      const body = document.createElement("div");
      body.className = "body";
      root.appendChild(body);

      if (this._addForm) {           // an add-panel flow is open: keep it on screen
        this._renderAddFlowView(body);
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
      if (!this._entries || !this._entries.length) {
        body.innerHTML = `<h2>Panels</h2><div class="card">
          No NSPanel Nexus panels are linked yet.<br>
          <span class="about">Add one: Settings → Devices &amp; services → Add integration → NSPanel Nexus.</span>
        </div>`;
        return;
      }

      if (this._tab === "panels") this._renderPanels(body);
      else if (this._tab === "firmware") this._renderFirmware(body);
      else if (this._tab === "settings") this._renderSettings(body);
      else this._renderAbout(body);
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
      this._entries.forEach((entry) => {
        const data = (entry && (entry.data || entry.raw_data)) || null;
        const dev = data ? (data.device_name || entry.title || "panel")
                         : (entry ? (entry.title || "").replace(/^NSPanel /, "") : "panel");
        const ents = this._entityMap(entry, dev);
        console.log("NSPanel Nexus: device=", dev, "ents=", JSON.stringify(ents));
        const st = (id) => (ents[id] ? h.states[ents[id]] : null);
        const probe = st("uptime") || st("ip_address");
        const online = probe ? probe.state !== "unavailable" : true;
        const screen = st("screen");
        const bright = st("brightness");
        const rdp = st("remote_desktop_switch");
        const url = st("remote_desktop_url");
        const lux = st("illuminance");
        const ip = st("ip_address");

        const card = document.createElement("div");
        card.className = "card " + (online ? "on" : "off");
        card.innerHTML = `
          <div class="pname">${dev}</div>
          ${ip ? `<div class="stat"><span class="ic">⌂</span>${ip.state}</div>` : ""}
          ${lux ? `<div class="stat"><span class="ic">☀</span>${lux.state} lx</div>` : ""}
          ${probe ? `<div class="stat"><span class="ic">⏱</span>uptime ${probe.state} s</div>` : ""}
          <div class="actions" style="display:flex;border:0;padding:6px 0 0">
            <button class="primary" data-act="screen">${screen && screen.state === "on" ? "Sleep" : "Wake"}</button>
            <input type="range" min="0" max="255" value="${bright ? bright.state : 128}" data-br title="brightness">
            ${rdp ? `<button data-act="rdp">${rdp.state === "on" ? "RD on" : "RD off"}</button>` : ""}
            ${url && url.state ? `<button data-act="open">Open</button>` : ""}
            <button class="warn" data-act="reboot">Reboot</button>
            <button class="warn" data-act="remove" title="Unlink this panel from Home Assistant">Unlink</button>
          </div>
`;

        const on = (sel, fn) => {
          const el = card.querySelector(sel);
          if (el) el.onclick = fn;
        };
        const brEl = card.querySelector("[data-br]");
        if (brEl && ents["brightness"]) {
          brEl.oninput = (e) => {
            h.callService("number", "set_value",
              { entity_id: ents["brightness"], value: Number(e.target.value) });
          };
        }
        on("[data-act=screen]", () => {
          const btn = card.querySelector("[data-act=screen]");
          const turningOff = screen && screen.state === "on";
          if (btn) btn.textContent = turningOff ? "Wake" : "Sleep";
          if (ents["screen"]) h.callService("switch",
            turningOff ? "turn_off" : "turn_on", { entity_id: ents["screen"] });
          setTimeout(() => this._schedule(true), 300);
        });
        on("[data-act=rdp]", () => {
          const btn = card.querySelector("[data-act=rdp]");
          const turningOff = rdp && rdp.state === "on";
          if (btn) btn.textContent = turningOff ? "RD off" : "RD on";
          if (ents["remote_desktop_switch"]) h.callService("switch",
            turningOff ? "turn_off" : "turn_on",
            { entity_id: ents["remote_desktop_switch"] });
          setTimeout(() => this._schedule(true), 300);
        });
        on("[data-act=open]", () => { if (url && url.state) window.open(url.state, "_blank"); });
        on("[data-act=reboot]", () =>
          h.callService("nspanel_nexus", "command", { device_name: dev, command: "reboot" })
            .catch((e) => { console.error("reboot failed:", e); alert("Reboot command failed: " + (e && e.message || e)); }));
        on("[data-act=remove]", () => {
          if (!confirm(`Unlink ${dev} from Home Assistant? The panel itself is not touched.`)) return;
          this._deleteEntry(entry.entry_id);
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
        const dev = data ? data.device_name : (entry.title || "").replace(/^NSPanel /, "");
        const ents = this._entityMap(entry, dev);
        const cur = ents["firmware"] ? h.states[ents["firmware"]] : null;
        const card = document.createElement("div");
        card.className = "card on";
        card.innerHTML = `
          <div class="pname">${dev}</div>
          <div class="stat">Current version: ${cur ? cur.state : "unknown"}</div>
          <div class="actions"><button class="primary" data-act="check">Check for update</button></div>`;
        card.querySelector("[data-act=check]").onclick = () =>
          h.callService("nspanel_nexus", "command", { device_name: dev, command: "firmware:check" })
            .catch((e) => { console.error("firmware check failed:", e); alert("Firmware check failed: " + (e && e.message || e)); });
        grid.appendChild(card);
      });
    }

    _renderSettings(body) {
      const h = this._hass;
      body.innerHTML = `<h2>Settings</h2>
        <div class="card about">
          Per-panel settings (MQTT host/port, HA token, dashboard URL, module switches)
          live on the panel app's own screen, and can also be changed from Home Assistant:
          <br><br>
          <button class="btn" id="cfg">Open panel configuration</button>
          <div class="sub" style="margin-top:10px">
            Or use the <b>nspanel_nexus.config_update</b> action from automations, e.g.
            <code>{"brightness": 180}</code> or <code>{"module.remote_desktop.enabled": true}</code>.
          </div>
        </div>`;
      body.querySelector("#cfg").onclick = () =>
        this.dispatchEvent(new CustomEvent("config_entries_open", {
          bubbles: true, composed: true,
        }));
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
          await this._schedule(true), this._refresh();
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
              ? `<select name="${f.name}">${(f.options || []).map((o) =>
                    `<option value="${o[0]}" ${o[0] === f.default ? "selected" : ""}>${o[1]}</option>`).join("")}</select>`
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
        const r = await fetch(`/api/config/config_entries/entry/${entryId}`, {
          method: "DELETE",
          headers: { Authorization: "Bearer " + this._token(), },
        });
        if (r.ok) {
          this._flash("Panel removed.");
          await this._refresh();
        } else {
          this._flash("Delete failed: HTTP " + r.status);
        }
      } catch (e) {
        this._flash("Delete failed: " + (e && e.message || e));
      }
    }

    _token() {
      for (const k of Object.keys(localStorage)) {
        try {
          const v = JSON.parse(localStorage.getItem(k));
          if (v && v.access_token) return v.access_token;
        } catch (_) {}
      }
      return null;
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
