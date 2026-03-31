package ragas

import ragas.backends.BackendRegistry
import ragas.backends.InMemoryBackend
import ragas.backends.LocalCsvBackend
import ragas.backends.LocalJsonlBackend
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackendsTest {
    @Test
    fun inMemoryBackendStoresDatasetsAndExperiments() {
        val backend = InMemoryBackend()
        val datasetRows = listOf(mapOf("id" to 1, "text" to "hello"))
        val experimentRows = listOf(mapOf("metric" to "faithfulness", "score" to 0.8))

        backend.saveDataset("d1", datasetRows)
        backend.saveExperiment("e1", experimentRows)

        assertEquals(datasetRows, backend.loadDataset("d1"))
        assertEquals(experimentRows, backend.loadExperiment("e1"))
        assertEquals(listOf("d1"), backend.listDatasets())
        assertEquals(listOf("e1"), backend.listExperiments())
    }

    @Test
    fun localCsvBackendRoundTripsSimpleRows() {
        val root = createTempDirectory("ragas-csv-test").toFile()
        val backend = LocalCsvBackend(root.absolutePath)

        val rows =
            listOf(
                mapOf("id" to 1, "text" to "hello,world", "flag" to true),
                mapOf("id" to 2, "text" to "plain", "flag" to false),
            )
        backend.saveDataset("dataset1", rows)

        val loaded = backend.loadDataset("dataset1")
        assertEquals(rows.size, loaded.size)
        assertEquals("1", loaded[0]["id"])
        assertEquals("hello,world", loaded[0]["text"])
        assertEquals("true", loaded[0]["flag"])
        assertEquals("2", loaded[1]["id"])
        assertEquals("plain", loaded[1]["text"])
        assertEquals("false", loaded[1]["flag"])
        assertEquals(listOf("dataset1"), backend.listDatasets())
    }

    @Test
    fun localCsvBackendRoundTripsMultilineQuotedFields() {
        val root = createTempDirectory("ragas-csv-test").toFile()
        val backend = LocalCsvBackend(root.absolutePath)

        val rows =
            listOf(
                mapOf("id" to 1, "text" to "first line\nsecond line"),
                mapOf("id" to 2, "text" to "plain"),
            )
        backend.saveDataset("dataset_multiline", rows)

        val loaded = backend.loadDataset("dataset_multiline")
        assertEquals("first line\nsecond line", loaded[0]["text"])
        assertEquals("plain", loaded[1]["text"])
    }

    @Test
    fun localCsvBackendRejectsTraversalStyleNames() {
        val root = createTempDirectory("ragas-csv-test").toFile()
        val backend = LocalCsvBackend(root.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                backend.saveDataset("../evil", listOf(mapOf("id" to 1)))
            }
        assertTrue(error.message.orEmpty().contains("Invalid name '../evil'"))
    }

    @Test
    fun localJsonlBackendRoundTripsNestedRows() {
        val root = createTempDirectory("ragas-jsonl-test").toFile()
        val backend = LocalJsonlBackend(root.absolutePath)

        val rows =
            listOf(
                mapOf(
                    "id" to 1,
                    "name" to "alice",
                    "tags" to listOf("a", "b"),
                    "meta" to mapOf("x" to 1, "ok" to true),
                ),
            )
        backend.saveExperiment("exp1", rows)

        val loaded = backend.loadExperiment("exp1")
        assertEquals(1L, loaded[0]["id"])
        assertEquals("alice", loaded[0]["name"])
        assertEquals(listOf("a", "b"), loaded[0]["tags"])
        @Suppress("UNCHECKED_CAST")
        val meta = loaded[0]["meta"] as Map<String, Any?>
        assertEquals(1L, meta["x"])
        assertEquals(true, meta["ok"])
        assertEquals(listOf("exp1"), backend.listExperiments())
    }

    @Test
    fun localJsonlBackendRejectsUnsupportedValueTypes() {
        val root = createTempDirectory("ragas-jsonl-test").toFile()
        val backend = LocalJsonlBackend(root.absolutePath)

        val rows = listOf(mapOf("id" to 1, "unsupported" to Any()))
        assertFailsWith<IllegalArgumentException> {
            backend.saveExperiment("exp-bad", rows)
        }
    }

    @Test
    fun localJsonlBackendRejectsTraversalStyleNames() {
        val root = createTempDirectory("ragas-jsonl-test").toFile()
        val backend = LocalJsonlBackend(root.absolutePath)

        val error =
            assertFailsWith<IllegalArgumentException> {
                backend.saveExperiment("../evil", listOf(mapOf("id" to 1)))
            }
        assertTrue(error.message.orEmpty().contains("Invalid name '../evil'"))
    }

    @Test
    fun backendRegistrySupportsAliases() {
        val registry = BackendRegistry()
        registry.register("custom", ::InMemoryBackend, aliasList = listOf("c"))

        assertTrue(registry.contains("custom"))
        assertTrue(registry.contains("c"))
        val backend = registry.create("c")
        assertTrue(backend is InMemoryBackend)
    }

    @Test
    fun backendRegistryRejectsAliasCollisionsAcrossDifferentBackends() {
        val registry = BackendRegistry()
        registry.register("first", ::InMemoryBackend, aliasList = listOf("shared"))

        val error =
            assertFailsWith<IllegalArgumentException> {
                registry.register("second", ::InMemoryBackend, aliasList = listOf("shared"))
            }
        assertTrue(error.message.orEmpty().contains("Alias 'shared' is already registered"))
    }

    @Test
    fun missingDatasetRaisesFileNotFound() {
        val backend = InMemoryBackend()
        assertFailsWith<java.io.FileNotFoundException> {
            backend.loadDataset("missing")
        }
    }
}
