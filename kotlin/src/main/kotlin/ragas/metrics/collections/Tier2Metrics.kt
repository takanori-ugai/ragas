package ragas.metrics.collections

import ragas.metrics.Metric

fun agentToolCallTier2Metrics(): List<Metric> =
    listOf(
        ToolCallAccuracyMetric(),
        ToolCallF1Metric(),
        AgentGoalAccuracyWithReferenceMetric(),
        AgentGoalAccuracyWithoutReferenceMetric(),
        AgentWorkflowCompletionMetric(),
    )
