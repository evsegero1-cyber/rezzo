package ru.rezzo.academy.feature.model

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.rezzo.academy.AppContainer
import ru.rezzo.academy.ui.BulletList
import ru.rezzo.academy.ui.InfoNote
import ru.rezzo.academy.ui.Panel
import ru.rezzo.academy.ui.Pill
import ru.rezzo.academy.ui.ScreenHeader
import ru.rezzo.academy.ui.SectionLabel
import ru.rezzo.academy.ui.theme.RezzoColors

@Composable
fun ModelScreen(container: AppContainer, onBack: () -> Unit) {
    val status by container.sceneStatus.collectAsState()
    val version by container.models.version.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val hasCustom = remember(version) { container.models.hasCustom() }
    val config = container.sceneConfig

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val r = container.models.import(uri)
                message = r.fold(
                    onSuccess = { "Модель импортирована (${it / 1024} КБ). Откройте вкладку «Автомобиль» — она загрузится вместо заглушки." },
                    onFailure = { "Не удалось импортировать: ${it.message}" },
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("3D-модель", if (hasCustom) "Своя модель" else "Учебная заглушка", onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!hasCustom) {
                InfoNote(
                    "Сейчас показана учебная заглушка, а не настоящая модель Chevrolet Rezzo. " +
                        (config?.placeholderNote ?: "") +
                        " Все механики (двери, капот, свет, разборка, выбор деталей) работают на ней и останутся при замене модели.",
                )
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Последняя загрузка")
                    val s = status
                    if (s == null) {
                        Text("3D-вид ещё не открывался в этом запуске.", style = MaterialTheme.typography.bodyMedium, color = RezzoColors.TextDim)
                    } else {
                        Text(
                            if (s.ok) "Загружено: ${s.modelName}" else "Ошибка: ${s.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (s.ok) RezzoColors.Text else RezzoColors.Danger,
                        )
                        if (s.ok) {
                            Text("Узлов в модели: ${s.nodeCount}", style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
                            Text("Узлов из конфигурации найдено: ${s.boundNodes}", style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
                            if (s.missingNodes.isNotEmpty()) {
                                Text(
                                    "Не найдено узлов: ${s.missingNodes.size} — эти детали не будут интерактивными: " +
                                        s.missingNodes.take(25).joinToString(", ") + if (s.missingNodes.size > 25) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RezzoColors.Amber,
                                )
                            }
                        }
                        if (s.fellBackToPlaceholder) {
                            Text("Своя модель не загрузилась — показана заглушка. ${s.message ?: ""}", style = MaterialTheme.typography.bodySmall, color = RezzoColors.Amber)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Импортировать GLB / glTF", selected = true, onClick = { picker.launch(arrayOf("*/*")) })
                if (hasCustom) {
                    Pill("Вернуть заглушку", selected = false, onClick = {
                        container.models.reset()
                        message = "Возвращена учебная заглушка."
                    })
                }
            }
            message?.let { InfoNote(it, RezzoColors.Blue) }

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel("Требования к настоящей модели")
                    Spacer(Modifier.height(10.dp))
                    BulletList(
                        listOf(
                            "Формат GLB (glTF 2.0 в одном файле) или .gltf со встроенными данными.",
                            "Модель именно Chevrolet Rezzo / Daewoo Tacuma этого поколения — не машины других марок.",
                            "Метры; ось Y вверх, X вперёд, Z влево (сторона водителя). Чужие единицы приложение попробует вписать по длине 4,35 м.",
                            "Каждая деталь — отдельный узел с уникальным именем, как в конфигурации (door_front_l, hood, engine_block, wheel_fl …).",
                            "Подвижные части — отдельные узлы с началом координат в оси шарнира: двери на передней кромке, капот и дверь багажника — на петлях.",
                            "Стёкла дверей — дочерние узлы дверей, чтобы опускаться вместе с ними.",
                            "Кузовные панели — материал с alphaMode BLEND (иначе в режиме «Прозрачный кузов» они будут скрываться).",
                            "Фары и фонари — отдельные узлы, чтобы их можно было «включать».",
                            "Агрегаты под капотом и днищем — отдельными узлами для разборки и подсветки.",
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Полный список имён узлов и правила — в репозитории: docs/ASSETS.md и app/src/main/assets/scene/car_scene.json.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RezzoColors.TextDim,
                    )
                }
            }
        }
    }
}
