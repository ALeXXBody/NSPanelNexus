"""Same-origin proxy for the panel's remote-desktop HTTP server.

The panel serves its live screen and touch input over plain HTTP on the LAN
(`http://<panel-ip>:8080/`). A browser page loaded over https (e.g. the public
HA URL) refuses to load that stream (mixed content), so the sidebar would show
"Live screen unavailable" even with the right token.

This module lets Home Assistant itself serve those bytes: the sidebar uses
same-origin `/api/nspanel_nexus/rd/...` URLs, which work over http, https,
locally and remotely. Access is granted by short-lived HMAC-signed URLs
(`async_sign_path`, bound to the requesting user's refresh token) issued over
the authenticated websocket connection — the panel's RD token never leaves
the server.

The RD token/port live in the config entry options (saved from the sidebar
Settings "Save RD" button or any `config_update` call that includes them),
so the proxy keeps working across HA restarts.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import timedelta
from typing import Any

import aiohttp
import voluptuous as vol
from aiohttp import hdrs, web
from homeassistant.components import websocket_api
from homeassistant.components.http.auth import async_sign_path
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.http import KEY_AUTHENTICATED, HomeAssistantView

from .const import DOMAIN
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)

#: Panel RD sub-paths the proxy is allowed to reach.
ALLOWED_PATHS = frozenset(
    {
        "stream",
        "input/tap",
        "input/swipe",
        "input/key",
        "input/text",
    }
)

URL_TTL = timedelta(minutes=10)
_CHUNK = 65536
_CONNECT_TIMEOUT = 10
_STREAM_READ_TIMEOUT = 60
_ONESHOT_TIMEOUT = 15


def _entry_data(hass: HomeAssistant, entry_id: str) -> dict[str, Any] | None:
    data = hass.data.get(DOMAIN)
    if not data:
        return None
    return data.get(entry_id)


def _rd_target(hass: HomeAssistant, entry_id: str) -> tuple[PanelLink, str, int, str]:
    """Return (link, host, port, token); raise HTTPBadGateway with the exact reason."""
    entry_data = _entry_data(hass, entry_id)
    if not entry_data:
        raise web.HTTPNotFound(text="unknown panel entry")
    link: PanelLink = entry_data["link"]
    entry = hass.config_entries.async_get_entry(entry_id)
    opts = entry.options if entry else {}
    try:
        port = int(opts.get("remote_desktop_port", 8080))
    except (TypeError, ValueError):
        port = 8080
    token = str(opts.get("remote_desktop_token", "") or "")
    if not token:
        raise web.HTTPBadGateway(text="rd-token-missing")
    host: str | None = None
    try:
        st = hass.states.get(f"sensor.nspanel_{link.device_name.lower()}_ip_address")
        if st is not None and st.state not in ("unknown", "unavailable", ""):
            host = st.state
    except Exception:  # noqa: BLE001
        host = None
    if not host:
        raise web.HTTPBadGateway(text="panel-ip-unknown")
    return link, host, port, token


class NexusRDProxyView(HomeAssistantView):
    """Forward RD stream/input traffic to the panel, same-origin for browsers."""

    url = "/api/nspanel_nexus/rd/{entry_id}/{rd_path:.*}"
    name = "api:nspanel_nexus:rd"
    # Same pattern as the camera proxy views: the auth middleware marks valid
    # signed GETs (stream <img>) and bearer POSTs (fetchWithAuth input) as
    # authenticated; anything else is rejected in _proxy.
    requires_auth = False

    async def get(self, request: web.Request, entry_id: str, rd_path: str) -> web.StreamResponse:
        return await self._proxy(request, entry_id, rd_path)

    async def post(self, request: web.Request, entry_id: str, rd_path: str) -> web.StreamResponse:
        return await self._proxy(request, entry_id, rd_path)

    async def _proxy(self, request: web.Request, entry_id: str, rd_path: str) -> web.StreamResponse:
        hass: HomeAssistant = request.app["hass"]
        if not request.get(KEY_AUTHENTICATED, False):
            if hdrs.AUTHORIZATION in request.headers:
                raise web.HTTPUnauthorized
            raise web.HTTPForbidden
        if rd_path not in ALLOWED_PATHS:
            raise web.HTTPNotFound(text="unknown rd path")
        _link, host, port, token = _rd_target(hass, entry_id)
        panel_url = f"http://{host}:{port}/{rd_path}"
        is_stream = rd_path == "stream" and request.method == "GET"
        timeout = (
            aiohttp.ClientTimeout(
                total=None, connect=_CONNECT_TIMEOUT, sock_read=_STREAM_READ_TIMEOUT
            )
            if is_stream
            else aiohttp.ClientTimeout(total=_ONESHOT_TIMEOUT)
        )
        sess = aiohttp.ClientSession(timeout=timeout)
        streaming = False
        try:
            if request.method == "POST":
                body = await request.read()
                ctype = request.headers.get("Content-Type", "application/x-www-form-urlencoded")
                upstream = await sess.post(
                    panel_url,
                    data=body,
                    headers={"Content-Type": ctype, "X-Nexus-Token": token},
                )
                payload = await upstream.read()
                status, headers = upstream.status, upstream.headers
            else:
                upstream = await sess.get(panel_url, params={"token": token})
                status, headers = upstream.status, upstream.headers
                payload = None
            if status != 200:
                raise web.HTTPBadGateway(text=f"panel-answered-{status}")
            if is_stream:
                streaming = True  # _pump owns the session from here on
                return await self._pump(request, sess, upstream)
            return web.Response(
                status=200,
                body=payload,
                content_type=headers.get("Content-Type", "application/octet-stream").split(";")[
                    0
                ],
            )
        except (asyncio.TimeoutError, aiohttp.ClientError) as err:
            _LOGGER.debug("RD proxy to %s failed: %s", panel_url, err)
            raise web.HTTPBadGateway(text=f"panel-unreachable@{host}:{port}") from err
        finally:
            if not streaming:
                await sess.close()

    async def _pump(
        self,
        request: web.Request,
        sess: aiohttp.ClientSession,
        upstream: aiohttp.ClientResponse,
    ) -> web.StreamResponse:
        """Relay the panel's multipart MJPEG bytes, keeping its framing."""
        content_type = upstream.headers.get(
            "Content-Type", "multipart/x-mixed-replace; boundary=frame"
        )
        resp = web.StreamResponse(status=200, headers={"Content-Type": content_type})
        await resp.prepare(request)
        try:
            async for chunk in upstream.content.iter_chunked(_CHUNK):
                await resp.write(chunk)
        except (ConnectionResetError, asyncio.CancelledError, aiohttp.ClientError):
            pass
        finally:
            try:
                upstream.close()
            except Exception as err:  # noqa: BLE001
                _LOGGER.debug("upstream close failed: %s", err)
            await sess.close()
        return resp


@callback
def _ws_rd_url(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    entry_id = msg["entry_id"]
    path = msg["path"].lstrip("/")
    if path not in ALLOWED_PATHS:
        connection.send_error(msg["id"], "invalid_path", "unknown rd path")
        return
    if _entry_data(hass, entry_id) is None:
        connection.send_error(msg["id"], "no_entry", "unknown panel entry")
        return
    url = async_sign_path(hass, f"/api/nspanel_nexus/rd/{entry_id}/{path}", URL_TTL)
    connection.send_result(msg["id"], {"url": url})


_WS_SCHEMA = {
    vol.Required("type"): "nspanel_nexus/rd_url",
    vol.Required("entry_id"): str,
    vol.Required("path"): str,
}


def async_register(hass: HomeAssistant) -> None:
    """Register the proxy view + signer command (safe to call once)."""
    if hass.data.setdefault("_nspanel_nexus_rd_registered", False):
        return
    hass.data["_nspanel_nexus_rd_registered"] = True
    hass.http.register_view(NexusRDProxyView)
    websocket_api.async_register_command(
        hass,
        websocket_api.websocket_command(_WS_SCHEMA)(_ws_rd_url),
    )
    _LOGGER.debug("RD proxy registered")
