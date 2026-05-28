# Python -> Kotlin Parity Test Matrix

Last updated: 2026-05-28

This matrix maps Python parity targets to Kotlin implementation modules and concrete Kotlin test evidence.

## Partial-Status Coverage Focus (WS9)

No remaining `Partial` WS9 entries in `Plan.md` parity map (as of 2026-05-24).

## Deep Investigation Notes (2026-05-28)

- Metric fixture and parity suites are green, including:
  `WS9CrossLanguagePartialGoldenTest`, `GoldenFixturesTest`,
  `*MetricParityTest`, and metric collection `*FixtureTest` coverage.
- Source-level audit gaps closed in follow-up implementation:
  - Added missing Python-style collections exports and aliases in Kotlin.
  - Added `SQLSemanticEquivalence` naming alias.
  - Set key LLM-backed collection metrics to strict-by-default behavior, with explicit compatibility fallback flags.

## Recently Promoted to Done (WS9)

| Python module | Kotlin target | Kotlin tests | Fixture evidence |
| --- | --- | --- | --- |
| `../src/ragas/metrics/collections/agent_goal_accuracy/metric.py` (workflow inference/completion intent) | `src/main/kotlin/ragas/metrics/collections/AgentWorkflowMetrics.kt` (`AgentWorkflowCompletionMetric`) | `src/test/kotlin/ragas/metrics/collections/AgentWorkflowLlmParityTest.kt`, `src/test/kotlin/ragas/metrics/collections/AgentWorkflowFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier2_agent_workflow_fixture.json` |
| `../src/ragas/metrics/collections/topic_adherence/metric.py` | `src/main/kotlin/ragas/metrics/collections/FactualAndTopicMetrics.kt` | `src/test/kotlin/ragas/TopicAdherenceMetricParityTest.kt`, `src/test/kotlin/ragas/metrics/collections/FactualAndTopicFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_factual_topic_fixture.json` |
| `../src/ragas/metrics/collections/quoted_spans/metric.py` | `src/main/kotlin/ragas/metrics/collections/QuotedAndChrfMetrics.kt` | `src/test/kotlin/ragas/metrics/collections/QuotedAndChrfFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_quoted_chrf_fixture.json` |
| `../src/ragas/metrics/collections/chrf_score/metric.py` | `src/main/kotlin/ragas/metrics/collections/QuotedAndChrfMetrics.kt` | `src/test/kotlin/ragas/metrics/collections/QuotedAndChrfFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_quoted_chrf_fixture.json` |
| `../src/ragas/metrics/collections/_bleu_score.py` | `src/main/kotlin/ragas/metrics/collections/BleuAndRougeMetrics.kt` | `src/test/kotlin/ragas/metrics/collections/BleuAndRougeFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_bleu_rouge_fixture.json` |
| `../src/ragas/metrics/collections/_rouge_score.py` | `src/main/kotlin/ragas/metrics/collections/BleuAndRougeMetrics.kt` | `src/test/kotlin/ragas/metrics/collections/BleuAndRougeFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_bleu_rouge_fixture.json` |
| `../src/ragas/metrics/collections/summary_score/metric.py` | `src/main/kotlin/ragas/metrics/collections/NoiseAndSummaryMetrics.kt` | `src/test/kotlin/ragas/metrics/collections/NoiseAndSummaryFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_noise_summary_fixture.json` |
| `../src/ragas/metrics/collections/noise_sensitivity/metric.py` | `src/main/kotlin/ragas/metrics/collections/NoiseAndSummaryMetrics.kt` | `src/test/kotlin/ragas/NoiseSensitivityMetricParityTest.kt`, `src/test/kotlin/ragas/metrics/collections/NoiseAndSummaryFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_noise_summary_fixture.json` |
| `../src/ragas/metrics/collections/_semantic_similarity.py` | `src/main/kotlin/ragas/metrics/collections/SemanticSimilarityMetric.kt` | `src/test/kotlin/ragas/SemanticSimilarityMetricParityTest.kt`, `src/test/kotlin/ragas/metrics/collections/SemanticSimilarityFixtureTest.kt` | `src/test/resources/fixtures/metrics/ws3_tier3_semantic_similarity_fixture.json` |

## WS4 Integration Evidence

| Python module | Kotlin target | Kotlin tests | Fixture evidence |
| --- | --- | --- | --- |
| `../src/ragas/integrations/langgraph.py` (`convert_to_ragas_messages`) | `src/main/kotlin/ragas/integrations/LangGraphIntegration.kt` (`evaluateRecords`, `convertToRagasMessages`, `toMultiTurnSample`) | `src/test/kotlin/ragas/integrations/LangGraphIntegrationTest.kt`, `src/test/kotlin/ragas/integrations/LangGraphMessageConversionTest.kt` | N/A (behavioral conversion and integration smoke tests) |
| `../src/ragas/integrations/langsmith.py` (`evaluate`, record mapping) | `src/main/kotlin/ragas/integrations/LangsmithIntegration.kt` (`toDataset`, `evaluateRecords`, `toMetricPayload`) | `src/test/kotlin/ragas/integrations/LangsmithIntegrationTest.kt` | N/A (integration smoke + mapping/trace contract assertions) |
| `../src/ragas/integrations/ag_ui.py` (`evaluate`, record mapping) | `src/main/kotlin/ragas/integrations/AgUiIntegration.kt` (`toDataset`, `evaluateRecords`, `toMetricPayload`) | `src/test/kotlin/ragas/integrations/AgUiIntegrationTest.kt` | N/A (integration smoke + mapping/trace contract assertions) |

## Notes

- `WS9CrossLanguagePartialGoldenTest` is retained as cross-language smoke coverage and uses score bands (`perfect`/`high`/`partial`/`low`) for stability.
- For strict numeric parity claims, rely on module-specific fixture suites where available and promote status from `Partial` to `Done` only after behavioral equivalence is demonstrated.
