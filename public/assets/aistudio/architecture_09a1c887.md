# Architecture

## Preferred target
Native Android Kotlin + Jetpack Compose when Google AI Studio Build Mode offers the Android target. A web/Capacitor implementation is a fallback only if the native target is unavailable or explicitly chosen.

## Layers
- UI: Compose screens and reusable components.
- Domain: project, scene, subtitle, template and watermark models.
- Data: Room/local database and app-private media files.
- Platform: Android document picker, microphone permission, media playback and rendering.
- AI: optional provider interface isolated from local editing.

## Suggested modules
`core/model`, `core/storage`, `feature/projects`, `feature/script`, `feature/audio`, `feature/subtitles`, `feature/templates`, `feature/editor`, `feature/watermark`, `feature/export`, `ai/provider`.

## Offline boundary
Offline: projects, script editing, manual SRT, template selection, local assets, watermark settings, preview where renderer supports it, local export where device encoder supports it.
Online/optional: Gemini, speech-to-text, image/video generation, cloud sync.

## Rendering
Use one normalized scene graph for preview and export. All positions should be stored in normalized coordinates (0..1) or a documented canvas coordinate system. Apply aspect ratio transforms once. Watermark layers are part of the render graph.
