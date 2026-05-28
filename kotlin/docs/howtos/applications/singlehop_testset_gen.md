<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Single-Hop Testset Generation

Generate single-hop QA testsets from documents.

```kotlin
import ragas.testset.synthesizers.TestsetGenerator
import ragas.testset.transforms.defaultTransformsForDocuments

val docs: List<String> = TODO("Provide document strings")
val transforms = defaultTransformsForDocuments(docs)

val generator = TestsetGenerator()
val testset = generator.generateFromDocuments(documents = docs, testsetSize = 50, transforms = transforms)
```
