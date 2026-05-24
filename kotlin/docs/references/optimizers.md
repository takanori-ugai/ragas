# Optimizers

Top-level accessors in package `ragas`:

- `geneticOptimizer()`
- `dspyOptimizer(cache: CacheBackend? = null)`
- `dspyOptimizer(runtimeConfig: DspyRuntimeConfig, cache: CacheBackend? = null)`

## Core types

- `Optimizer`
- `OptimizationDataset`
- `OptimizationExample`
- `OptimizerPrompt` (`Text`, `MultiModal`)
- `OptimizerOutcome`
- `DspyRuntimeConfig` (`numCandidates`, `maxBootstrappedDemos`, `maxLabeledDemos`, `initTemperature`, `auto`, `metricThreshold`, etc.)

## Implementations

- `GeneticOptimizer`
- `DspyOptimizer` (adapter-backed with heuristic fallback)

`DspyOptimizer` accepts runtime parity controls via constructor or top-level helper:

- `DspyOptimizer(runtimeConfig = DspyRuntimeConfig(...), cache = ...)`
- `dspyOptimizer(runtimeConfig = DspyRuntimeConfig(...), cache = ...)`

Both implementations expose prompt-object optimization through `optimizePrompts(...)`.
