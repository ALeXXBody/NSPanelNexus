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
    :host{display:block;color:var(--primary-text-color)}
    .topbar{display:flex;align-items:center;gap:12px;padding:10px 18px;
            background:#111318;border-bottom:1px solid #22262e}
    .topbar img{height:38px;width:38px;border-radius:10px}
    .topbar .name{font-size:18px;font-weight:700;color:#fff;letter-spacing:.3px}
    .topbar .name b{color:#FF8C28;font-weight:700}
    .tabs{display:flex;gap:4px;margin-left:26px}
    .tab{padding:8px 16px;border-radius:8px;cursor:pointer;font-size:14px;color:#aab2bd}
    .tab.active{background:#232830;color:#fff}
    .body{padding:18px 22px;max-width:1400px}
    h2{font-size:24px;font-weight:500;margin:6px 0 16px;color:#e8ebef}
    .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:16px}
    .card{background:#161a20;border:0;border-top:3px solid #757575;border-radius:4px;
          padding:14px 16px;color:#dfe4ea;position:relative}
    .card.on{border-top-color:#2e9e44}
    .card .pname{font-size:17px;font-weight:600;margin-bottom:8px;color:#f2f4f7}
    .stat{display:flex;align-items:center;gap:8px;font-size:13px;margin:4px 0;color:#aab2bd}
    .actions{margin-top:12px;display:flex;flex-wrap:wrap;gap:6px}
    .actions button, .btn{border:0;border-radius:4px;padding:8px 14px;cursor:pointer;
           font-size:13px;background:#232830;color:#dfe4ea}
    .actions button:hover,.btn:hover{background:#2c323b}
    .actions button.primary{background:#FF8C28;color:#111318;font-weight:600}
    .actions button.warn{background:#5d1d1d;color:#ff9b9b}
    input[type=range]{width:150px;accent-color:#FF8C28;vertical-align:middle}
    .pill{position:absolute;top:10px;right:12px;font-size:12px;color:#8fd19e}
    .pill.off{color:#9aa2ad}
    .field{margin:10px 0}
    .field .lbl{font-size:12px;color:#8d97a3;margin-bottom:4px}
    select{padding:8px;border-radius:4px;background:#232830;color:#dfe4ea;border:1px solid #2c323b}
    .err{color:#ff8a8a;font-size:13px;white-space:pre-wrap}
    .about{font-size:13px;color:#aab2bd;line-height:1.6}
  `;

  // which entity unique_ids map to which row in a panel card
  const OBJ_IDS = ["screen", "brightness", "module_remote_desktop", "remote_desktop",
    "sensor_illuminance", "sensor_uptime", "sensor_ip_address", "sensor_firmware",
    "event_touch", "event_proximity", "event_light", "presence",
    "module_voice", "module_zigbee", "module_remote_desktop"];

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
      this._expanded = null;
    }

    setConfig() { /* not needed */ }

    set hass(hass) {
      this._hass = hass;
      if (!this._rendered) this._skeleton();
      this._rendered = true;
      this._schedule();
    }

    _schedule() {
      clearTimeout(this._t);
      this._t = setTimeout(() => this._refresh(), 150);
    }

    _skeleton() {
      this.shadowRoot.innerHTML = `<style>${CSS}</style>
        <div class="topbar"></div><div class="body">Loading…</div>`;
    }

    async _refresh() {
      const h = this._hass;
      if (!h) return;
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

    _entityMap(entry, dev) {
      const h = this._hass, out = {};
      if (this._registry && this._registry.length) {
        this._registry.forEach((r) => {
          if (r.config_entry_id === entry.entry_id) {
            const uid = (r.unique_id || "").replace("nexus_" + dev + "_", "");
            out[uid] = r.entity_id;
          }
        });
      }
      if (!Object.keys(out).length) {
        const prefix = "nexus." + String(dev).toLowerCase() + "_";
        Object.keys(h.states).forEach((eid) => {
          if (eid.startsWith(prefix)) out[eid.slice(prefix.length)] = eid;
        });
      }
      return out;
    }

    _topbar() {
      return `<div class="topbar">
        <img src="/nspanel-nexus/brand/icon.png" alt="NSPanel Nexus">
        <span class="name">NSPanel <b>Nexus</b></span>
        <div class="tabs">
          <div class="tab ${this._tab === "panels" ? "active" : ""}" data-t="panels">Panels</div>
          <div class="tab ${this._tab === "firmware" ? "active" : ""}" data-t="firmware">Firmware</div>
          <div class="tab ${this._tab === "settings" ? "active" : ""}" data-t="settings">Settings</div>
          <div class="tab ${this._tab === "about" ? "active" : ""}" data-t="about">About</div>
        </div>
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
      body.innerHTML = `<h2>Panels</h2><div class="grid" id="grid"></div>`;
      const grid = body.querySelector("#grid");
      this._entries.forEach((entry) => {
        const data = (entry && (entry.data || entry.raw_data)) || null;
        const dev = data ? (data.device_name || entry.title || "panel")
                         : (entry ? (entry.title || "").replace(/^NSPanel /, "") : "panel");
        const ents = this._entityMap(entry, dev);
        const st = (id) => (ents[id] ? h.states[ents[id]] : null);
        const probe = st("sensor_uptime") || st("sensor_ip_address");
        const online = probe ? probe.state !== "unavailable" : true;
        const screen = st("screen");
        const bright = st("brightness");
        const rdp = st("module_remote_desktop");
        const url = st("remote_desktop");
        const lux = st("sensor_illuminance");
        const ip = st("sensor_ip_address");

        const card = document.createElement("div");
        card.className = "card " + (online ? "on" : "off");
        card.innerHTML = `
          <div class="pname">${dev}</div>
          <span class="pill ${online ? "" : "off"}">${online ? "online" : "offline"}</span>
          ${ip ? `<div class="stat">⌂ ${ip.state}</div>` : ""}
          ${lux ? `<div class="stat">☀ ${lux.state} lx</div>` : ""}
          <div class="field"><div class="lbl">Brightness ${bright ? bright.state : ""}</div>
            <input type="range" min="0" max="255" value="${bright ? bright.state : 128}" data-br>
          </div>
          <div class="actions">
            <button class="primary" data-act="screen">${screen && screen.state === "on" ? "Sleep" : "Wake"}</button>
            <button data-act="rdp">${rdp && rdp.state === "on" ? "Remote desktop: on" : "Remote desktop: off"}</button>
            ${url && url.state ? `<button data-act="open">Open</button>` : ""}
            <button class="warn" data-act="reboot">Reboot</button>
          </div>`;

        card.querySelector("[data-br]").onchange = (e) => {
          if (ents["brightness"]) h.callService("number", "set_value",
            { entity_id: ents["brightness"], value: Number(e.target.value) });
        };
        card.querySelector("[data-act=screen]").onclick = () => {
          if (ents["screen"]) h.callService("switch",
            screen && screen.state === "on" ? "turn_off" : "turn_on", { entity_id: ents["screen"] });
          setTimeout(() => this._refresh(), 700);
        };
        card.querySelector("[data-act=rdp]").onclick = () => {
          if (ents["module_remote_desktop"]) h.callService("switch",
            rdp && rdp.state === "on" ? "turn_off" : "turn_on",
            { entity_id: ents["module_remote_desktop"] });
          setTimeout(() => this._refresh(), 700);
        };
        const open = card.querySelector("[data-act=open]");
        if (open) open.onclick = () => window.open(url.state, "_blank");
        card.querySelector("[data-act=reboot]").onclick = () =>
          h.callService("nspanel_nexus", "command", { device_name: dev, command: "reboot" });

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
        const cur = ents["sensor_firmware"] ? h.states[ents["sensor_firmware"]] : null;
        const card = document.createElement("div");
        card.className = "card on";
        card.innerHTML = `
          <div class="pname">${dev}</div>
          <div class="stat">Current version: ${cur ? cur.state : "unknown"}</div>
          <div class="actions"><button class="primary" data-act="check">Check for update</button></div>`;
        card.querySelector("[data-act=check]").onclick = () =>
          h.callService("nspanel_nexus", "command", { device_name: dev, command: "firmware:check" });
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
  }

  customElements.define("nspanel-nexus-manager", NexusPanelManager);
})();
