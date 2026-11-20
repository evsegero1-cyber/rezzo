package ru.rezzo.academy

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import ru.rezzo.academy.core.data.ProgressRepository
import ru.rezzo.academy.core.data.SettingsRepository
import ru.rezzo.academy.core.db.AppDatabase
import ru.rezzo.academy.core.kb.KnowledgeBase
import ru.rezzo.academy.scene3d.LoadResult
import ru.rezzo.academy.scene3d.ModelSource
import ru.rezzo.academy.scene3d.PickResult
import ru.rezzo.academy.scene3d.SceneConfig
import ru.rezzo.academy.scene3d.ViewMode
import java.io.File

class RezzoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Простое внедрение зависимостей: всё приложение получает модули отсюда. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val knowledge: KnowledgeBase by lazy { KnowledgeBase.load(appContext) }

    /** Конфигурация 3D-сцены; null — если файл повреждён или отсутствует (приложение не падает). */
    val sceneConfig: SceneConfig? by lazy { runCatching { SceneConfig.load(appContext) }.getOrNull() }
    val sceneConfigError: String? by lazy {
        runCatching { SceneConfig.load(appContext) }.exceptionOrNull()?.let { it.message ?: it.toString() }
    }

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val progress: ProgressRepository by lazy { ProgressRepository(database.progressDao()) }
    val settings = SettingsRepository(appContext)
    val models = ModelStore(appContext)

    /** Результат последней загрузки 3D-модели (для экрана «3D-модель»). */
    val sceneStatus = MutableStateFlow<LoadResult?>(null)

    /** Запрос «показать деталь на модели» из базы знаний. */
    val focusRequest = MutableStateFlow<String?>(null)

    /** Состояние 3D-экрана, переживающее переход между вкладками. */
    val carState = CarUiState()
}

/** Состояние органов управления 3D-сценой (то, что включил пользователь). */
class CarUiState {
    var mode by mutableStateOf(ViewMode.NORMAL)
    val movables = mutableStateMapOf<String, Boolean>()
    val lights = mutableStateMapOf<String, Boolean>()
    var hazard by mutableStateOf(false)
    var selected by mutableStateOf<PickResult?>(null)
    var panelTab by mutableStateOf(0)
    var disStep by mutableStateOf(0)
    var disTotal by mutableStateOf(0)
    var disTitle by mutableStateOf<String?>(null)
    var hasHidden by mutableStateOf(false)
}

/** Хранение пользовательской 3D-модели (импорт GLB/glTF). */
class ModelStore(private val context: Context) {
    private val dir = File(context.filesDir, "models")
    val customFile = File(dir, "custom_model.glb")

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun hasCustom(): Boolean = customFile.exists() && customFile.length() > 0

    fun source(config: SceneConfig): ModelSource =
        if (hasCustom()) ModelSource.LocalFile(customFile) else ModelSource.Asset(config.model)

    suspend fun import(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "import.tmp")
            val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
            input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            val head = ByteArray(4)
            tmp.inputStream().use { it.read(head) }
            val isGlb = head[0] == 'g'.code.toByte() && head[1] == 'l'.code.toByte() &&
                head[2] == 'T'.code.toByte() && head[3] == 'F'.code.toByte()
            val isGltfJson = head[0] == '{'.code.toByte()
            if (!isGlb && !isGltfJson) {
                tmp.delete()
                error("Файл не похож на GLB или glTF")
            }
            if (customFile.exists()) customFile.delete()
            if (!tmp.renameTo(customFile)) {
                tmp.copyTo(customFile, overwrite = true)
                tmp.delete()
            }
            _version.value = _version.value + 1
            customFile.length()
        }
    }

    fun reset() {
        if (customFile.exists()) customFile.delete()
        _version.value = _version.value + 1
    }
}
