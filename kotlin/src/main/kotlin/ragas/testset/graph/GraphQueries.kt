package ragas.testset.graph

fun getChildNodes(
    node: Node,
    graph: KnowledgeGraph,
    level: Int = 1,
): List<Node> {
    val children = mutableListOf<Node>()
    val visited = mutableSetOf<String>()

    fun dfs(
        currentNode: Node,
        currentLevel: Int,
    ) {
        if (currentLevel > level) {
            return
        }

        graph.relationships
            .filter { rel -> rel.type == "child" && rel.sourceId == currentNode.id }
            .forEach { rel ->
                val child = graph.getNodeById(rel.targetId) ?: return@forEach
                if (!visited.add(child.id)) {
                    return@forEach
                }
                children += child
                dfs(child, currentLevel + 1)
            }
    }

    dfs(node, 1)
    return children
}

fun getParentNodes(
    node: Node,
    graph: KnowledgeGraph,
    level: Int = 1,
): List<Node> {
    val parents = mutableListOf<Node>()
    val visited = mutableSetOf<String>()

    fun dfs(
        currentNode: Node,
        currentLevel: Int,
    ) {
        if (currentLevel > level) {
            return
        }

        graph.relationships
            .filter { rel -> rel.type == "child" && rel.targetId == currentNode.id }
            .forEach { rel ->
                val parent = graph.getNodeById(rel.sourceId) ?: return@forEach
                if (!visited.add(parent.id)) {
                    return@forEach
                }
                parents += parent
                dfs(parent, currentLevel + 1)
            }
    }

    dfs(node, 1)
    return parents
}
