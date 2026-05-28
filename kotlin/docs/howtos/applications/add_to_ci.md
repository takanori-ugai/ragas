<!-- Adapted for ragas-kotlin on 2026-05-27 -->
# Add Evaluation to CI

Use Kotlin tests to enforce score thresholds in CI.

## JUnit Test Pattern

```kotlin
import kotlin.test.Test
import kotlin.test.assertTrue

class RagasCiTest {
    @Test
    fun amnestyE2e() {
        val result = runBaselineEvaluation()
        val answerCorrectness = (result["answer_correctness"] as Number).toDouble()
        assertTrue(answerCorrectness in 0.60..1.0, "answer_correctness out of range")
    }
}
```

## GitHub Actions

```yaml
name: ragas-ci
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - run: ./gradlew test --tests "*RagasCiTest"
```
