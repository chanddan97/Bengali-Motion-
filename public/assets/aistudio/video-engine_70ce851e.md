# Video Engine
- Use a normalized scene graph for text, shapes, backgrounds, images, subtitles and watermark layers.
- Preview and export must use identical layout/motion calculations.
- Use device-supported encoding; target H.264/AAC MP4 when available.
- Keep aspect ratio options: 9:16, 1:1, 4:5, 16:9.
- Export should handle storage, cancellation, process interruption and retry.
- Never rename a still image or placeholder to `.mp4`.
- Test real output on physical Android hardware.
