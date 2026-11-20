package ru.rezzo.academy.core.model

/**
 * Насколько достоверна информация.
 * Приложение никогда не выдаёт неподтверждённые данные за точные.
 */
enum class Confidence(val label: String, val hint: String) {
    CONFIRMED("Подтверждено", "Есть источник именно для Rezzo 1.6"),
    VARIANT("Зависит от версии", "Значение отличается у разных версий 1.6 — выберите свою в «Мой Rezzo»"),
    RELATED("По данным Tacuma", "Данные для родственной Daewoo Tacuma / других рынков — требуют сверки"),
    UNCONFIRMED("Не подтверждено", "Нет надёжного источника для вашей версии — сверьте с руководством или ПТС"),
    GENERAL("Общий принцип", "Так устроены автомобили этого типа; конкретика Rezzo не утверждается");

    companion object {
        fun parse(value: String?): Confidence =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNCONFIRMED
    }
}

data class CarSystem(
    val id: String,
    val title: String,
    val summary: String,
    val order: Int,
    /** Этап плана, на котором появится отдельная 3D-лаборатория системы. */
    val labStage: Int,
)

data class Part(
    val id: String,
    val systemId: String,
    val name: String,
    val purpose: String,
    val principle: String,
    val location: String,
    val locationConfidence: Confidence,
    val interacts: List<String>,
    val faults: List<String>,
    val symptoms: List<String>,
    val maintenance: List<String>,
    val note: String?,
)

data class Source(val id: String, val title: String, val url: String, val note: String?)

data class SpecItem(
    val group: String,
    val label: String,
    val value: String,
    val confidence: Confidence,
    val sources: List<String>,
    /** Варианты мощности ("90", "105", "107"), к которым относится строка. Пусто — ко всем. */
    val power: List<String>,
    /** Коды двигателя, к которым относится строка. Пусто — ко всем. */
    val engine: List<String>,
    val note: String?,
)

data class EngineOption(
    val code: String,
    val title: String,
    val description: String,
    val confidence: Confidence,
    val sources: List<String>,
)

data class PowerOption(val id: String, val title: String, val description: String, val sources: List<String>)

/** Профиль конкретного автомобиля пользователя. */
data class CarProfile(
    val engineCode: String = ENGINE_UNCONFIRMED,
    val customEngineCode: String = "",
    val powerVariant: String = POWER_UNKNOWN,
    val abs: String = ABS_UNKNOWN,
    val showHotspots: Boolean = true,
) {
    val engineLabel: String
        get() = when (engineCode) {
            ENGINE_UNCONFIRMED -> "код не подтверждён"
            ENGINE_CUSTOM -> customEngineCode.ifBlank { "свой вариант (не указан)" }
            else -> engineCode
        }

    companion object {
        const val ENGINE_UNCONFIRMED = "UNCONFIRMED"
        const val ENGINE_CUSTOM = "CUSTOM"
        const val POWER_UNKNOWN = "UNKNOWN"
        const val ABS_UNKNOWN = "UNKNOWN"
        const val ABS_YES = "YES"
        const val ABS_NO = "NO"
    }
}
