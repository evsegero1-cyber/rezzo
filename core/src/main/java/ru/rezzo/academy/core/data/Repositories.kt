package ru.rezzo.academy.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import ru.rezzo.academy.core.db.PartProgressEntity
import ru.rezzo.academy.core.db.ProgressDao
import ru.rezzo.academy.core.model.CarProfile
import java.io.IOException

private val Context.rezzoDataStore: DataStore<Preferences> by preferencesDataStore(name = "rezzo_settings")

/** Настройки автомобиля пользователя (DataStore). */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.rezzoDataStore

    private object Keys {
        val ENGINE = stringPreferencesKey("engine_code")
        val CUSTOM_ENGINE = stringPreferencesKey("custom_engine_code")
        val POWER = stringPreferencesKey("power_variant")
        val ABS = stringPreferencesKey("abs")
        val HOTSPOTS = booleanPreferencesKey("show_hotspots")
    }

    val profile: Flow<CarProfile> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            CarProfile(
                engineCode = p[Keys.ENGINE] ?: CarProfile.ENGINE_UNCONFIRMED,
                customEngineCode = p[Keys.CUSTOM_ENGINE] ?: "",
                powerVariant = p[Keys.POWER] ?: CarProfile.POWER_UNKNOWN,
                abs = p[Keys.ABS] ?: CarProfile.ABS_UNKNOWN,
                showHotspots = p[Keys.HOTSPOTS] ?: true,
            )
        }

    suspend fun setEngineCode(code: String) {
        store.edit { it[Keys.ENGINE] = code }
    }

    suspend fun setCustomEngineCode(code: String) {
        store.edit { it[Keys.CUSTOM_ENGINE] = code.trim().uppercase() }
    }

    suspend fun setPowerVariant(id: String) {
        store.edit { it[Keys.POWER] = id }
    }

    suspend fun setAbs(value: String) {
        store.edit { it[Keys.ABS] = value }
    }

    suspend fun setShowHotspots(show: Boolean) {
        store.edit { it[Keys.HOTSPOTS] = show }
    }
}

/** Прогресс изучения деталей (Room). */
class ProgressRepository(private val dao: ProgressDao) {
    val progress: Flow<Map<String, PartProgressEntity>> =
        dao.observeAll().map { list -> list.associateBy { it.partId } }

    suspend fun markViewed(partId: String) {
        val now = System.currentTimeMillis()
        val old = dao.get(partId)
        dao.put(
            old?.copy(viewCount = old.viewCount + 1)
                ?: PartProgressEntity(partId, viewCount = 1, firstViewedAt = now, studied = false, studiedAt = null)
        )
    }

    suspend fun setStudied(partId: String, studied: Boolean) {
        val now = System.currentTimeMillis()
        val old = dao.get(partId) ?: PartProgressEntity(partId, 0, now, false, null)
        dao.put(old.copy(studied = studied, studiedAt = if (studied) now else null))
    }
}
