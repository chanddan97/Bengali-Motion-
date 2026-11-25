# Template JSON Specification

## Purpose
A template JSON file describes visual layout and supported motion settings. It is configuration data, not executable code. The app must never run JavaScript or code contained in a JSON file.

## Required top-level structure
- `schemaVersion`
- `catalogVersion`
- `templates` array
- Each template: stable `id`, `name`, `origin`, `category`, `description`, supported `aspectRatios`, `layers`, `motion`, `preview`, `accessibility`

## Validation
- Validate with `schemas/template.schema.json`.
- Reject unknown fields unless the schema version explicitly allows them.
- Clamp numeric values (opacity 0–1, duration >= 0, scale within safe limits).
- Only allow known layer types and motion types.
- Resolve assets from app-bundled or user-selected local assets; never fetch arbitrary URLs automatically.
- JSON is data only. Never eval/execute its content.
- Keep schema versioning and migrations.

## Example
See `schemas/template-catalog.example.json`.
