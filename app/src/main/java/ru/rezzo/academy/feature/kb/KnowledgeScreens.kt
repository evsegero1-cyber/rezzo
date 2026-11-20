package ru.rezzo.academy.feature.kb

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.rezzo.academy.AppContainer
import ru.rezzo.academy.core.db.PartProgressEntity
import ru.rezzo.academy.core.model.Confidence
import ru.rezzo.academy.core.model.Part
import ru.rezzo.academy.ui.BulletList
import ru.rezzo.academy.ui.ConfidenceBadge
import ru.rezzo.academy.ui.InfoNote
import ru.rezzo.academy.ui.Panel
import ru.rezzo.academy.ui.Pill
import ru.rezzo.academy.ui.ScreenHeader
import ru.rezzo.academy.ui.SectionLabel
import ru.rezzo.academy.ui.theme.RezzoColors

@Composable
fun SystemsScreen(container: AppContainer, onOpenSystem: (String) -> Unit) {
    val kb = container.knowledge
    val progress by container.progress.progress.collectAsState(initial = emptyMap())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader("Системы автомобиля", "Справочник по Rezzo 1.6: ${kb.parts.size} деталей")
        }
        if (kb.loadErrors.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    InfoNote("Часть базы знаний не загрузилась: " + kb.loadErrors.joinToString("; "), RezzoColors.Danger)
                }
            }
        }
        items(kb.systems, key = { it.id }) { system ->
            val parts = kb.partsOf(system.id)
            val studied = parts.count { progress[it.id]?.studied == true }
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RezzoColors.Surface1)
                    .border(1.dp, RezzoColors.Outline, RoundedCornerShape(16.dp))
                    .clickable { onOpenSystem(system.id) }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(system.title, style = MaterialTheme.typography.titleMedium, color = RezzoColors.Text, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = RezzoColors.TextDim)
                }
                Spacer(Modifier.height(4.dp))
                Text(system.summary, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (parts.isEmpty()) 0f else studied.toFloat() / parts.size },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = RezzoColors.Accent,
                    trackColor = RezzoColors.Surface3,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("Изучено $studied из ${parts.size}", style = MaterialTheme.typography.labelMedium, color = RezzoColors.TextDim, modifier = Modifier.weight(1f))
                    Text("3D-лаборатория — этап ${system.labStage}", style = MaterialTheme.typography.labelMedium, color = RezzoColors.TextDim)
                }
            }
        }
    }
}

@Composable
fun SystemScreen(container: AppContainer, systemId: String, onBack: () -> Unit, onOpenPart: (String) -> Unit) {
    val kb = container.knowledge
    val system = kb.system(systemId)
    val parts = kb.partsOf(systemId)
    val progress by container.progress.progress.collectAsState(initial = emptyMap())
    val config = container.sceneConfig
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader(system?.title ?: "Система", "${parts.size} деталей", onBack) }
        if (system != null) {
            item {
                Text(
                    system.summary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RezzoColors.TextDim,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        items(parts, key = { it.id }) { part ->
            PartRow(part, progress[part.id], onModel = config?.hasPart(part.id) == true) { onOpenPart(part.id) }
        }
    }
}

@Composable
private fun PartRow(part: Part, progress: PartProgressEntity?, onModel: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        progress?.studied == true -> RezzoColors.Accent
                        progress != null -> RezzoColors.Accent.copy(alpha = 0.35f)
                        else -> RezzoColors.Surface3
                    },
                ),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(part.name, style = MaterialTheme.typography.bodyLarge, color = RezzoColors.Text)
            Text(part.purpose, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onModel) {
            Text("3D", style = MaterialTheme.typography.labelSmall, color = RezzoColors.Accent, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun PartScreen(
    container: AppContainer,
    partId: String,
    onBack: () -> Unit,
    onOpenPart: (String) -> Unit,
    onShowOnModel: (String) -> Unit,
) {
    val kb = container.knowledge
    val part = kb.part(partId)
    val progress by container.progress.progress.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    LaunchedEffect(partId) { if (part != null) container.progress.markViewed(partId) }

    if (part == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Деталь не найдена", partId, onBack)
            Box(Modifier.padding(16.dp)) { InfoNote("Для «$partId» ещё нет описания в базе знаний.") }
        }
        return
    }
    val system = kb.system(part.systemId)
    val studied = progress[part.id]?.studied == true
    val onModel = container.sceneConfig?.hasPart(part.id) == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(part.name, system?.title, onBack)
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(if (studied) "✓ Изучено" else "Отметить изученной", selected = studied, onClick = {
                scope.launch { container.progress.setStudied(part.id, !studied) }
            })
            if (onModel) Pill("Показать на 3D-модели", selected = false, onClick = { onShowOnModel(part.id) })
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("1. Назначение") { Body(part.purpose) }
            Section("2. Принцип работы") { Body(part.principle) }
            Section("3. Где находится", badge = part.locationConfidence) {
                Body(part.location)
                Spacer(Modifier.height(6.dp))
                Text(part.locationConfidence.hint, style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
            }
            Section("4. С чем взаимодействует") { BulletList(part.interacts) }
            Section("5. Типичные неисправности") { BulletList(part.faults) }
            Section("6. Признаки неисправности") { BulletList(part.symptoms) }
            Section("7. Обслуживание") { BulletList(part.maintenance) }
            part.note?.let { InfoNote(it, RezzoColors.Blue) }
            InfoNote(
                "Признаки неисправности помогают понять, что проверить, но не являются диагнозом. " +
                    "Моменты затяжки, номера деталей и интервалы обслуживания смотрите в руководстве для вашей модификации.",
                RezzoColors.TextDim,
            )
            val related = kb.partsOf(part.systemId).filter { it.id != part.id }.take(6)
            if (related.isNotEmpty()) {
                SectionLabel("Другие детали системы", Modifier.padding(top = 6.dp))
                related.forEach { r ->
                    Text(
                        r.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenPart(r.id) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RezzoColors.Accent,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, badge: Confidence? = null, content: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = RezzoColors.Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (badge != null) ConfidenceBadge(badge)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = RezzoColors.Text)
}
