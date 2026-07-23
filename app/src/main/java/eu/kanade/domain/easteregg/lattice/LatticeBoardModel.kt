package eu.kanade.domain.easteregg.lattice

import org.json.JSONObject

/** Шесть направлений flat-top гекса в аксиальных координатах (q, r). */
val DIRS = arrayOf(
    intArrayOf(1, 0),
    intArrayOf(1, -1),
    intArrayOf(0, -1),
    intArrayOf(-1, 0),
    intArrayOf(-1, 1),
    intArrayOf(0, 1),
)

enum class LatticeSegment(val connectors: List<Int>, val symmetry: Int) {
    LINE(listOf(0, 3), 3),
    CURVE(listOf(0, 2), 6),
    ELBOW(listOf(0, 1), 6),
    TEE(listOf(0, 2, 4), 2),
}

data class LatticePort(val q: Int, val r: Int, val dir: Int)

class LatticeCell(
    val q: Int,
    val r: Int,
    val segment: LatticeSegment,
    var rotation: Int,
) {
    fun connectorDirs(): Set<Int> = segment.connectors.map { (it + rotation).mod(6) }.toSet()
    fun effectiveRotation(): Int = rotation.mod(segment.symmetry)
}

data class LatticeCircuit(
    val closed: Boolean,
    val coreReached: Boolean,
    val reached: Set<Pair<Int, Int>>,
    val stubs: Set<Pair<Int, Int>>,
)

class LatticeBoard(
    val radius: Int,
    val port: LatticePort,
    val cells: Map<Pair<Int, Int>, LatticeCell>,
) {

    fun rotate(q: Int, r: Int) {
        cells[q to r]?.let { it.rotation = (it.rotation + 1).mod(6) }
    }

    /** Сигнал идёт от порта; цепь замкнута, когда ядро достигнуто, обрывов нет и все ячейки запитаны. */
    fun evaluate(): LatticeCircuit {
        val portKey = port.q to port.r
        val portCell = cells[portKey]
        if (portCell == null || port.dir !in portCell.connectorDirs()) {
            return LatticeCircuit(closed = false, coreReached = false, reached = emptySet(), stubs = emptySet())
        }
        val reached = mutableSetOf(portKey)
        val stubs = mutableSetOf<Pair<Int, Int>>()
        var coreReached = false
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(portKey)
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            val cell = cells.getValue(key)
            for (d in cell.connectorDirs()) {
                if (key == portKey && d == port.dir) continue // внешний ввод порта
                val nq = key.first + DIRS[d][0]
                val nr = key.second + DIRS[d][1]
                if (nq == 0 && nr == 0) {
                    coreReached = true
                    continue
                }
                val nKey = nq to nr
                val neighbor = cells[nKey]
                if (neighbor != null && (d + 3).mod(6) in neighbor.connectorDirs()) {
                    if (reached.add(nKey)) queue.add(nKey)
                } else {
                    stubs.add(key)
                }
            }
        }
        val closed = coreReached && stubs.isEmpty() && reached.size == cells.size
        return LatticeCircuit(closed, coreReached, reached, stubs)
    }

    fun serializeRotations(): String = cells.values
        .sortedWith(compareBy({ it.q }, { it.r }))
        .joinToString(";") { "${it.q},${it.r}:${it.rotation}" }

    fun restoreRotations(serialized: String) {
        serialized.split(";").forEach { token ->
            val head = token.substringBefore(":", "")
            val rot = token.substringAfter(":", "").toIntOrNull() ?: return@forEach
            val q = head.substringBefore(",").toIntOrNull() ?: return@forEach
            val r = head.substringAfter(",").toIntOrNull() ?: return@forEach
            cells[q to r]?.rotation = rot.mod(6)
        }
    }

    fun effectiveRotations(): List<Triple<Int, Int, Int>> = cells.values
        .sortedWith(compareBy({ it.q }, { it.r }))
        .map { Triple(it.q, it.r, it.effectiveRotation()) }

    fun topologyCanonical(): String = LatticeCanonical.topology(effectiveRotations())

    companion object {
        fun fromJson(raw: String): LatticeBoard? = try {
            val obj = JSONObject(raw)
            val port = obj.getJSONObject("port")
            val arr = obj.getJSONArray("cells")
            val cells = buildMap {
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val cell = LatticeCell(
                        q = c.getInt("q"),
                        r = c.getInt("r"),
                        segment = LatticeSegment.valueOf(c.getString("segment")),
                        rotation = c.getInt("rotation"),
                    )
                    put(cell.q to cell.r, cell)
                }
            }
            LatticeBoard(
                radius = obj.getInt("radius"),
                port = LatticePort(port.getInt("q"), port.getInt("r"), port.getInt("dir")),
                cells = cells,
            )
        } catch (e: Exception) {
            null
        }
    }
}
