# ragas-kotlin Public API (v0)

This file defines the intended stable entrypoints under package `ragas`.

## Top-level API

- `ragas.VERSION`
- `ragas.evaluate(...)`
- `ragas.aevaluate(...)`
- `ragas.defaultMetrics()`
- `ragas.withCache(llm, cache)`
- `ragas.withCache(embedding, cache)`
- `ragas.backendRegistry()`

## Stability Goal

These entrypoints are intended to remain stable while internals evolve.
When adding modules, prefer exposing user-facing access through these facades.

## Notes

- `evaluate/aevaluate` delegate to the internal evaluation engine.
- If metrics are omitted, default single-turn metrics are used.
- Integrations and advanced optimizers remain partial and may throw explicit unsupported errors.
