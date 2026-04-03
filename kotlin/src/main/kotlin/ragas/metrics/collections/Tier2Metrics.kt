package ragas.metrics.collections

import ragas.metrics.Metric

/**
 * Executes agentToolCallTier2Metrics.
 */
fun agentToolCallTier2Metrics(): List<Metric> =
    listOf(
        ToolCallAccuracyMetric(),
        ToolCallF1Metric(),
        AgentGoalAccuracyWithReferenceMetric(),
        AgentGoalAccuracyWithoutReferenceMetric(),
        AgentWorkflowCompletionMetric(),
    )
