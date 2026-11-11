# NSPanel Nexus — Home Assistant branding

## Where HA takes the integration icon from (HA 2026.11+ Marketplace)

Custom integrations now ship their brand images **inside the integration**
in a `brand/` folder. HA's Marketplace reads them straight from the
integration's latest release, and serves them at:

```
https://brands.home-assistant.io/marketplace/nspanel_nexus/icon.png
```

An hourly sync function picks up whatever the latest release contains, so
after publishing a release with the images the Marketplace shows the icon.

## What ships (in `custom_components/nspanel_nexus/brand/`)

| File | Size | Notes |
|---|---|---|
| `icon.png` | 256×256 | light-theme tile |
| `icon@2x.png` | 512×512 | |
| `dark_icon.png` | 256×256 | dark-theme tile |
| `dark_icon@2x.png` | 512×512 | |
| `logo.png` | 512×256 | landscape, light theme |
| `logo@2x.png` | 1024×512 | |
| `dark_logo.png` | 512×256 | dark theme |
| `dark_logo@2x.png` | 1024×512 | |

All images meet the Brands spec: PNG, transparent/trimmed, icon 1:1 at
256/512 px, logo landscape with shortest side 256/512 px, no Home Assistant
branded artwork.

Regenerate with `python3 branding/make_ha_brand.py`.

## What HA reads from the integration folder for identity

- `manifest.json` — `name`, `domain`, `codeowners`, `documentation`,
  `issue_tracker`, `integration_type: device`, `iot_class`, `config_flow`
- `strings.json` — Setup dialog title, field labels, descriptions

## Legacy note

Older guidance said to submit images to the `home-assistant/brands` repo
(`custom_integrations/<domain>/`). Since HA 2026.3 the `brand/` folder inside
the integration is the supported path; the Brands repo remains the fallback
for HA versions before that.
