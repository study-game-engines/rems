package me.anno.mesh.blender.blocks

import me.anno.mesh.blender.BlenderFile
import org.apache.logging.log4j.LogManager
import java.io.IOException

class BlockTable(val file: BlenderFile, val blocks: List<Block>?, offHeapStructs: IntArray?) {

    companion object {
        private const val HEAP_BASE = 4096L
        private val LOGGER = LogManager.getLogger(BlockTable::class)
    }

    constructor(file: BlenderFile) : this(file, null, null)

    val sorted = blocks?.sortedBy { it.address }?.toMutableList() ?: ArrayList()
    val blockList = blocks?.toMutableList() ?: ArrayList()

    var offHeapAreas: HashMap<Int, BlockTable>? = null

    init {
        if (offHeapStructs != null) {
            offHeapAreas = HashMap()
            for (index in offHeapStructs.indices) {
                offHeapAreas!![offHeapStructs[index]] = BlockTable(file)
            }
            val sorted = sorted
            var i = 0
            while (i < sorted.size) {
                val b = sorted[i]
                for (index in offHeapStructs.indices) {
                    val sdnaIndex = offHeapStructs[index]
                    if (b.sdnaIndex == sdnaIndex) {
                        offHeapAreas!![sdnaIndex]!!.add(b)
                        sorted.removeAt(i)
                        i--
                    }
                }
                i++
            }
            // checkBlockOverlaps()
        }
        if (sorted.isNotEmpty()) {
            val first = sorted.first()
            if (first.address <= HEAP_BASE) throw IllegalStateException()
        }
    }

    fun binarySearch(address: Long): Int {
        return sorted.binarySearch { it.address.compareTo(address) }
    }

    fun binarySearch(block: Block): Int {
        return binarySearch(block.address)
    }

    fun add(block: Block) {
        val index = binarySearch(block)
        if (index >= 0) throw IOException("Block cannot be defined twice")
        sorted.add(-index - 1, block)
    }

    fun getBlockAt(positionInFile: Int): Block {
        var index = blockList.binarySearch { it.positionInFile.compareTo(positionInFile) }
        if (index < 0) index = -index - 2 // -2, because we want the block before
        if (index < 0) index = 0
        return blockList[index]
    }

    fun getAddressAt(positionInFile: Int): Long {
        val block = getBlockAt(positionInFile)
        return block.address + (positionInFile - block.positionInFile)
    }

    fun findBlock(file: BlenderFile, address: Long): Block? {
        if (address == 0L) return null
        var i = binarySearch(address)
        if (i >= 0) {
            return sorted[i]
        } else {
            // if the address lies between two block start addresses, then
            // -i-1 is the pos of the block with start address larger
            // than address. But we need the block with a start address
            // lower than address. Thus, -i-2
            i = -i - 2
            if (i >= 0) {
                val b = sorted[i]
                val endAddress = b.address + b.size
                if (address < endAddress) {
                    // block found
                    return b
                } else {
                    LOGGER.debug(
                        "Block out of bounds: $address >= ${b.address} + ${b.size} " +
                                "(type: ${file.structs[b.sdnaIndex].type.name}), " +
                                "next block: ${sorted.getOrNull(i + 1)?.address}"
                    )
                }
            }
            return null
        }
    }
}