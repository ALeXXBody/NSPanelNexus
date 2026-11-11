/**
 * NSPanel Nexus — HA sidebar manager.
 *
 * Lists every panel linked to the integration and lets you select and manage
 * each one: screen wake/sleep, brightness, remote desktop on/off, reboot,
 * and a link to the panel's browser remote desktop.
 */
class NexusPanelManager extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._hass = null;
    this._entries = [];
    this._registry = [];
    this._selected = null;
  }

  set hass(hass) {
    this._hass = hass;
    this._refresh();
  }

  setConfig(config) { this._config = config; }

  async _refresh() {
    if (!this._hass || this._loading) return;
    this._loading = true;
    try {
      const entries = await this._hass.callApi(
        "GET", "config/config_entries/entry");
      this._entries = (entries || []).filter(e => e.domain === "nspanel_nexus");
      const reg = await this._hass.callApi(
        "GET", "config/entity_registry/list");
      this._registry = reg || [];
      if (!this._selected && this._entries.length) this._selected = this._entries[0];
      this._render();
    } catch (e) { /* keep last view */ }
    this._loading = false;
  }

  _entitiesFor(entry) {
    const out = {};
    this._registry.forEach(r => {
      if (r.config_entry_id === entry.entry_id) {
        const uid = (r.unique_id || "").replace("nexus_" + entry.data.device_name + "_", "");
        out[uid] = r.entity_id;
      }
    });
    return out;
  }

  _render() {
    const h = this._hass, root = this.shadowRoot;
    root.innerHTML = `
      <style>
        :host{display:block;padding:16px;font-family:var(--paper-font-body1_-_font-family);color:var(--primary-text-color)}
        .row{display:flex;align-items:center;gap:8px}
        .card{background:var(--card-background-color);border-radius:12px;padding:14px;margin-bottom:12px;
              box-shadow:var(--ha-card-box-shadow,0 2px 6px rgba(0,0,0,.2))}
        .title{font-size:20px;font-weight:600;margin-bottom:4px}
        .sub{opacity:.7;font-size:13px;margin-bottom:10px}
        .pill{display:inline-block;padding:2px 10px;border-radius:12px;font-size:12px}
        .on{background:#2e7d32;color:#fff}.off{background:#757575;color:#fff}
        button{border:0;border-radius:8px;padding:8px 14px;margin:2px;cursor:pointer;
               background:var(--primary-color);color:var(--text-primary-color,var(--primary-text-color))}
        button.sec{background:var(--secondary-background-color);color:var(--primary-text-color)}
        input[type=range]{width:180px}
        select{padding:8px;border-radius:8px;background:var(--card-background-color);color:var(--primary-text-color);border:1px solid var(--divider-color)}
        a{color:var(--primary-color)}
        .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px}
      </style>
      <div class="row">
        <div style="flex:1">
          <div class="title">NSPanel Nexus</div>
          <div class="sub">Your wall panels, managed from here.</div>
        </div>
      </div>`;

    if (!this._entries.length) {
      root.innerHTML += `
        <div class="card">
          <div>No NSPanel Nexus panels are linked yet.</div>
          <div class="sub">Add one: Settings → Devices &amp; services → Add integration → NSPanel Nexus.</div>
        </div>`;
      return;
    }

    // panel selector
    const sel = document.createElement("select");
    this._entries.forEach(e => {
      const opt = document.createElement("option");
      opt.value = e.entry_id;
      opt.textContent = e.title || e.data.device_name;
      sel.appendChild(opt);
    });
    sel.value = this._selected.entry_id;
    sel.onchange = () => {
      this._selected = this._entries.find(e => e.entry_id === sel.value);
      this._render();
    };
    const selRow = document.createElement("div");
    selRow.className = "card";
    selRow.appendChild(sel);
    root.appendChild(selRow);

    const entry = this._selected;
    const dev = entry.data.device_name;
    const ents = this._entitiesFor(entry);
    const st = id => (ents[id] ? h.states[ents[id]] : null);
    const dot = cls => `<span class="pill ${cls}">${cls === "on" ? "online" : "offline"}</span>`;

    const availId = ents["sensor_uptime"] || ents["sensor_ip_address"];
    const online = availId ? (h.states[availId] && h.states[availId].state !== "unavailable") : true;

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
          <div class="sub">Screen</div>
          <button id="scr">${screen && screen.state === "on" ? "Sleep" : "Wake"}</button>
        </div>
        <div>
          <div class="sub">Brightness ${bright ? bright.state : "—"}</div>
          <input type="range" min="0" max="255" value="${bright ? bright.state : 128}" id="br">
        </div>
        <div>
          <div class="sub">Remote desktop ${rdp ? (rdp.state === "on" ? "on" : "off") : ""}</div>
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
      const svc = screen && screen.state === "on" ? "turn_off" : "turn_on";
      h.callService("switch", svc, { entity_id: ents["screen"] });
      setTimeout(() => this._refresh(), 600);
    };
    card.querySelector("#br").onchange = e => {
      h.callService("number", "set_value", {
        entity_id: ents["brightness"], value: Number(e.target.value),
      });
    };
    card.querySelector("#rdp").onclick = () => {
      const svc = rdp && rdp.state === "on" ? "turn_off" : "turn_on";
      h.callService("switch", svc, { entity_id: ents["module_remote_desktop"] });
      setTimeout(() => this._refresh(), 600);
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

// panel_custom expects an element named after the js_url "module"
window.customPanels = window.customPanels || {};
window.customPanels.set("nspanel-nexus-manager", NexusPanelManager);
