package ragas.testset.transforms

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ragas.runtime.RunConfig
import ragas.testset.graph.KnowledgeGraph

sealed interface Transforms

data class SingleTransform(
    val transform: BaseGraphTransformation,
) : Transforms

data class Parallel(
    val transformations: List<BaseGraphTransformation>,
) : Transforms

data class SequenceTransforms(
    val transformations: List<Transforms>,
) : Transforms

suspend fun applyTransforms(
    kg: KnowledgeGraph,
    transforms: Transforms,
    runConfig: RunConfig = RunConfig(),
) {
    when (transforms) {
        is SingleTransform -> {
            executePlan(transforms.transform.generateExecutionPlan(kg), runConfig)
        }

        is Parallel -> {
            val plan = transforms.transformations.flatMap { transform -> transform.generateExecutionPlan(kg) }
            executePlan(plan, runConfig)
        }

        is SequenceTransforms -> {
            transforms.transformations.forEach { transform ->
                applyTransforms(kg, transform, runConfig)
            }
        }
    }
}

private suspend fun executePlan(
    plan: List<suspend () -> Unit>,
    runConfig: RunConfig,
) {
    if (plan.isEmpty()) {
        return
    }

    coroutineScope {
        val semaphore = Semaphore(runConfig.maxWorkers)
        plan
            .map { task ->
                async {
                    semaphore.withPermit {
                        task()
                    }
                }
            }.awaitAll()
    }
}
