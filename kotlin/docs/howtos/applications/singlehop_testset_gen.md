<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Single-Hop Testset Generation

Generate single-hop QA testsets from documents.

```kotlin
import ragas.testset.graph.KnowledgeGraph
import ragas.testset.graph.Node
import ragas.testset.synthesizers.TestsetGenerator
import ragas.testset.transforms.defaultTransformsForDocuments

val documentNodes: List<Node> = TODO("Provide document nodes")
val docs: List<String> = TODO("Provide document strings")
val graph = KnowledgeGraph(nodes = documentNodes)
val transforms = defaultTransformsForDocuments(docs)

val generator = TestsetGenerator()
val testset = generator.generateFromDocuments(documents = docs, testsetSize = 50, transforms = transforms)
```
