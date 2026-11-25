# SRT Subtitle Workflow

## Import
1. User taps **Import SRT**.
2. Open Android document picker for `.srt`.
3. Read as UTF-8; if invalid encoding is detected, offer a safe fallback and preview.
4. Parse blocks into cue index, start time, end time and text.
5. Show import summary: cue count, duration, warnings and errors.
6. Do not overwrite existing subtitles until user confirms.

## Supported timestamp form
`HH:MM:SS,mmm --> HH:MM:SS,mmm`

Example:
```srt
1
00:00:01,000 --> 00:00:03,500
আজকের গল্পে তোমাকে স্বাগতম।

2
00:00:03,600 --> 00:00:06,000
চলো, শুরু করা যাক।
```

## Editing
- Edit cue text, start and end time.
- Add, duplicate, split, merge and delete cues.
- Shift all cues by a positive or negative offset.
- Search/replace subtitle text.
- Preview cue against audio and scene.
- Warn if cues overlap, have negative duration or exceed media duration.
- Preserve line breaks unless user chooses reflow.
- Support Bengali Unicode and mixed Bengali/English.

## Export
- Export UTF-8 `.srt`.
- Re-number cues sequentially.
- Use comma milliseconds.
- Ensure start < end.
- Offer a validation report before saving.

## Auto SRT
- Manual import/edit is P0 and works offline.
- Audio transcription is P1 and must identify the configured provider/model, language support, quota and network requirement.
- Do not claim Gemini can transcribe a file unless the selected model/API endpoint has been verified for that exact audio workflow.
- Provide manual correction because speech recognition can mishear names, dialects and Bengali punctuation.

## Acceptance tests
- Bengali text round-trips without mojibake.
- Multi-line cues parse correctly.
- Invalid timecodes are highlighted.
- Cue ordering is preserved or safely re-numbered.
- Exported SRT opens in a second subtitle editor.
