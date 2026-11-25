---
name: banglamotion-template-json
description: Create, validate, repair and document safe template-catalog JSON for the BanglaMotion Mobile Studio Android app. Use when asked to generate visual templates, motion preset configurations, template JSON or schema-compliant catalogues.
---

# BanglaMotion Template JSON Skill

## Purpose
Generate safe, complete, schema-compliant JSON configuration for the BanglaMotion Mobile Studio template gallery.

## Required workflow
1. Read the supplied template catalogue, JSON specification and schema.
2. Preserve stable template IDs exactly.
3. Separate source-observed labels from proposed original additions using the `origin` field.
4. Do not claim that sampled reference footage proves exact colors, animations or hidden templates.
5. Produce original design descriptions and implementable layer/motion parameters.
6. Use only schema-allowed layer and motion types.
7. Support aspect ratios `9:16`, `1:1`, `4:5`, `16:9` where practical.
8. Keep text within safe zones and support Bengali Unicode.
9. Do not include JavaScript, HTML, shell commands, executable code, arbitrary remote URLs or copyrighted assets.
10. Validate every ID for uniqueness and every numeric value for allowed ranges.
11. If validation fails, repair the JSON and explain the correction.
12. Never silently omit a requested template. If output is too long, split into parts and then provide a merged complete file.

## JSON safety
JSON is data only. Never suggest using `eval`, dynamic code loading or executing strings from JSON. Asset references must be local bundled asset IDs or user-selected local media IDs.

## Output format
Return:
- `template-catalog.json`
- validation summary
- template count grouped by origin
- unsupported renderer features, if any
- a short app integration note

## Example user request
"Use the supplied catalogue and schema to create the full BanglaMotion template-catalog.json. Preserve IDs and distinguish observed labels from proposed original templates."
