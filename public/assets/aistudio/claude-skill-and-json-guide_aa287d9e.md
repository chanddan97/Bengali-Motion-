# Claude Skill and Template JSON — Step-by-Step Guide

## What is what?
- **Skill:** reusable instructions for Claude. The included Skill folder contains `SKILL.md`.
- **Prompt:** your request in the Claude chat.
- **JSON:** structured template data that the app can read. It is not the Skill and it is not executable code.
- **JSON Schema:** the rulebook used to validate that JSON.

## Files included
- `claude-skill/banglamotion-template-json-skill/SKILL.md`
- `schemas/template.schema.json`
- `schemas/template-catalog.example.json`

## Use Claude on a phone
1. Open Claude in your browser/app.
2. Attach `reference-template-catalog.md`, `template-json-spec.md` and `template.schema.json` if file upload is available.
3. Paste the prompt below.
4. Ask Claude to create a JSON file and also show the full JSON in the answer.
5. Validate the JSON against the schema. If Claude cannot create downloadable files on your current plan, copy the JSON code block into a text editor and save it as `template-catalog.json`.
6. Never paste generated JSON directly into the app without validation.

## Copy-ready Claude prompt
```text
Use the BanglaMotion Template JSON Skill and the attached template catalogue and JSON Schema.

Create a complete template-catalog.json for BanglaMotion Mobile Studio.
Include every template in the catalogue. Preserve the exact stable template IDs.
For the ten reference-observed labels, set origin to "observed-label-original-design".
For additional styles, set origin to "proposed-original".
Do not claim the original tutorial's exact colors, animations or layouts are known.
Create original, implementable visual specifications. Use only allowed layer and motion types from the schema.
Support 9:16, 1:1, 4:5 and 16:9 where practical.
Use Bengali-friendly typography and readable safe zones.
Do not include JavaScript, HTML, executable code, remote URLs or external copyrighted assets.
Return:
1. The complete valid JSON file.
2. A short summary of template count and any limitations.
3. A validation checklist.
Do not omit templates to save tokens. If output is too long, split into numbered JSON parts and then provide a merged final JSON file.
```

## Important Claude Skill availability
Claude's current help documentation says Skills can be used on Free, Pro, Max, Ultra, Team and Enterprise when Code execution and file creation are enabled. Custom Skill upload is available through Customize → Skills → Create skill → Upload a skill, subject to account capability. If the upload control is unavailable, use the Skill's `SKILL.md` as a normal instruction file by attaching it to a chat. See the official references in the conversation response.

## JSON handoff to Google AI Studio
Upload `template-catalog.json` and `template.schema.json` to the AI Studio project. Tell AI Studio to validate the JSON at app startup/build time and render cards from it. JSON controls layout parameters only; it must not contain code to execute.
