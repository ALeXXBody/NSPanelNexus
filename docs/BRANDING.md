# NSPanel Nexus — Home Assistant branding

This is what makes HA pick the integration up **with its own name and logo**.

## What the integration already has (in `custom_components/nspanel_nexus/`)

| File | What HA uses it for |
|---|---|
| `manifest.json` | the integration's identity: `name`, `domain`, `codeowners`, `documentation`, `issue_tracker`, `integration_type: device`, `iot_class: local_push`, `config_flow: true` |
| `strings.json` | the Setup dialog title, field labels and descriptions HA displays |
| `logo.png` (kept in `branding/`) | rendered on the Marketplace page via the README |

HA shows the integration's **name** from the manifest everywhere (discovery
prompt, Devices & services, entity prefixes). That part is complete and works
as soon as the Marketplace/HACS copy is installed.

## The one thing that needs a PR: the icon and logo

Home Assistant does **not** read icons from the integration folder. The
frontend pulls them from the official **Brands** repository:

```
https://brands.home-assistant.io/_/nspanel_nexus/icon.png
https://brands.home-assistant.io/_/nspanel_nexus/logo.png
```

For a custom integration you submit those assets yourself — one PR, and every
HA install in the world shows the NSPanel Nexus logo next to the integration.

## Ready-made assets (exact sizes the Brands repo requires)

They are in `branding/ha-brands/`, already at the required dimensions:

| File | Size |
|---|---|
| `icon.png` | 256×256 |
| `icon@2x.png` | 512×512 |
| `logo.png` | 1024×1024 |
| `logo@2x.png` | 2048×2048 |
| `dark_logo.png` | 1024×1024 |
| `dark_logo@2x.png` | 2048×2048 |

## How to submit (5 minutes)

1. Fork https://github.com/home-assistant/brands
2. Create the folder: `brands/home-assistant.io/_/nspanel_nexus/`
3. Copy the six PNGs above into it (unchanged names)
4. Open a PR titled `Add nspanel_nexus brand`
5. Once merged, `https://brands.home-assistant.io/_/nspanel_nexus/logo.png`
   goes live and HA everywhere shows the branding automatically.

No change is needed in this repository for that — the assets are final.
