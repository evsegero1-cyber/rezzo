package ru.rezzo.academy.feature.car

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.rezzo.academy.AppContainer
import ru.rezzo.academy.CarUiState
import ru.rezzo.academy.core.model.CarProfile
import ru.rezzo.academy.scene3d.CameraPreset
import ru.rezzo.academy.scene3d.CarSceneController
import ru.rezzo.academy.scene3d.HotspotScreen
import ru.rezzo.academy.scene3d.LoadResult
import ru.rezzo.academy.scene3d.SceneConfig
import ru.rezzo.academy.scene3d.ViewMode
import ru.rezzo.academy.ui.ConfidenceBadge
import ru.rezzo.academy.ui.InfoNote
import ru.rezzo.academy.ui.Pill
import ru.rezzo.academy.ui.theme.RezzoColors
import kotlin.math.roundToInt

@Composable
fun CarScreen(
    container: AppContainer,
    onOpenPart: (String) -> Unit,
    onOpenModel: () -> Unit,
) {
    val config = container.sceneConfig
    if (config == null) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            InfoNote(
                "Не удалось прочитать описание 3D-сцены (assets/scene/car_scene.json): " +
                    (container.sceneConfigError ?: "неизвестная ошибка") +
                    ". Справочник и настройки работают, 3D-вид недоступен.",
                RezzoColors.Danger,
            )
        }
        return
    }

    val ui = container.carState
    val modelVersion by container.models.version.collectAsState()
    val profile by container.settings.profile.collectAsState(initial = CarProfile())
    val loadResult by container.sceneStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<CarSceneController?>(null) }
    var hotspots by remember { mutableStateOf<List<HotspotScreen>>(emptyList()) }

    val listener = remember {
        object : CarSceneController.Listener {
            override fun onLoaded(result: LoadResult) {
                container.sceneStatus.value = result
            }

            override fun onHotspots(points: List<HotspotScreen>) {
                hotspots = points
            }

            override fun onDisassemblyChanged(step: Int, total: Int, lastTitle: String?) {
                ui.disStep = step
                ui.disTotal = total
                ui.disTitle = lastTitle
            }
        }
    }

    // восстановить включённое пользователем после пересоздания сцены и обработать «показать на модели»
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        c.setMode(ui.mode)
        ui.movables.forEach { (id, open) -> c.setMovable(id, open) }
        ui.lights.forEach { (id, on) -> c.setLight(id, on) }
        ui.selected?.let { c.select(it.node) }
        ui.hasHidden = false
        container.focusRequest.collect { partId ->
            if (partId != null) {
                val part = container.knowledge.part(partId)
                if (part != null && part.systemId != "body" && ui.mode == ViewMode.NORMAL) {
                    ui.mode = ViewMode.TRANSPARENT
                    c.setMode(ViewMode.TRANSPARENT)
                }
                ui.selected = c.focusPart(partId)
                container.focusRequest.value = null
            }
        }
    }

    Box(Modifier.fillMaxSize().background(RezzoColors.Graphite)) {
        key(modelVersion) {
            CarViewport(
                config = config,
                source = container.models.source(config),
                listener = listener,
                onController = { controller = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // жесты: касание — выбор детали, перетаскивание — вращение, щипок — масштаб
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    detectTapGestures(onTap = { o ->
                        val c = controller ?: return@detectTapGestures
                        val r = c.pick(o.x, o.y)
                        ui.selected = r
                        c.select(r?.node)
                    })
                }
                .pointerInput(controller) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        controller?.drag(pan.x, pan.y)
                        if (zoom != 1f) controller?.zoom(zoom)
                    }
                },
        )

        if (profile.showHotspots) {
            HotspotLayer(hotspots, ui.selected?.partId) { partId ->
                val c = controller ?: return@HotspotLayer
                ui.selected = c.focusPart(partId)
            }
        }

        Column(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            TopBar(config, loadResult, onOpenModel)
            ModeRow(ui) { m ->
                ui.mode = m
                controller?.setMode(m)
            }
            if (loadResult?.ok == false) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    InfoNote("3D-модель не загрузилась: ${loadResult?.message}. Откройте «Учебная модель» для подробностей.", RezzoColors.Danger)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.mode == ViewMode.DISASSEMBLY) {
                DisassemblyBar(ui, controller)
            }
            val sel = ui.selected
            if (sel != null) {
                PartCard(
                    container = container,
                    config = config,
                    ui = ui,
                    controller = controller,
                    onOpenPart = onOpenPart,
                    onClose = {
                        ui.selected = null
                        controller?.select(null)
                    },
                )
            } else {
                ControlPanel(
                    config = config,
                    ui = ui,
                    controller = controller,
                    showHotspots = profile.showHotspots,
                    onToggleHotspots = { scope.launch { container.settings.setShowHotspots(!profile.showHotspots) } },
                )
            }
        }
    }
}

private fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }

@Composable
private fun TopBar(config: SceneConfig, load: LoadResult?, onOpenModel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Chevrolet Rezzo · 2005", style = MaterialTheme.typography.titleMedium, color = RezzoColors.Text)
            Text("1.6 л · 16V · МКПП-5 · передний привод", style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
        }
        val placeholder = load?.placeholder ?: config.placeholder
        Pill(
            text = if (placeholder) "Учебная модель" else "Своя модель",
            selected = true,
            onClick = onOpenModel,
            accent = if (placeholder) RezzoColors.Amber else RezzoColors.Green,
        )
    }
}

@Composable
private fun ModeRow(ui: CarUiState, onMode: (ViewMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewMode.entries.forEach { m ->
            Pill(m.title, selected = ui.mode == m, onClick = { onMode(m) })
        }
    }
}

@Composable
private fun HotspotLayer(points: List<HotspotScreen>, selectedPartId: String?, onTap: (String) -> Unit) {
    val density = LocalDensity.current
    val sizeDp = 26.dp
    val half = with(density) { (sizeDp / 2).toPx() }
    Box(Modifier.fillMaxSize()) {
        points.forEach { p ->
            val selected = p.partId == selectedPartId
            Box(
                Modifier
                    .offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }
                    .size(sizeDp)
                    .clip(CircleShape)
                    .background(if (selected) RezzoColors.Accent.copy(alpha = 0.35f) else RezzoColors.Scrim)
                    .border(2.dp, if (selected) RezzoColors.Accent else RezzoColors.Accent.copy(alpha = 0.75f), CircleShape)
                    .clickable { onTap(p.partId) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(RezzoColors.Accent))
            }
        }
    }
}

@Composable
private fun DisassemblyBar(ui: CarUiState, controller: CarSceneController?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RezzoColors.Surface1.copy(alpha = 0.96f))
            .border(1.dp, RezzoColors.Outline, RoundedCornerShape(16.dp))
            .blockTouches()
            .padding(12.dp),
    ) {
        Text(
            "Разборка: шаг ${ui.disStep} из ${ui.disTotal}" + (ui.disTitle?.let { " — снято: $it" } ?: ""),
            style = MaterialTheme.typography.labelLarge,
            color = RezzoColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("◀ Вернуть", selected = false, enabled = ui.disStep > 0, onClick = { controller?.assembleBack() })
            Pill("Снять ▶", selected = true, enabled = ui.disStep < ui.disTotal, onClick = { controller?.disassembleNext() })
            Pill("Собрать всё", selected = false, enabled = ui.disStep > 0, onClick = { controller?.assembleAll() })
        }
    }
}

@Composable
private fun PartCard(
    container: AppContainer,
    config: SceneConfig,
    ui: CarUiState,
    controller: CarSceneController?,
    onOpenPart: (String) -> Unit,
    onClose: () -> Unit,
) {
    val sel = ui.selected ?: return
    val part = container.knowledge.part(sel.partId)
    val system = part?.let { container.knowledge.system(it.systemId) }
    val movableId = controller?.movableIdForNode(sel.node)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RezzoColors.Surface1.copy(alpha = 0.97f))
            .border(1.dp, RezzoColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .blockTouches()
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(
                    (part?.name ?: sel.partId) + (sel.side?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = RezzoColors.Text,
                )
                Text(system?.title ?: "", style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = RezzoColors.TextDim)
            }
        }
        Column(Modifier.padding(end = 10.dp)) {
            if (part != null) {
                Spacer(Modifier.height(6.dp))
                Text(part.purpose, style = MaterialTheme.typography.bodyMedium, color = RezzoColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Расположение: ", style = MaterialTheme.typography.bodySmall, color = RezzoColors.TextDim)
                    ConfidenceBadge(part.locationConfidence)
                }
                if (config.placeholder) {
                    Spacer(Modifier.height(4.dp))
                    Text("На учебной модели форма и размещение условные.", style = MaterialTheme.typography.bodySmall, color = RezzoColors.Amber)
                }
            } else {
                Text("Описание этой детали ещё не добавлено в базу знаний.", style = MaterialTheme.typography.bodyMedium, color = RezzoColors.Amber)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (part != null) Pill("Подробнее", selected = true, onClick = { onOpenPart(part.id) })
                if (movableId != null) {
                    val open = ui.movables[movableId] == true
                    Pill(if (open) "Закрыть" else "Открыть", selected = open, onClick = {
                        ui.movables[movableId] = !open
                        controller?.setMovable(movableId, !open)
                    })
                }
                Pill("Только эта деталь", selected = false, onClick = {
                    controller?.isolateNode(sel.node)
                    ui.hasHidden = true
                })
                Pill("Скрыть", selected = false, onClick = {
                    controller?.hideNode(sel.node)
                    ui.hasHidden = true
                    ui.selected = null
                    controller?.select(null)
                })
            }
        }
    }
}

