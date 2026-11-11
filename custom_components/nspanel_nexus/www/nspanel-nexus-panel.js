/**
 * NSPanel Nexus — HA sidebar manager.
 *
 * Lists every panel linked to the integration and lets you select and manage
 * each one: screen wake/sleep, brightness, remote desktop on/off, reboot,
 * and a link to the panel's browser remote desktop.
 *
 * Renders immediately (never a blank page) and surfaces errors in the panel.
 */
(function () {
  const CSS = `
    :host{display:block;padding:16px;color:var(--primary-text-color)}
    .title{font-size:20px;font-weight:600;margin-bottom:4px}
    .sub{opacity:.7;font-size:13px;margin-bottom:10px}
    .card{background:var(--card-background-color);border-radius:12px;padding:14px;
          margin-bottom:12px;box-shadow:var(--ha-card-box-shadow,0 2px 6px rgba(0,0,0,.2))}
    .row{display:flex;align-items:center;gap:8px}
    .pill{display:inline-block;padding:2px 10px;border-radius:12px;font-size:12px}
    .on{background:#2e7d32;color:#fff}.off{background:#757575;color:#fff}
    button{border:0;border-radius:8px;padding:8px 14px;margin:2px;cursor:pointer;
           background:var(--primary-color);color:var(--text-primary-color,var(--primary-text-color))}
    button.sec{background:var(--secondary-background-color);color:var(--primary-text-color)}
    input[type=range]{width:180px}
    select{padding:8px;border-radius:8px;background:var(--card-background-color);
           color:var(--primary-text-color);border:1px solid var(--divider-color)}
    .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px}
    .err{color:var(--error-color,#ff5252);font-size:13px;white-space:pre-wrap}
  `;

  class NexusPanelManager extends HTMLElement {
    constructor() {
      super();
      this.attachShadow({ mode: "open" });
      this._hass = null;
      this._selected = null;
      this._error = null;
      this._entries = null;   // null = not loaded yet
      this._registry = null;
      this._loading = false;
    }

    setConfig() { /* not needed */ }

    set hass(hass) {
      this._hass = hass;
      if (!this._rendered) {           // never a blank page
        this._skeleton();
        this._rendered = true;
      }
      this._schedule();
    }

    _schedule() {
      clearTimeout(this._t);
      this._t = setTimeout(() => this._refresh(), 150);
    }

    _skeleton() {
      this.shadowRoot.innerHTML = `<style>${CSS}</style>
        <div class="title">NSPanel Nexus</div>
        <div class="sub">Loading panels…</div>`;
    }

    async _refresh() {
      const h = this._hass;
      if (!h) return;
      try {
        const entries = await h.callApi("GET", "config/config_entries/entry");
        this._entries = (entries || []).filter((e) => e.domain === "nspanel_nexus");
        try {
          this._registry = await h.callApi("GET", "config/entity_registry/list");
        } catch (_) {
          this._registry = [];   // fall back to hass.states below
        }
        this._error = null;
      } catch (e) {
        this._error = "Could not read panels: " + (e && e.message ? e.message : e);
      }
      this._render();
    }

    _entityMap(entry) {
      const h = this._hass, dev = entry.data.device_name, out = {};
      if (this._registry && this._registry.length) {
        this._registry.forEach((r) => {
          if (r.config_entry_id === entry.entry_id) {
            const uid = (r.unique_id || "").replace("nexus_" + dev + "_", "");
            out[uid] = r.entity_id;
          }
        });
      } else {
        // fallback: match entities by prefix from the state machine
        const prefix = "nexus." + dev.toLowerCase() + "_";
        Object.keys(h.states).forEach((eid) => {
          if (eid.startsWith(prefix)) out[eid.slice(prefix.length)] = eid;
        });
      }
      return out;
    }

    _render() {
      const h = this._hass, root = this.shadowRoot;
      root.innerHTML = `<style>${CSS}</style>
        <div class="title">NSPanel Nexus</div>
        <div class="sub">Your wall panels, managed from here.</div>`;

      if (this._error) {
        root.innerHTML += `<div class="card"><div class="err">${this._error}</div>
          <div class="sub">Fix the integration setup, then reload this page.</div></div>`;
        return;
      }
      if (!this._entries || !this._entries.length) {
        root.innerHTML += `<div class="card">
          <div>No NSPanel Nexus panels are linked yet.</div>
          <div class="sub">Add one: Settings → Devices &amp; services → Add integration → NSPanel Nexus.</div>
        </div>`;
        return;
      }
      if (!this._selected || !this._entries.find((e) => e.entry_id === this._selected)) {
        this._selected = this._entries[0];
      }

      // selector
      const sel = document.createElement("select");
      this._entries.forEach((e) => {
        const opt = document.createElement("option");
        opt.value = e.entry_id;
        opt.textContent = e.title || e.data.device_name;
        sel.appendChild(opt);
      });
      sel.value = this._selected.entry_id;
      sel.onchange = () => { this._selected = this._entries.find((e) => e.entry_id === sel.value); this._render(); };
      const selRow = document.createElement("div");
      selRow.className = "card";
      selRow.appendChild(sel);
      root.appendChild(selRow);

      const entry = this._selected;
      const dev = entry.data.device_name;
      const ents = this._entityMap(entry);
      const st = (id) => (ents[id] ? h.states[ents[id]] : null);
      const dot = (cls) => `<span class="pill ${cls}">${cls === "on" ? "online" : "offline"}</span>`;

      const probe = st("sensor_uptime") || st("sensor_ip_address");
      const online = probe ? probe.state !== "unavailable" : true;
      const screen = st("screen");
      const bright = st("brightness");
      const rdp = st("module_remote_desktop");
      const url = st("remote_desktop");

      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        <div class="row">
          <div style="flex:1;font-size:17px;font-weight:600">${entry.title || dev}</div>
          ${dot(online ? "on" : "off")}
        </div>
        <div class="sub">MQTT topic: ${entry.data.topic_prefix}/${dev}</div>
        <div class="grid">
          <div>
            <div class="sub">Screen ${screen ? (screen.state === "on" ? "— on" : "— off") : ""}</div>
            <button id="scr">${screen && screen.state === "on" ? "Sleep" : "Wake"}</button>
          </div>
          <div>
            <div class="sub">Brightness ${bright ? bright.state : "—"}</div>
            <input type="range" min="0" max="255" value="${bright ? bright.state : 128}" id="br">
          </div>
          <div>
            <div class="sub">Remote desktop ${rdp ? (rdp.state === "on" ? "— on" : "— off") : ""}</div>
            <button id="rdp">${rdp && rdp.state === "on" ? "Turn off" : "Turn on"}</button>
            ${url && url.state ? `<button class="sec" id="open">Open</button>` : ""}
          </div>
          <div>
            <div class="sub">Maintenance</div>
            <button class="sec" id="reboot">Reboot</button>
            <button class="sec" id="fw">Check firmware</button>
          </div>
        </div>`;

      card.querySelector("#scr").onclick = () => {
        h.callService("switch", screen && screen.state === "on" ? "turn_off" : "turn_on",
          { entity_id: ents["screen"] });
        setTimeout(() => this._refresh(), 700);
      };
      card.querySelector("#br").onchange = (e) => {
        h.callService("number", "set_value", { entity_id: ents["brightness"], value: Number(e.target.value) });
      };
      card.querySelector("#rdp").onclick = () => {
        h.callService("switch", rdp && rdp.state === "on" ? "turn_off" : "turn_on",
          { entity_id: ents["module_remote_desktop"] });
        setTimeout(() => this._refresh(), 700);
      };
      const open = card.querySelector("#open");
      if (open) open.onclick = () => window.open(url.state, "_blank");
      card.querySelector("#reboot").onclick = () =>
        h.callService("nspanel_nexus", "command", { device_name: dev, command: "reboot" });
      card.querySelector("#fw").onclick = () =>
        h.callService("nspanel_nexus", "command", { device_name: dev, command: "firmware:check" });

      root.appendChild(card);
    }
  }

  customElements.define("nspanel-nexus-manager", NexusPanelManager);
})();
