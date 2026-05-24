# Transforms

Transform orchestration for testset graph processing lives in `ragas.testset.transforms`.

## Core abstractions

- `BaseGraphTransformation`
- `Extractor`
- `Splitter`
- `RelationshipBuilder`

## Composition

- `SingleTransform`
- `Parallel`
- `SequenceTransforms`
- `applyTransforms(kg, transforms, runConfig)`

Execution is coroutine-based and concurrency-limited by `RunConfig.maxWorkers`.

## Built-in transforms

- Extractors:
  - `LlmBasedSummaryExtractor`
  - `RegexEntityExtractor`
  - `EmbeddingsTopicExtractor`
  - `HeadlinesExtractor`
  - `EmbeddingExtractor`
- Splitters:
  - `SentenceChunkSplitter`
  - `HeadlineSplitter`
- Relationship builders:
  - `AdjacentChunkRelationshipBuilder`
  - `SharedKeywordRelationshipBuilder`
  - `CosineSimilarityBuilder`
  - `SummaryCosineSimilarityBuilder`
  - `JaccardSimilarityBuilder`
  - `OverlapScoreBuilder`

## Default pipelines

- `defaultTransformsForDocuments(documents)`
- `defaultTransformsForPrechunked()`