@Composable
private fun ControlPanel(
    config: SceneConfig,
    ui: CarUiState,
    controller: CarSceneController?,
    showHotspots: Boolean,
    onToggleHotspots: () -> Unit,
) {
    val tabs = listOf("Кузов", "Свет", "Вид")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RezzoColors.Surface1.copy(alpha = 0.95f))
            .border(1.dp, RezzoColors.Outline, RoundedCornerShape(18.dp))
            .blockTouches()
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, t ->
                Text(
                    t,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (ui.panelTab == i) RezzoColors.Surface3 else RezzoColors.Surface1)
                        .clickable { ui.panelTab = i }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (ui.panelTab == i) RezzoColors.Text else RezzoColors.TextDim,
                )
            }
            Spacer(Modifier.weight(1f))
            if (ui.hasHidden) {
                Text(
                    "Показать всё",
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            controller?.showAll()
                            ui.hasHidden = false
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = RezzoColors.Accent,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val scroll = rememberScrollState()
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (ui.panelTab) {
                0 -> {
                    listOf("hood" to "Капот", "tailgate" to "Багажник", "doors" to "Двери", "windows" to "Окна").forEach { (group, title) ->
                        val ids = config.movables.filter { it.group == group }.map { it.id }
                        if (ids.isNotEmpty()) {
                            val open = ids.all { ui.movables[it] == true }
                            Pill(title, selected = open, onClick = {
                                ids.forEach { ui.movables[it] = !open }
                                controller?.setGroupOpen(group, !open)
                            })
                        }
                    }
                }
                1 -> {
                    fun toggle(id: String) {
                        val on = ui.lights[id] != true
                        ui.lights[id] = on
                        controller?.setLight(id, on)
                    }
                    LightPill("Габариты", ui, "parking") { toggle("parking") }
                    LightPill("Ближний", ui, "headlights") { toggle("headlights") }
                    Pill("◀ Поворот", selected = ui.lights["turn_left"] == true, accent = RezzoColors.Amber, enabled = !ui.hazard, onClick = { toggle("turn_left") })
                    Pill("Поворот ▶", selected = ui.lights["turn_right"] == true, accent = RezzoColors.Amber, enabled = !ui.hazard, onClick = { toggle("turn_right") })
                    Pill("Аварийка", selected = ui.hazard, accent = RezzoColors.Amber, onClick = {
                        ui.hazard = !ui.hazard
                        listOf("turn_left", "turn_right").forEach { id ->
                            ui.lights[id] = ui.hazard
                            controller?.setLight(id, ui.hazard)
                        }
                    })
                    Pill("Стоп-сигнал", selected = ui.lights["brake"] == true, accent = RezzoColors.Danger, onClick = { toggle("brake") })
                    LightPill("Задний ход", ui, "reverse") { toggle("reverse") }
                }
                else -> {
                    CameraPreset.entries.forEach { p ->
                        Pill(p.title, selected = false, onClick = { controller?.setPreset(p) })
                    }
                    Pill(if (showHotspots) "Точки: вкл" else "Точки: выкл", selected = showHotspots, onClick = onToggleHotspots)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Касание — выбрать деталь · перетаскивание — вращение · два пальца — масштаб",
            style = MaterialTheme.typography.bodySmall,
            color = RezzoColors.TextDim,
        )
    }
}

@Composable
private fun LightPill(title: String, ui: CarUiState, id: String, onClick: () -> Unit) {
    Pill(title, selected = ui.lights[id] == true, onClick = onClick)
}
