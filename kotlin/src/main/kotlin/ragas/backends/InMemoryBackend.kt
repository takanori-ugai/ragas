package ragas.backends

import java.util.concurrent.ConcurrentHashMap

class InMemoryBackend : BaseBackend {
    private val datasets = ConcurrentHashMap<String, List<Map<String, Any?>>>()
    private val experiments = ConcurrentHashMap<String, List<Map<String, Any?>>>()

    override fun loadDataset(name: String): List<Map<String, Any?>> =
        datasets[name]?.map { row -> row.toMap() }
            ?: throw java.io.FileNotFoundException("Dataset '$name' not found")

    override fun loadExperiment(name: String): List<Map<String, Any?>> =
        experiments[name]?.map { row -> row.toMap() }
            ?: throw java.io.FileNotFoundException("Experiment '$name' not found")

    override fun saveDataset(
        name: String,
        data: List<Map<String, Any?>>,
    ) {
        datasets[name] = data.map { row -> row.toMap() }
    }

    override fun saveExperiment(
        name: String,
        data: List<Map<String, Any?>>,
    ) {
        experiments[name] = data.map { row -> row.toMap() }
    }

    override fun listDatasets(): List<String> = datasets.keys.sorted()

    override fun listExperiments(): List<String> = experiments.keys.sorted()
}
