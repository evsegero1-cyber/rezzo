package ru.rezzo.academy.core.kb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.rezzo.academy.core.model.CarProfile
import ru.rezzo.academy.core.model.CarSystem
import ru.rezzo.academy.core.model.Confidence
import ru.rezzo.academy.core.model.EngineOption
import ru.rezzo.academy.core.model.Part
import ru.rezzo.academy.core.model.PowerOption
import ru.rezzo.academy.core.model.Source
import ru.rezzo.academy.core.model.SpecItem

/**
 * База знаний. Хранится в assets/kb в виде JSON, поэтому новые детали и
 * характеристики добавляются без изменения кода:
 *  - kb/systems.json  — системы автомобиля
 *  - kb/parts/ — детали (по json-файлу на систему)
 *  - kb/vehicle.json  — характеристики Rezzo 1.6, варианты двигателя и мощности, источники
 */
class KnowledgeBase private constructor(
    val systems: List<CarSystem>,
    val parts: List<Part>,
    val specs: List<SpecItem>,
    val engineOptions: List<EngineOption>,
    val powerOptions: List<PowerOption>,
    val sources: Map<String, Source>,
    val loadErrors: List<String>,
) {
    private val partById = parts.associateBy { it.id }

    fun part(id: String): Part? = partById[id]
    fun system(id: String): CarSystem? = systems.firstOrNull { it.id == id }
    fun partsOf(systemId: String): List<Part> = parts.filter { it.systemId == systemId }

    /** Характеристики, относящиеся к выбранной версии (остальные варианты скрываются). */
    fun specsFor(profile: CarProfile): List<SpecItem> = specs.filter { spec ->
        val powerOk = spec.power.isEmpty() || profile.powerVariant == CarProfile.POWER_UNKNOWN ||
            spec.power.contains(profile.powerVariant)
        val code = if (profile.engineCode == CarProfile.ENGINE_CUSTOM) profile.customEngineCode else profile.engineCode
        val engineOk = spec.engine.isEmpty() || profile.engineCode == CarProfile.ENGINE_UNCONFIRMED ||
            spec.engine.any { it.equals(code, ignoreCase = true) }
        powerOk && engineOk
    }

    companion object {
        fun load(context: Context): KnowledgeBase {
            val errors = mutableListOf<String>()
            val assets = context.assets

            fun read(path: String): String? = try {
                assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: Exception) {
                errors += "Не удалось прочитать $path: ${e.message}"
                null
            }

            val systems = mutableListOf<CarSystem>()
            read("kb/systems.json")?.let { text ->
                runCatching {
                    val arr = JSONObject(text).getJSONArray("systems")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        systems += CarSystem(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            summary = o.optString("summary"),
                            order = o.optInt("order", i),
                            labStage = o.optInt("labStage", 0),
                        )
                    }
                }.onFailure { errors += "systems.json: ${it.message}" }
            }

            val parts = mutableListOf<Part>()
            val partFiles = runCatching { assets.list("kb/parts")?.sorted().orEmpty() }.getOrDefault(emptyList())
            for (file in partFiles) {
                if (!file.endsWith(".json")) continue
                val text = read("kb/parts/$file") ?: continue
                runCatching {
                    val root = JSONObject(text)
                    val systemId = root.getString("system")
                    val arr = root.getJSONArray("parts")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        parts += Part(
                            id = o.getString("id"),
                            systemId = o.optString("system", systemId),
                            name = o.getString("name"),
                            purpose = o.optString("purpose"),
                            principle = o.optString("principle"),
                            location = o.optString("location"),
                            locationConfidence = Confidence.parse(o.optString("locationConfidence", "UNCONFIRMED")),
                            interacts = o.optJSONArray("interacts").strings(),
                            faults = o.optJSONArray("faults").strings(),
                            symptoms = o.optJSONArray("symptoms").strings(),
                            maintenance = o.optJSONArray("maintenance").strings(),
                            note = o.optString("note").takeIf { it.isNotBlank() },
                        )
                    }
                }.onFailure { errors += "$file: ${it.message}" }
            }

            val specs = mutableListOf<SpecItem>()
            val engines = mutableListOf<EngineOption>()
            val powers = mutableListOf<PowerOption>()
            val sources = linkedMapOf<String, Source>()
            read("kb/vehicle.json")?.let { text ->
                runCatching {
                    val root = JSONObject(text)
                    root.optJSONArray("sources")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val s = Source(o.getString("id"), o.getString("title"), o.optString("url"), o.optString("note").takeIf { it.isNotBlank() })
                            sources[s.id] = s
                        }
                    }
                    root.optJSONArray("engineOptions")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            engines += EngineOption(
                                code = o.getString("code"),
                                title = o.getString("title"),
                                description = o.optString("description"),
                                confidence = Confidence.parse(o.optString("confidence")),
                                sources = o.optJSONArray("sources").strings(),
                            )
                        }
                    }
                    root.optJSONArray("powerOptions")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            powers += PowerOption(o.getString("id"), o.getString("title"), o.optString("description"), o.optJSONArray("sources").strings())
                        }
                    }
                    root.optJSONArray("specs")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            specs += SpecItem(
                                group = o.getString("group"),
                                label = o.getString("label"),
                                value = o.getString("value"),
                                confidence = Confidence.parse(o.optString("confidence")),
                                sources = o.optJSONArray("sources").strings(),
                                power = o.optJSONArray("power").strings(),
                                engine = o.optJSONArray("engine").strings(),
                                note = o.optString("note").takeIf { it.isNotBlank() },
                            )
                        }
                    }
                }.onFailure { errors += "vehicle.json: ${it.message}" }
            }

            return KnowledgeBase(
                systems = systems.sortedBy { it.order },
                parts = parts,
                specs = specs,
                engineOptions = engines,
                powerOptions = powers,
                sources = sources,
                loadErrors = errors,
            )
        }

        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
        }
    }
}
