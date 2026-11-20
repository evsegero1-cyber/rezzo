package ru.rezzo.academy.scene3d

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Привязка узла 3D-модели к детали из базы знаний. */
data class PartBinding(val node: String, val partId: String, val side: String?)

data class MovableDef(
    val id: String,
    val node: String,
    /** rotate | translate */
    val type: String,
    val axis: FloatArray,
    val angleDeg: Float,
    val offset: FloatArray,
    val group: String,
    val durationSec: Float,
)

data class LightDef(
    val id: String,
    val nodes: List<String>,
    val color: FloatArray,
    val intensity: Float,
    val blink: Boolean,
    val perNodeColor: Map<String, FloatArray>,
)

data class HotspotDef(val partId: String, val node: String, val pos: FloatArray)

data class DisassemblyNode(val node: String, val offset: FloatArray)

data class DisassemblyStep(val title: String, val nodes: List<DisassemblyNode>)

data class OrbitPose(val target: FloatArray, val yaw: Float, val pitch: Float, val distance: Float)

data class FirstPersonPose(val eye: FloatArray, val yaw: Float, val pitch: Float)

/**
 * Описание сцены (assets/scene/car_scene.json). Вся «логика» модели — какие узлы
 * открываются, какие светят, какие детали чему соответствуют — задаётся здесь,
 * а не в коде. Поэтому заглушку можно заменить настоящей моделью без
 * переписывания приложения: достаточно сохранить имена узлов.
 */
data class SceneConfig(
    val model: String,
    val placeholder: Boolean,
    val placeholderNote: String,
    val parts: List<PartBinding>,
    val movables: List<MovableDef>,
    val lights: List<LightDef>,
    val groups: Map<String, List<String>>,
    val disassembly: List<DisassemblyStep>,
    val hotspots: List<HotspotDef>,
    val home: OrbitPose,
    val interior: FirstPersonPose,
) {
    private val bindingByNode = parts.associateBy { it.node }

    fun binding(node: String): PartBinding? = bindingByNode[node]
    fun nodesForPart(partId: String): List<String> = parts.filter { it.partId == partId }.map { it.node }
    fun hasPart(partId: String): Boolean = parts.any { it.partId == partId }
    fun movableForNode(node: String): MovableDef? = movables.firstOrNull { it.node == node }

    companion object {
        const val DEFAULT_PATH = "scene/car_scene.json"

        fun load(context: Context, path: String = DEFAULT_PATH): SceneConfig {
            val text = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(JSONObject(text))
        }

        fun parse(root: JSONObject): SceneConfig {
            val parts = root.optJSONArray("parts").objects().map {
                PartBinding(it.getString("node"), it.getString("partId"), it.optString("side").takeIf { s -> s.isNotBlank() })
            }
            val movables = root.optJSONArray("movables").objects().map {
                MovableDef(
                    id = it.getString("id"),
                    node = it.getString("node"),
                    type = it.optString("type", "rotate"),
                    axis = it.optJSONArray("axis").floats(3, 0f),
                    angleDeg = it.optDouble("angle", 0.0).toFloat(),
                    offset = it.optJSONArray("offset").floats(3, 0f),
                    group = it.optString("group"),
                    durationSec = it.optDouble("duration", 1.0).toFloat().coerceAtLeast(0.1f),
                )
            }
            val lights = root.optJSONArray("lights").objects().map { o ->
                val per = mutableMapOf<String, FloatArray>()
                o.optJSONObject("perNodeColor")?.let { pc ->
                    pc.keys().forEach { k -> per[k] = pc.optJSONArray(k).floats(3, 1f) }
                }
                LightDef(
                    id = o.getString("id"),
                    nodes = o.optJSONArray("nodes").strings(),
                    color = o.optJSONArray("color").floats(3, 1f),
                    intensity = o.optDouble("intensity", 1.0).toFloat(),
                    blink = o.optBoolean("blink", false),
                    perNodeColor = per,
                )
            }
            val groups = mutableMapOf<String, List<String>>()
            root.optJSONObject("groups")?.let { g -> g.keys().forEach { k -> groups[k] = g.optJSONArray(k).strings() } }
            val disassembly = root.optJSONArray("disassembly").objects().map { st ->
                DisassemblyStep(
                    title = st.optString("title"),
                    nodes = st.optJSONArray("nodes").objects().map { DisassemblyNode(it.getString("node"), it.optJSONArray("offset").floats(3, 0f)) },
                )
            }
            val hotspots = root.optJSONArray("hotspots").objects().map {
                HotspotDef(it.getString("partId"), it.getString("node"), it.optJSONArray("pos").floats(3, 0f))
            }
            val home = root.optJSONObject("home")
            val interior = root.optJSONObject("interior")
            return SceneConfig(
                model = root.optString("model", "models/rezzo_placeholder.glb"),
                placeholder = root.optBoolean("placeholder", false),
                placeholderNote = root.optString("placeholderNote"),
                parts = parts,
                movables = movables,
                lights = lights,
                groups = groups,
                disassembly = disassembly,
                hotspots = hotspots,
                home = OrbitPose(
                    target = home?.optJSONArray("target").floats(3, 0f),
                    yaw = home?.optDouble("yaw", 35.0)?.toFloat() ?: 35f,
                    pitch = home?.optDouble("pitch", 15.0)?.toFloat() ?: 15f,
                    distance = home?.optDouble("distance", 7.0)?.toFloat() ?: 7f,
                ),
                interior = FirstPersonPose(
                    eye = interior?.optJSONArray("eye")?.floats(3, 0f) ?: floatArrayOf(0f, 1.2f, 0.37f),
                    yaw = interior?.optDouble("yaw", 0.0)?.toFloat() ?: 0f,
                    pitch = interior?.optDouble("pitch", -10.0)?.toFloat() ?: -10f,
                ),
            )
        }

        private fun JSONArray?.objects(): List<JSONObject> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optJSONObject(it) }
        }

        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
        }

        private fun JSONArray?.floats(n: Int, default: Float): FloatArray {
            val out = FloatArray(n) { default }
            if (this == null) return out
            for (i in 0 until minOf(n, length())) out[i] = optDouble(i, default.toDouble()).toFloat()
            return out
        }
    }
}
