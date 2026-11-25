# Free / Low-Cost Mobile-Only Workflow

## What can be free
- Writing scripts manually.
- Local project saving.
- Manual SRT creation/import/edit/export.
- Applying text/image watermarks locally.
- Selecting bundled templates and editing scenes.
- Offline preview if the renderer supports the selected effects.
- Local export if a compatible Android rendering implementation is included and tested.

## What may not be unlimited/free
- Gemini or other AI calls: free quotas and model availability can change.
- Speech-to-text: provider-dependent; some services charge by audio duration.
- Image/video generation: often quota-limited or paid.
- Claude Custom Skill upload and file creation: account capability/plan dependent.
- App signing, Play Store publishing or some cloud build services may have separate requirements.

## Recommended zero-mandatory-cost design
1. Make all core editing features local-first.
2. Bundle a small set of original template assets and fonts with appropriate licenses.
3. Make AI features optional; provide manual alternatives.
4. Do not require a server for manual SRT, watermark or basic editing.
5. If Gemini is used, use a secure server-side proxy for production. Never place a production key in the APK.
6. During personal testing, follow Google AI Studio's current key and quota guidance; do not treat a free tier as unlimited or permanent.

## Android build reality
Google AI Studio Build Mode currently supports web apps and native Android projects using Kotlin/Jetpack Compose. Select the Android app target when available. The generated source still needs build, install, permission and real-device testing. Do not assume a source preview is already a release APK.

## Mobile-only steps
- Keep source/docs in Google Drive or local file storage.
- Upload docs to AI Studio from the Android browser if supported.
- Build one milestone at a time.
- Download source/build output where available.
- If a local Android build is needed, use a supported cloud build workflow or Android development environment; phone-only builds may be slower and constrained.
- Test import, recording, storage permission, SRT and export on the actual phone.
