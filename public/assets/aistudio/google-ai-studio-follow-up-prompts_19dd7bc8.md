# Google AI Studio — Follow-up Prompts (Use One at a Time)

After each milestone, test before sending the next prompt.

## Milestone 2 — Script and SRT
```text
Implement Milestone 2 only: Bengali/English script editor and complete offline SRT workflow from srt-workflow.md. Add UTF-8 import, cue parser, validation, cue editor, offset adjustment, preview timing and UTF-8 export. Include unit tests with Bengali multi-line cues and malformed timestamps. Do not add cloud transcription yet. Build and test, then stop with a change report.
```

## Milestone 3 — Templates and JSON
```text
Implement Milestone 3 only. Load schemas/template-catalog.example.json using the supplied schema. Render the complete template gallery with category filters, observed/proposed origin labels, aspect-ratio support and real preview cards. Implement the ten observed-label designs and proposed styles as original artwork. No copied branding. Add JSON validation and tests. Stop after tests.
```

## Milestone 4 — Audio
```text
Implement Milestone 4 only: audio import, playback, recording with permission requested only after tapping Record, waveform/duration metadata where supported, and SRT/audio synchronization. Manual SRT remains fully offline. Do not claim automatic transcription unless a real configured provider is available.
```

## Milestone 5 — Scene editor and preview
```text
Implement Milestone 5 only: split scenes from SRT cues, reorder/edit scenes, add text/image/background layers, select supported motion presets, and create a deterministic preview. Ensure preview and export share the same layout model.
```

## Milestone 6 — Watermarks
```text
Implement Milestone 6 only using watermark-and-branding.md. Add independent User Watermark and Developer Watermark settings. Both are off by default. Support text and transparent PNG/WebP for the user watermark, position/size/opacity controls, safe margins, per-project settings and a global default. Show preview and overlap warnings. Add tests proving disabled means no watermark.
```

## Milestone 7 — Real MP4 export
```text
Implement Milestone 7 only. Integrate a real Android media rendering pipeline for MP4 H.264/AAC where supported. Composite scenes, subtitles, audio and enabled watermarks. Add progress, cancellation if supported, low-storage handling, retry and file verification. Do not use a placeholder export. Run a real-device test plan and clearly report anything not verified.
```

## Milestone 8 — Optional AI
```text
Implement optional AI integration only after the local app works. Use server-side secret handling for production. Add provider/model configuration, quota disclosure, user consent before uploads, timeout/retry and strict JSON schema validation. If no backend/key is configured, keep AI controls disabled with a clear explanation. Never embed production API keys in the APK.
```

## Final audit
```text
Audit the complete app against every acceptance criterion in PRD.md, watermark-and-branding.md, srt-workflow.md and testing.md. Run tests, inspect permissions, inspect APK/source for exposed secrets, verify all template IDs, test SRT Bengali round-trip, test both watermark toggles, and test an actual exported MP4 on a physical Android device. Produce a pass/fail table. Do not mark untested items as passed.
```
