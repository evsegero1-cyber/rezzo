package ru.rezzo.academy.scene3d

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Исходные параметры материала одного примитива (нужны, чтобы менять прозрачность и подсветку и затем возвращать как было). */
data class PrimMaterial(val color: FloatArray, val emissive: FloatArray, val blend: Boolean) {
    companion object {
        val DEFAULT = PrimMaterial(floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(0f, 0f, 0f), false)
    }
}

/**
 * Лёгкий разбор JSON-части GLB/glTF: имена узлов и материалы их примитивов.
 * Геометрию загружает Filament (gltfio), здесь — только метаданные.
 */
class GltfInfo private constructor(
    val nodeMaterials: Map<String, List<PrimMaterial>>,
    val nodeNames: List<String>,
    val isPlaceholder: Boolean,
) {
    companion object {
        private const val MAGIC = 0x46546C67 // "glTF"
        private const val CHUNK_JSON = 0x4E4F534A

        fun parse(bytes: ByteArray): GltfInfo {
            val json = extractJson(bytes) ?: return GltfInfo(emptyMap(), emptyList(), false)
            val root = JSONObject(json)
            val materials = root.optJSONArray("materials")
            val meshes = root.optJSONArray("meshes")
            val nodes = root.optJSONArray("nodes")
            val result = mutableMapOf<String, List<PrimMaterial>>()
            val names = mutableListOf<String>()

            fun material(index: Int): PrimMaterial {
                val m = materials?.optJSONObject(index) ?: return PrimMaterial.DEFAULT
                val pbr = m.optJSONObject("pbrMetallicRoughness")
                val bc = pbr?.optJSONArray("baseColorFactor")
                val color = FloatArray(4) { i -> bc?.optDouble(i, 1.0)?.toFloat() ?: 1f }
                val ef = m.optJSONArray("emissiveFactor")
                val emissive = FloatArray(3) { i -> ef?.optDouble(i, 0.0)?.toFloat() ?: 0f }
                val blend = m.optString("alphaMode", "OPAQUE").equals("BLEND", ignoreCase = true)
                return PrimMaterial(color, emissive, blend)
            }

            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    val n = nodes.optJSONObject(i) ?: continue
                    val name = n.optString("name")
                    if (name.isBlank()) continue
                    names += name
                    if (!n.has("mesh") || meshes == null) continue
                    val mesh = meshes.optJSONObject(n.optInt("mesh")) ?: continue
                    val prims = mesh.optJSONArray("primitives") ?: continue
                    result[name] = (0 until prims.length()).map { p ->
                        val prim = prims.optJSONObject(p)
                        if (prim != null && prim.has("material")) material(prim.optInt("material")) else PrimMaterial.DEFAULT
                    }
                }
            }
            val placeholder = root.optJSONObject("extras")?.optBoolean("placeholder", false) ?: false
            return GltfInfo(result, names, placeholder)
        }

        private fun extractJson(bytes: ByteArray): String? {
            if (bytes.size < 20) return null
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.getInt(0) != MAGIC) {
                // .gltf (текстовый JSON)
                return runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
            }
            val chunkLength = bb.getInt(12)
            val chunkType = bb.getInt(16)
            if (chunkType != CHUNK_JSON || chunkLength <= 0 || 20 + chunkLength > bytes.size) return null
            return String(bytes, 20, chunkLength, Charsets.UTF_8)
        }
    }
}
