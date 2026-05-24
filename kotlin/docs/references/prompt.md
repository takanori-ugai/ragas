# Prompt Subsystem

## Main components

- `SimplePrompt`
- `TypedPrompt` / `BasePrompt`
- `ImageTextTypedPrompt`
- `DynamicFewShotPrompt`
- `PromptCollection`
- `PromptContentPart`
- `MultiModalContentNormalizer` / `MultiModalInputPolicy`

## Capabilities

- Prompt formatting with instruction/examples/input-output framing
- Structured JSON output parsing and retry correction
- Single and batch structured generation (`generate`, `generateMultiple`)
- Prompt save/load with stable hashing
- Optional example selection using embedding similarity (`DynamicFewShotPrompt`)
- Multimodal prompt content (`Text`, `ImageDataUri`, `ImageUrl`)
- Secure multimodal item normalization from untrusted strings (data URI, URL with SSRF/size checks, optional local-file allow-list)

## Security note

- URL safety checks are best-effort in the current `HttpURLConnection` implementation; DNS rebinding TOCTOU remains a known limitation until a connection-time IP-pinning client path is introduced.
