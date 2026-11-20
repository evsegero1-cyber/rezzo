package ru.rezzo.academy.feature.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.rezzo.academy.AppContainer
import ru.rezzo.academy.core.model.CarProfile
import ru.rezzo.academy.ui.ConfidenceBadge
import ru.rezzo.academy.ui.InfoNote
import ru.rezzo.academy.ui.Panel
import ru.rezzo.academy.ui.Pill
import ru.rezzo.academy.ui.ScreenHeader
import ru.rezzo.academy.ui.SectionLabel
import ru.rezzo.academy.ui.theme.RezzoColors

/** Этапы разработки — честный статус, что уже работает. */
private val roadmap = listOf(
    Triple(1, "Архитектура, навигация, дизайн, база данных", true),
    Triple(2, "3D-сцена: вращение, масштаб, виды, режимы", true),
    Triple(3, "Двери, капот, багажник, окна, свет, интерактивные точки", true),
    Triple(4, "Лаборатория двигателя и анимация 4 тактов", false),
    Triple(5, "КПП и сцепление, подвеска, тормоза", false),
    Triple(6, "Остальные системы (электрика, топливо, охлаждение, выпуск, салон)", false),
    Triple(7, "Виртуальное вождение", false),
    Triple(8, "Диагностика, ремонт, персональный гараж", false),
    Triple(9, "Обучение и тесты", false),
)

@Composable
fun VehicleScreen(container: AppContainer, onOpenModel: () -> Unit) {
    val kb = container.knowledge
    val profile by container.settings.profile.collectAsState(initial = CarProfile())
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var customDraft by remember(profile.customEngineCode) { mutableStateOf(profile.customEngineCode) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader("Мой Rezzo", "Chevrolet Rezzo 2005 · 1.6 (1598 см³) · 16V · МКПП-5 · передний привод")

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoNote(
                "Код двигателя и мощность записаны в ПТС: строки «Модель, № двигателя» и «Мощность двигателя». " +
                    "Код также выбит на блоке цилиндров. Выбор ниже меняет показываемые характеристики.",
                RezzoColors.Blue,
            )

            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Код двигателя — сейчас: ${profile.engineLabel}")
                    Spacer(Modifier.height(4.dp))
                    kb.engineOptions.forEach { opt ->
                        OptionRow(
                            selected = profile.engineCode == opt.code,
                            title = opt.title,
                            description = opt.description,
                            onClick = { scope.launch { container.settings.setEngineCode(opt.code) } },
                        ) { if (opt.code != CarProfile.ENGINE_UNCONFIRMED && opt.code != CarProfile.ENGINE_CUSTOM) ConfidenceBadge(opt.confidence) }
                    }
                    if (profile.engineCode == CarProfile.ENGINE_CUSTOM) {
                        OutlinedTextField(
                            value = customDraft,
                            onValueChange = {
                                customDraft = it
                                scope.launch { container.settings.setCustomEngineCode(it) }
                            },
                            label = { Text("Код как в ПТС") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RezzoColors.Accent,
                                unfocusedBorderColor = RezzoColors.Outline,
                                focusedLabelColor = RezzoColors.Accent,
                            ),
                        )
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Мощность (версия 1.6)")
                    Spacer(Modifier.height(4.dp))
                    kb.powerOptions.forEach { opt ->
                        OptionRow(
                            selected = profile.powerVariant == opt.id,
                            title = opt.title,
                            description = opt.description,
                            onClick = { scope.launch { container.settings.setPowerVariant(opt.id) } },
                        )
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel("ABS")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(CarProfile.ABS_YES to "Есть", CarProfile.ABS_NO to "Нет", CarProfile.ABS_UNKNOWN to "Не знаю").forEach { (v, t) ->
                            Pill(t, selected = profile.abs == v, onClick = { scope.launch { container.settings.setAbs(v) } })
                        }
                    }
                }
            }

            SectionLabel("Характеристики выбранной версии", Modifier.padding(top = 6.dp))
            val specs = kb.specsFor(profile)
            specs.groupBy { it.group }.forEach { (group, items) ->
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(group, style = MaterialTheme.typography.titleSmall, color = RezzoColors.Accent)
                        items.forEach { spec ->
                            Column {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(spec.label, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim, modifier = Modifier.weight(1f))
                                    ConfidenceBadge(spec.confidence)
                                }
                                Text(spec.value, style = MaterialTheme.typography.bodyLarge, color = RezzoColors.Text)
                                spec.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RezzoColors.Amber) }
                                if (spec.sources.isNotEmpty()) {
                                    Text(
                                        "Источник: " + spec.sources.mapNotNull { kb.sources[it]?.title?.substringBefore(" — ") }.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RezzoColors.TextDim,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Что означают отметки")
                    ru.rezzo.academy.core.model.Confidence.entries.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConfidenceBadge(c)
                            Spacer(Modifier.width(8.dp))
                            Text(c.hint, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
                        }
                    }
                }
            }

            Panel(Modifier.fillMaxWidth().clickable(onClick = onOpenModel)) {
                Column {
                    SectionLabel("3D-модель")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (container.models.hasCustom()) "Загружена своя модель" else "Используется учебная заглушка",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RezzoColors.Text,
                    )
                    Text("Статус, импорт GLB и требования к модели →", style = MaterialTheme.typography.bodySmall, color = RezzoColors.Accent)
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("План разработки")
                    roadmap.forEach { (n, title, done) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (done) RezzoColors.Accent else RezzoColors.Surface3),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Этап $n. $title",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (done) RezzoColors.Text else RezzoColors.TextDim,
                            )
                        }
                    }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Источники")
                    kb.sources.values.forEach { s ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = s.url.isNotBlank()) { runCatching { uri.openUri(s.url) } }
                                .padding(vertical = 2.dp),
                        ) {
                            Text(s.title, style = MaterialTheme.typography.bodySmall, color = if (s.url.isNotBlank()) RezzoColors.Accent else RezzoColors.Text)
                            s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = RezzoColors.Accent, unselectedColor = RezzoColors.TextDim),
        )
        Column(Modifier.weight(1f).padding(top = 10.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = RezzoColors.Text)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
        }
        Box(Modifier.padding(top = 12.dp)) { trailing() }
    }
}
