# Watermark and Branding Specification — Option B + C

## Two independent watermark layers

### 1. User watermark (Option B)
- Off by default for a new project.
- User can choose text (e.g. their channel name) or import a transparent PNG/WebP logo.
- Controls: enabled, type, text/image asset, width/height, scale, opacity, anchor position, X/Y offset, safe margin, start/end time.
- Position presets: top-left, top-center, top-right, center, bottom-left, bottom-center, bottom-right.
- Optional drag-to-position canvas interaction.
- Project-level settings plus “Use as default for new projects”.

### 2. Developer watermark (Option C)
- App identity label/icon, e.g. “Made with BanglaMotion”.
- Off by default; user may enable or disable it.
- Independent position, size and opacity.
- Do not hardcode it as an unavoidable overlay.
- Do not misrepresent it as a third-party logo or trademark.

## Export behavior
1. Render the scene and subtitle layers.
2. Composite enabled watermark layers in the same coordinate system as the output frame.
3. Respect aspect-ratio scaling and safe margins.
4. Ensure preview and export share the same layout calculation.
5. If both watermarks overlap, show a warning and offer repositioning.
6. If an image watermark cannot be decoded, report the error; do not silently omit it.

## UI screens
- Watermark setup screen with User Watermark and Developer Watermark sections.
- Toggle, image picker/text field, position grid, opacity slider, size slider.
- Preview over the actual selected aspect ratio.
- Export summary lists enabled watermark layers.

## Data model
```json
{
  "watermarks": {
    "user": {
      "enabled": false,
      "kind": "text",
      "text": "",
      "assetId": null,
      "position": "bottom-right",
      "scale": 1.0,
      "opacity": 0.85,
      "marginPercent": 3,
      "startMs": 0,
      "endMs": null
    },
    "developer": {
      "enabled": false,
      "text": "Made with BanglaMotion",
      "position": "top-right",
      "scale": 0.7,
      "opacity": 0.55,
      "marginPercent": 3,
      "startMs": 0,
      "endMs": null
    }
  }
}
```

## Acceptance tests
- Both off → no watermark in exported file.
- User only → user watermark only.
- Developer only → developer watermark only.
- Both on → both render and overlap warning works.
- Change position/opacity/size → preview and export match.
- Transparent logo keeps alpha transparency.
