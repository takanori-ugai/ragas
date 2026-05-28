<!-- Adapted for ragas-kotlin on 2026-05-28 -->
# Langsmith
## Dataset and Tracing Visualisation

[Langsmith](https://docs.smith.langchain.com/) is a platform for building production-grade LLM applications from the LangChain team. It helps with tracing, debugging, and evaluating LLM applications.

The langsmith + ragas integrations offer 2 features:
1. View traces for ragas evaluator runs.
2. Evaluate LangSmith records with ragas metrics.

## Tracing ragas metrics

To set up LangSmith, make sure the following env vars are set (see the [LangSmith quick start](https://docs.smith.langchain.com/#quick-start)):

```bash
export LANGCHAIN_TRACING_V2=true
export LANGCHAIN_ENDPOINT=https://api.smith.langchain.com
export LANGCHAIN_API_KEY=<your-api-key>
export LANGCHAIN_PROJECT=<your-project>  # if not specified, defaults to "default"
```

Once LangSmith is set up, run evaluation as you normally would.

```kotlin
// @compile
import ragas.defaultMetrics
import ragas.integrations.LangsmithIntegration
import ragas.integrations.LangsmithRecord
import ragas.llms.BaseRagasLlm

val llm: BaseRagasLlm = TODO("Configure LLM")
val records =
    listOf(
        LangsmithRecord(
            input = "What is Ragas?",
            output = "Ragas is an evaluation framework for LLM applications.",
            retrievedContexts = listOf("Ragas helps evaluate and iterate on LLM systems."),
            reference = "Ragas is a framework for evaluating LLM apps.",
        ),
    )

val result = LangsmithIntegration.evaluateRecords(records = records, metrics = defaultMetrics(), llm = llm)
println(result.scores)
```

Then open your LangSmith project to inspect runs and traces.
