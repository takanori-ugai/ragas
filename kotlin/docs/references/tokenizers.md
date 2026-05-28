# Tokenizers

Kotlin exposes a tokenizer API aligned with Python's `ragas.tokenizers` surface.

Available APIs:

- `ragas.BaseTokenizer`
- `ragas.TiktokenWrapper`
- `ragas.HuggingFaceTokenizer`
- `ragas.getDefaultTokenizer()`
- `ragas.getTokenizer(...)`
- `ragas.DEFAULT_TOKENIZER`

Behavior notes:

- `TiktokenWrapper` is backed by JTokkit and defaults to `o200k_base`.
- `DEFAULT_TOKENIZER` is lazily initialized (singleton), matching Python semantics.
- `HuggingFaceTokenizer` in Kotlin uses adapter functions (`fromFunctions`) and does not bundle model loading in core.
