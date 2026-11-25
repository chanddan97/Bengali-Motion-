# BanglaMotion Mobile Studio — Complete Updated Documentation

**Goal:** A Bengali-first Android video creation app that the owner can use personally from an Android phone, with a local-first workflow and no compulsory subscription.

## What this pack contains
- Full product, UI, architecture, security, testing and implementation documentation.
- Ten style names observed in the supplied primary reference video, with original implementation specifications.
- Additional original proposed styles, clearly separated from source-observed styles.
- User watermark and optional developer watermark specifications.
- Complete SRT import/edit/export workflow.
- Claude Skill (`claude-skill/banglamotion-template-json-skill/`) and a matching JSON schema/example.
- Google AI Studio Build Mode master prompt and a staged follow-up prompt sequence.

## Source boundary
The primary supplied mobile video was inspected visually at sampled intervals of about five seconds. The ten preset names listed as **observed** are labels visible in the sampled footage. The full design properties, transitions, exact colors, and complete gallery contents are not fully verifiable from those samples. Therefore, every visual implementation specification in this pack is an **original proposed design**, not a claim that the reference video used those exact values. The secondary video is used only as supplementary workflow context. Audio streams existed, but no reliable complete transcript was available.

## Start here (mobile-friendly order)
1. Read `docs/PRD.md` and `docs/reference-template-catalog.md`.
2. Read `docs/watermark-and-branding.md` and `docs/srt-workflow.md`.
3. Read `docs/architecture.md`, `docs/security.md`, and `docs/free-mobile-workflow.md`.
4. Open `docs/google-ai-studio-master-prompt.md`.
5. In Google AI Studio, select **Build** and choose **Android/native app** if the platform picker offers it. Upload the documentation ZIP or the relevant Markdown files, then paste the master prompt.
6. Build in milestones. Do not ask the AI to generate the entire app in one unverified step.
7. For template JSON, use the Claude Skill folder or the copy-ready prompt in `docs/claude-skill-and-json-guide.md`.

## Important reality check
- A local editor, manual SRT, local project saving, watermark compositing and some export paths can be designed to work offline.
- AI script generation, AI speech-to-text, image generation and cloud services may have free quotas, account requirements, network requirements or paid limits.
- Google AI Studio can generate native Android Kotlin/Jetpack Compose projects or web apps; this does not mean every generated project is automatically a tested, signed, installable release APK. Follow the build/export instructions and test on the actual phone.
- No document or prompt can guarantee a fully free unlimited AI video-generation service. This design avoids making paid AI mandatory.
- Do not expose API keys in the app.

## Documentation index
- `docs/PRD.md` — product scope and acceptance criteria
- `docs/reference-template-catalog.md` — observed and proposed styles
- `docs/template-json-spec.md` — template schema and validation
- `docs/watermark-and-branding.md` — user/developer watermark modes
- `docs/srt-workflow.md` — SRT import, edit, sync and export
- `docs/claude-skill-and-json-guide.md` — Claude Skill and JSON steps
- `docs/google-ai-studio-master-prompt.md` — main Build Mode prompt
- `docs/google-ai-studio-follow-up-prompts.md` — staged prompts
- `docs/free-mobile-workflow.md` — realistic zero/low-cost phone workflow
- Existing architecture, design, feature, audio, video, data, security, testing and implementation docs.

## Non-negotiable principles
- Local-first, autosave, offline-capable core.
- No compulsory watermark on exported videos.
- User watermark is controlled by the owner.
- Developer watermark is optional and off by default.
- No fake AI, fake progress, fake export or non-working buttons.
- Preview and export use the same scene and layer model.
- Keep API secrets out of APKs, source code, logs and client storage.
