# Optional AI Integration
- AI is not required for core app use.
- Use Gemini only through a configured provider interface.
- Check current model capabilities, pricing and quotas before enabling a feature.
- Audio input and transcription support must be verified for the selected model/API endpoint.
- Request strict JSON for scene plans and validate against schema.
- Never execute AI-generated code.
- Never embed production keys in APK; use a backend proxy.
- Provide manual fallbacks for script, SRT and scene creation.
