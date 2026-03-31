package ragas.backends

interface BaseBackend {
    fun loadDataset(name: String): List<Map<String, Any?>>

    fun loadExperiment(name: String): List<Map<String, Any?>>

    fun saveDataset(
        name: String,
        data: List<Map<String, Any?>>,
    )

    fun saveExperiment(
        name: String,
        data: List<Map<String, Any?>>,
    )

    fun listDatasets(): List<String>

    fun listExperiments(): List<String>
}
