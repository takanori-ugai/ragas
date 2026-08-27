• Paper 2503.19878 evaluates three metrics only:

  1. Answer faithfulness
  2. Context recall
  3. Context precision

  Evidence in extracted paper text: /tmp/causalrag_2503.19878.txt:583, /tmp/causalrag_2503.19878.txt:585, /tmp/
  causalrag_2503.19878.txt:1111.

  Repository mapping:

  - Paper answer faithfulness → src/main/kotlin/ragas/metrics/defaults/FaithfulnessMetric.kt:17
  - Paper context recall → src/main/kotlin/ragas/metrics/defaults/ContextRecallMetric.kt:17
  - Paper context precision → use src/main/kotlin/ragas/metrics/collections/ContextPrecisionCollectionMetrics.kt:128 for
    best alignment with the paper’s reference-set definition (/tmp/causalrag_2503.19878.txt:309).

  Use for reproduction:

  - faithfulness
  - context_recall
  - context_precision_with_reference

  Do not use answer_relevancy from defaults if you want paper parity (defaults include an extra metric: src/main/kotlin/
  ragas/metrics/defaults/DefaultMetrics.kt:5).

  Also note: this repo’s metric outputs are 0..1, while the paper reports 0..100; multiply by 100 for comparable tables/
  plots.
