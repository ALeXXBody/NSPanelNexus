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
    .modal{background:#22252b;border-radius:8px;max-width:900px;width:100%;padding:14px 18px;color:#dfe4ea;box-shadow:0 4px 24px rgba(0,0,0,.5)}
    .modal.compact .field{margin:4px 0}
    .modal.compact input.txt,.modal.compact select{padding:4px 6px;font-size:12px}
    .modal.compact .lbl{font-size:11px;margin-bottom:1px}
    .modal.compact .pname{font-size:15px;margin-bottom:6px}
    .modal.compact .about{font-size:11px;margin:4px 0}
    .modal.compact .actions button,.modal.compact .btn{padding:4px 8px;font-size:12px}
    .modal.compact h3{font-size:16px;margin:0 0 6px}
    .modal.compact .stat{font-size:12px;margin:4px 0}
    .modal .grid{grid-template-columns:1fr;gap:12px} /* modal cards use full popup width */
    .setgrid{display:grid;grid-template-columns:1fr 1fr;gap:2px 14px}
    @media (max-width:700px){.setgrid{grid-template-columns:1fr}}
    .rdbox{background:#1a1e25;border:1px solid #2c323b;border-radius:8px;padding:10px 12px;margin:0 0 10px}
    .rdbox .rdhead{font-size:14px;font-weight:700;color:#fff;margin-bottom:6px;display:flex;align-items:center;gap:8px}
    .rdbox .rdpill{font-size:11px;font-weight:700;padding:2px 8px;border-radius:10px;background:#3a414d;color:#cfd6de}
    .rdbox .rdpill.on{background:#2e9e44;color:#fff}
    .rdbox .rdrow{display:flex;flex-wrap:wrap;gap:6px;align-items:center;margin-top:6px}
    .rdbox input.txt{flex:1;min-width:140px}
    .rdbox input.num{width:90px;flex:none}
    .rdbox .hint{font-size:11px;color:#8d97a3;margin-top:6px}
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
      return v || "v0.4.62";
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
        module_remote_desktop: ["switch", "remote_desktop", "module_remote_desktop"],
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
      return veil;
    }

    _closeModal() {
      if (this._modal) { try { this._modal.remove(); } catch (_) {} this._modal = null; }
    }

    async _rdUrl(entryId, path) {
      // Same-origin signed URL for the RD proxy (works over http/https, LAN/remote).
      // Cached for 9 of the 10 minutes the signature stays valid.
      this._rdUrls = this._rdUrls || {};
      const key = entryId + ":" + path;
      const hit = this._rdUrls[key];
      if (hit && hit.exp > Date.now()) return hit.url;
      const res = await this._hass.callWS({ type: "nspanel_nexus/rd_url", entry_id: entryId, path });
      this._rdUrls[key] = { url: res.url, exp: Date.now() + 9 * 60 * 1000 };
      return res.url;
    }

    _rdFailHtml(msg) {
      return this._panelSvg() + `<div class="about" style="margin:8px 0">${this._esc(msg)}</div>`;
    }

    _adoptRDStream(card, entryId, dev, devSafe) {
      // One persistent <img> per panel, re-adopted into each rebuilt card so the
      // 5-second sidebar refresh never restarts a playing stream.
      // Must run AFTER the card is attached (slot needs to be connected).
      const slot = card.querySelector('[data-rd="img"]');
      if (!slot || !slot.isConnected) return;
      this._rdImgs = this._rdImgs || {};
      // Drop nodes for panels that no longer exist.
      const known = {};
      (this._entries || []).forEach((e) => { known[e.entry_id] = true; });
      Object.keys(this._rdImgs).forEach((k) => { if (!known[k]) delete this._rdImgs[k]; });
      const rec = this._rdImgs[entryId];
      if (rec && !rec.dead && rec.url) {
        slot.replaceWith(rec.img);  // move the live node — stream continues
        return;
      }
      if (rec && !rec.dead) return;  // signer fetch already in flight; it will attach itself
      const img = document.createElement("img");
      img.className = "rdimg";
      img.alt = "live panel screen";
      img.title = "Click to open remote desktop";
      img.onclick = () => this._openRemote(dev, devSafe, entryId);
      const cur = this._rdImgs[entryId] = { img, dead: false, url: null };
      const failHere = (html) => {
        const w = slot.isConnected ? slot.closest('[data-rd="wrap"]') : null;
        if (w) w.innerHTML = html;
      };
      this._rdUrl(entryId, "stream").then((url) => {
        if (cur.dead) return;
        cur.url = url;
        img.onerror = () => {
          cur.dead = true;
          // The image failed: ask the proxy itself why (reason string is exact).
          fetch(url).then((r) => {
            try { r.body && r.body.cancel(); } catch (_) {}
            if (r.ok) {
              failHere(this._rdFailHtml("Live screen unavailable (stream broke unexpectedly — retrying)."));
              return;
            }
            r.text().then((t) => {
              const reason = String(t || "");
              let why;
              if (reason.includes("rd-token-missing")) why = "the RD token is not saved in Home Assistant yet — open Settings → Remote desktop, paste the token, press Save RD";
              else if (reason.includes("panel-ip-unknown")) why = "the panel IP address is unknown — is the panel online and linked?";
              else if (reason.includes("panel-unreachable")) why = "Home Assistant cannot reach " + this._esc(reason.split("@")[1] || "the panel") + " — check the panel is on the LAN";
              else if (reason.includes("panel-answered-401")) why = "the panel refused the token — retype it in Settings → Save RD";
              else if (reason.includes("panel-answered-")) why = "the panel answered HTTP " + this._esc(reason.split("panel-answered-")[1] || "?");
              else if (r.status === 403) why = "the signed URL was rejected — reload the page and retry";
              else why = "the proxy answered HTTP " + r.status;
              failHere(this._rdFailHtml("Live screen unavailable (" + why + ")."));
            }).catch(() => {
              failHere(this._rdFailHtml("Live screen unavailable (HTTP " + r.status + ")."));
            });
          }).catch(() => {
            failHere(this._rdFailHtml("Live screen unavailable (panel unreachable). If this persists, re-save the RD token in Settings."));
          });
        };
        img.src = url;
        // The card may have been rebuilt while signing; adopt wherever this entry's live slot is.
        try {
          const live = this.shadowRoot.querySelector(`[data-rd="img"][data-eid="${entryId}"]`);
          if (live && live.isConnected) live.replaceWith(img);
        } catch (_) {}
      }).catch((e) => {
        cur.dead = true;
        delete this._rdImgs[entryId];  // allow retry on next rebuild, not a stuck dead node
        failHere(this._rdFailHtml("Live screen unavailable (setup error: " + this._esc((e && e.message) || e) + ")."));
      });
    }
    _panelSvg() {
      return `<svg viewBox="0 0 240 240" width="100%" style="display:block;border-radius:6px;background:#0d1016" role="img" aria-label="NSPanel Pro (remote desktop off)">
        <!-- NSPanel Pro: 3.95 inch square 480x480, thin bezel, top status bar -->
        <rect x="8" y="8" width="224" height="224" rx="14" fill="#1a1d24" stroke="#2a2f3a" stroke-width="1.5"/>
        <rect x="16" y="16" width="208" height="208" rx="10" fill="#0f1218"/>
        <rect x="16" y="16" width="208" height="22" rx="10" fill="#161a22"/>
        <circle cx="36" cy="27" r="3" fill="#2e9e44"/>
        <text x="48" y="31" fill="#8d97a3" font-family="sans-serif" font-size="10">NSPanel Pro</text>
        <text x="204" y="31" text-anchor="end" fill="#8d97a3" font-family="sans-serif" font-size="10">--:--</text>
        <text x="120" y="118" text-anchor="middle" fill="#FF8C28" font-family="monospace" font-size="20" font-weight="bold">remote desktop</text>
        <text x="120" y="144" text-anchor="middle" fill="#8d97a3" font-family="monospace" font-size="14">off</text>
        <rect x="88" y="196" width="64" height="8" rx="4" fill="#2c323b"/>
      </svg>`;
    }

    _openRemote(dev, devSafe, entryId) {
      const veil = this._openModal(dev + " — remote desktop");
      if (!veil) return;
      const body = veil.querySelector(".mbody");
      if (!body) return;
      body.innerHTML = `
        <img class="rdimg" id="rd-live" alt="live panel screen" style="cursor:crosshair;touch-action:none">
        <div class="rdbtnrow">
          <button class="btn" data-k="3">HOME</button>
          <button class="btn" data-k="4">BACK</button>
          <button class="btn" data-k="26">POWER</button>
          <button class="btn" data-s="up">↑ up</button>
          <button class="btn" data-s="down">↓ down</button>
        </div>
        <div class="rdbtnrow">
          <input class="txt" id="rd-text" placeholder="type text…" style="flex:1;min-width:140px">
          <button class="btn" data-t="send">Type</button>
        </div>
        <div class="about" id="rd-log">connecting…</div>`;
      const img = body.querySelector("#rd-live");
      const log = body.querySelector("#rd-log");
      const say = (t) => { if (log && log.isConnected) log.textContent = t; };
      const post = (sub, bodyStr) => {
        // Same-origin POST with the HA bearer token (no mixed content, no per-tap signing).
        this._hass.fetchWithAuth(`/api/nspanel_nexus/rd/${entryId}/${sub}`, { method: "POST", body: bodyStr })
          .then((r) => { if (!r.ok) throw new Error("HTTP " + r.status); })
          .catch((e) => say("input failed: " + (e && e.message || e)));
      };
      const tap = (x, y) => { post("input/tap", "x=" + x + "&y=" + y); say("tap " + x + "," + y); };
      const swipe = (x1, y1, x2, y2, ms) => { post("input/swipe", "x1=" + x1 + "&y1=" + y1 + "&x2=" + x2 + "&y2=" + y2 + "&ms=" + ms); };
      this._rdUrl(entryId, "stream").then((url) => {
        if (!img.isConnected) return;
        img.onerror = () => say("Live screen unavailable (panel unreachable or RD token not saved — set it in Settings).");
        img.src = url;
        say("click to tap · drag to swipe");
      }).catch(() => say("Live screen unavailable — save the RD token in Settings first."));
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
      const txt = body.querySelector("#rd-text");
      const sendBtn = body.querySelector('[data-t="send"]');
      if (sendBtn) sendBtn.onclick = () => {
        const v = txt ? txt.value : "";
        if (!v) return;
        post("input/text", "text=" + encodeURIComponent(v));
        if (txt) txt.value = "";
        say("typed " + v.length + " chars");
      };
      body.querySelectorAll("[data-s]").forEach((b) => {
        b.onclick = () => {
          const dir = b.getAttribute("data-s") === "up" ? -1 : 1;
          if (dir > 0) swipe(240, 360, 240, 140, 300); else swipe(240, 140, 240, 360, 300);
        };
      });

    }

    _openInfoModal(dev, devSafe, ents, h) {
      const veil = this._openModal(dev + " — info");
      if (!veil) return;
      const body = veil.querySelector(".mbody");
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
      const veil = this._openModal(dev + " — settings");
      if (!veil) return;
      const modal = veil.querySelector(".modal");
      if (modal) modal.classList.add("compact");
      const body = veil.querySelector(".mbody");
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
        const rdpOn = rdp && rdp.state === "on";

        const card = document.createElement("div");
        card.className = "card " + (online ? "on" : "off");
        let rdHtml = "";
        if (!rdp || !rdpOn) {
          rdHtml = this._panelSvg() + `<div class="about" style="margin:8px 0">Remote desktop is off — turn it on in Settings.</div>`;
        } else {
          rdHtml = `<img class="rdimg" data-rd="img" data-eid="${entry.entry_id}" style="display:none" alt="">`;
        }
        card.innerHTML = `
          <div data-rd="wrap">${rdHtml}</div>
          <div class="actions" style="display:flex;border:0;padding:6px 0 0;justify-content:center">
            <button class="btn" data-act="info">Info</button>
            <button class="btn" data-act="settings">Settings</button>
            ${rdpOn ? `<button class="btn primary" data-act="remote">Remote</button>` : ""}
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
        on("[data-act=remote]", () => this._openRemote(dev, devSafe, entry.entry_id));
        on("[data-act=reboot]", () => {
          h.callService("nspanel_nexus", "command", { device_name: devSafe, command: "reboot" })
            .catch((e) => { console.error("reboot failed:", e); alert("Reboot command failed: " + (e && e.message || e)); }); });
        on("[data-act=remove]", () => {
          if (!confirm(`Unlink ${dev} from Home Assistant? The panel itself is not touched.`)) return;
          this._deleteEntry(entry.entry_id);
        });

        on("[data-act=info]", () => {
          const veil = this._openModal(dev + " — info");
          if (!veil) return;
          const body = veil.querySelector(".mbody");
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
          const veil = this._openModal(dev + " — settings");
          if (!veil) return;
          const modal = veil.querySelector(".modal");
          if (modal) modal.classList.add("compact");
          const body = veil.querySelector(".mbody");
          if (!body) return;
          this._renderSettingsForEntry(body, entry, dev, devSafe, ents, h);
        });

        grid.appendChild(card);
        if (rdpOn) this._adoptRDStream(card, entry.entry_id, dev, devSafe);
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
        ["exit_pin", "Exit PIN (optional)", "password"],
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
        const rdpSw = st("module_remote_desktop");
        const rdpIsOn = rdpSw && rdpSw.state === "on";
        const rdPortSt = st("cfg_remote_desktop_port");
        const rdPortVal = rdPortSt && rdPortSt.state != null && !isNaN(Number(rdPortSt.state)) ? Number(rdPortSt.state) : 8080;
        let html = `<div class="pname">${this._esc(dev)}</div>
          <div class="actions" style="display:flex;border:0;padding:4px 0 8px;gap:6px">
            <button class="btn" data-act="load">Load</button>
            <button class="btn primary" data-act="save">Save</button>
            <span class="about" data-act="msg" style="margin-left:auto"></span>
          </div>
          <div class="rdbox">
            <div class="rdhead">Remote desktop <span class="rdpill${rdpIsOn ? " on" : ""}" data-rd="pill">${rdpSw ? (rdpIsOn ? "ON" : "OFF") : "?"}</span></div>
            <div class="rdrow">
              <button class="btn${rdpIsOn ? "" : " primary"}" data-rd="toggle">${rdpIsOn ? "Turn off" : "Turn on"}</button>
              ${rdpIsOn ? `<button class="btn" data-rd="open">Open remote</button>` : ""}
            </div>
            <div class="rdrow">
              <input class="txt" data-rd="token" type="password" placeholder="Panel token (empty = unchanged)" title="Panel-side remote desktop token">
              <input class="txt num" data-rd="port" type="number" min="1" max="65535" step="1" value="${rdPortVal}" title="Remote desktop port">
              <button class="btn primary" data-rd="saverd">Save RD</button>
            </div>
            <div class="hint">Save RD stores the token on the panel + HA. Token field also in form below.</div>
          </div>
          <div class="setgrid">`;
        const fieldMap = Object.fromEntries(this._textFields);
        const sections = [
          ["Connection", ["mqtt_host","mqtt_user","mqtt_pass","topic_prefix","ha_topic_prefix","ha_url","ha_token"]],
          ["Dashboard & firmware", ["dashboard_url","remote_desktop_token","nexus_manifest_url"]],
          ["Zigbee & screens", ["zigbee_topic_base","screen_on_weekday_begin","screen_on_weekday_end","screen_on_weekend_begin","screen_on_weekend_end"]],
        ];
        sections.forEach(([title, keys]) => {
          html += `<div class="sec">${title}</div>`;
          keys.forEach((k) => {
            const info = fieldMap[k];
            const lbl = info ? info[1] : k;
            const typ = info ? info[2] : "text";
            const cur = st("cfg_" + k);
            const val = cur && cur.state != null && cur.state !== "unknown" && cur.state !== "unavailable" ? cur.state : "";
            const shortLbl = lbl.replace("MQTT ","").replace("Home Assistant ","HA ").replace("Remote desktop ","RD ").replace("Firmware manifest ","Manifest ").replace("Zigbee topic base","Zigbee base").replace("Screen on, ","").replace(" weekday "," wd ").replace(" weekend "," we ");
            html += `<div class="field"><div class="lbl">${shortLbl}</div>
              <input class="txt" data-cfg="${k}" data-kind="text" type="${typ}" value="${typ === "password" ? "" : this._esc(val)}" placeholder="${typ === "password" ? "•••" : ""}"></div>`;
          });
        });
        html += `<div class="sec">Ports & thresholds</div>`;
        this._numFields.forEach(([key, label, lo, hi, step]) => {
          const cur = st("cfg_" + key);
          const val = cur && cur.state != null && !isNaN(Number(cur.state)) ? Number(cur.state) : "";
          const shortLbl = label.replace("threshold (lx, 0 = off)","thresh").replace("when dark (-1 = off)","dark (-1=off)").replace("when bright (-1 = off)","bright (-1=off)").replace("Remote desktop ","RD ");
          html += `<div class="field"><div class="lbl">${shortLbl}</div>
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
        html += `</div><div class="field"><div class="lbl">Dark mode</div>
          <div style="display:flex;gap:6px;align-items:center">
            <button class="btn" data-dark="toggle">off</button>
          </div></div>
          <div class="field"><div class="lbl">Modules</div><div style="display:flex;flex-wrap:wrap;gap:6px">`;
        this._moduleToggles.forEach(([key, label]) => {
          const sw = st(key);
          const isOn = sw && sw.state === "on";
          html += `<button class="btn${isOn ? " primary" : ""}" data-mod="${key}">${label}: ${sw ? (isOn ? "on" : "off") : "?"}</button>`;
        });
        html += `</div><div class="about" style="font-size:10px;opacity:.5">MQTT has no switch — turning it off would silence the panel.</div></div>`;
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
              // Don't wipe a stored value with an empty string when we don't
              // actually know the current value (entity unavailable).
              if (el.value === "" && (curVal == null || curVal === "unknown" || curVal === "unavailable")) return;
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

        // ---- RD box wiring (on/off + token + port stay in Settings) ----
        {
          const pill = card.querySelector('[data-rd="pill"]');
          const tgl = card.querySelector('[data-rd="toggle"]');
          if (tgl) tgl.onclick = () => {
            const eid = ents["module_remote_desktop"];
            if (!eid) { msg("No remote-desktop switch entity yet — is the panel linked?"); return; }
            const cur = h.states[eid];
            const turnOn = !(cur && cur.state === "on");
            tgl.textContent = turnOn ? "Turn off" : "Turn on";
            tgl.classList.toggle("primary", !turnOn);
            if (pill) { pill.textContent = turnOn ? "ON" : "OFF"; pill.classList.toggle("on", turnOn); }
            h.callService("switch", turnOn ? "turn_on" : "turn_off", { entity_id: eid })
              .catch((e) => { console.error("RD toggle failed:", e); msg("RD toggle failed: " + (e && e.message || e)); });
            setTimeout(() => this._schedule(true), 1000);
          };
          const openBtn = card.querySelector('[data-rd="open"]');
          if (openBtn) openBtn.onclick = () => this._openRemote(dev, devSafe, entry.entry_id);
          const saveRd = card.querySelector('[data-rd="saverd"]');
          if (saveRd) saveRd.onclick = async () => {
            const patch = {};
            const tokEl = card.querySelector('[data-rd="token"]');
            const portEl = card.querySelector('[data-rd="port"]');
            if (tokEl && tokEl.value) patch["remote_desktop_token"] = tokEl.value;
            if (portEl && portEl.value !== "") {
              const curP = st("cfg_remote_desktop_port");
              if (!curP || String(Number(portEl.value)) !== String(curP.state)) patch["remote_desktop_port"] = Number(portEl.value);
            }
            if (!Object.keys(patch).length) { msg("RD: nothing changed."); return; }
            msg("Saving RD…");
            try {
              await h.callService("nspanel_nexus", "config_update", { device_name: devSafe, config: patch });
              if (tokEl) tokEl.value = "";
              flash("Saved RD: " + Object.keys(patch).join(", "));
              setTimeout(() => this._schedule(true), 1500);
              msg("");
            } catch (e) { msg("RD save failed: " + (e && e.message || e)); }
          };
        }

        {
          const darkBtn = card.querySelector('[data-dark="toggle"]');
          if (darkBtn) {
            const dm = st("cfg_dark_mode");
            const dmOn = dm && dm.state === "on";
            darkBtn.textContent = dmOn ? "on" : "off";
            darkBtn.classList.toggle("primary", dmOn);
            darkBtn.onclick = async () => {
              const turnOn = darkBtn.textContent.trim() !== "on";
              darkBtn.textContent = turnOn ? "on" : "off";
              darkBtn.classList.toggle("primary", turnOn);
              msg("Saving dark mode…");
              try {
                await h.callService("nspanel_nexus", "config_update",
                  { device_name: devSafe, config: { dark_mode: turnOn } });
                flash("Dark mode: " + (turnOn ? "on" : "off"));
                setTimeout(() => this._schedule(true), 1500);
                msg("");
              } catch (e) { msg("Dark toggle failed: " + (e && e.message || e)); }
            };
          }
        }

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
