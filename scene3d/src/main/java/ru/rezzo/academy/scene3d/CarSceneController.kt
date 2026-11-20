package ru.rezzo.academy.scene3d

import android.content.Context
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Откуда грузить модель. */
sealed class ModelSource {
    data class Asset(val path: String) : ModelSource()
    data class LocalFile(val file: File) : ModelSource()
}

enum class ViewMode(val title: String) {
    NORMAL("Обычный"),
    TRANSPARENT("Прозрачный кузов"),
    SECTION("Разрез"),
    DISASSEMBLY("Разборка"),
    UNDERSIDE("Вид снизу"),
}

data class LoadResult(
    val ok: Boolean,
    val placeholder: Boolean,
    val modelName: String,
    val message: String?,
    val nodeCount: Int,
    val boundNodes: Int,
    val missingNodes: List<String>,
    val fellBackToPlaceholder: Boolean,
)

data class PickResult(val node: String, val partId: String, val side: String?)

data class HotspotScreen(val partId: String, val node: String, val x: Float, val y: Float)

/**
 * 3D-сцена автомобиля на Filament. Не знает ничего об интерфейсе:
 * получает команды (режим, двери, свет, камера, выбор) и сообщает результат через [Listener].
 * Все вызовы — из главного потока.
 */
class CarSceneController(
    context: Context,
    private val surfaceView: SurfaceView,
    private val config: SceneConfig,
    private val source: ModelSource,
    private val listener: Listener,
) {
    interface Listener {
        fun onLoaded(result: LoadResult)
        fun onHotspots(points: List<HotspotScreen>)
        fun onDisassemblyChanged(step: Int, total: Int, lastTitle: String?)
    }

    companion object {
        init {
            Gltfio.init()
        }

        private val ACCENT = floatArrayOf(0.20f, 0.85f, 0.78f)
        private const val FOV = 40.0
        private const val NEAR = 0.05
        private const val FAR = 80.0
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity: Int = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val sun: Int = EntityManager.get().create()
    private val indirectLight: IndirectLight
    private val skybox: Skybox
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(context)
    private var swapChain: SwapChain? = null
    private val choreographer = Choreographer.getInstance()
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)
    private var asset: FilamentAsset? = null

    private val tm get() = engine.transformManager
    private val rm get() = engine.renderableManager

    // --- карта сцены
    private val nodeEntity = HashMap<String, Int>()
    private val entityName = HashMap<Int, String>()
    private val renderablesUnder = HashMap<String, IntArray>()
    private var allRenderables = IntArray(0)
    private val primInfo = HashMap<Int, List<PrimMaterial>>()
    private val renderableBinding = HashMap<Int, PartBinding>()
    private val localBox = HashMap<Int, FloatArray>() // cx, cy, cz, hx, hy, hz
    private var bodySet = HashSet<Int>()
    private var glassSet = HashSet<Int>()
    private var groundSet = HashSet<Int>()
    private var bodyIsBlend = true

    // --- видимость
    private val inScene = HashSet<Int>()
    private val modeHidden = HashSet<Int>()
    private val disHidden = HashSet<Int>()
    private val userHidden = HashSet<Int>()

    // --- анимация узлов
    private inner class NodeAnim(val entity: Int, val rest: FloatArray) {
        var movable: MovableDef? = null
        var moveProgress = 0f
        var moveTarget = 0f
        var disOffset = FloatArray(3)
        var disProgress = 0f
        var disTarget = 0f
        var disNodeName: String = ""
    }

    private val anims = HashMap<Int, NodeAnim>()
    private val movableById = HashMap<String, NodeAnim>()
    private var disStep = 0

    // --- свет и подсветка
    private val lightOn = HashMap<String, Boolean>()
    private var highlighted = HashSet<Int>()
    private val litColor = HashMap<Int, FloatArray>()
    private var blinkPhaseOn = true
    private var lightsDirty = false
    private var time = 0.0
    private val hotspotLocal = HashMap<HotspotDef, FloatArray>()

    var mode = ViewMode.NORMAL
        private set

    private val orbit = OrbitCamera(config.home, config.interior)
    private var viewportW = 1
    private var viewportH = 1
    private val viewM = FloatArray(16)
    private val projM = FloatArray(16)
    private val viewProj = FloatArray(16)
    private var lastFrameNanos = 0L
    private var dirtyFrames = 4
    private var resumed = false
    private var destroyed = false
    private var lastHotspots: List<HotspotScreen> = emptyList()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!resumed || destroyed) return
            choreographer.postFrameCallback(this)
            frame(frameTimeNanos)
        }
    }

    init {
        view.scene = scene
        view.camera = camera
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true
            sampleCount = 4
        }

        skybox = Skybox.Builder().color(0.012f, 0.014f, 0.017f, 1f).build(engine)
        scene.skybox = skybox

        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.97f, 0.93f)
            .intensity(95_000f)
            .direction(-0.35f, -1f, -0.45f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.80f, 0.85f, 0.95f))
            .intensity(38_000f)
            .build(engine)
        scene.indirectLight = indirectLight

        camera.setExposure(16f, 1f / 125f, 100f)

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                surfaceView.display?.let { displayHelper.attach(renderer, it) }
                dirtyFrames = 4
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                viewportW = max(1, width)
                viewportH = max(1, height)
                view.viewport = Viewport(0, 0, viewportW, viewportH)
                camera.setProjection(FOV, viewportW.toDouble() / viewportH, NEAR, FAR, Camera.Fov.VERTICAL)
                Matrix.perspectiveM(projM, 0, FOV.toFloat(), viewportW.toFloat() / viewportH, NEAR.toFloat(), FAR.toFloat())
                dirtyFrames = 4
            }
        }
        uiHelper.attachTo(surfaceView)

        val result = loadModel()
        // сообщаем интерфейсу асинхронно: конструктор вызывается во время построения экрана
        mainHandler.post {
            if (!destroyed) {
                listener.onLoaded(result)
                listener.onDisassemblyChanged(0, config.disassembly.size, null)
            }
        }
        resume()
    }

    // ------------------------------------------------------------------ загрузка

    private fun readSource(src: ModelSource): ByteArray = when (src) {
        is ModelSource.Asset -> appContext.assets.open(src.path).use { it.readBytes() }
        is ModelSource.LocalFile -> src.file.readBytes()
    }

    private fun createAsset(bytes: ByteArray): FilamentAsset? {
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        val a = assetLoader.createAsset(buffer) ?: return null
        resourceLoader.loadResources(a)
        a.releaseSourceData()
        return a
    }

    private fun loadModel(): LoadResult {
        var fellBack = false
        var message: String? = null
        var bytes: ByteArray? = null
        var name = when (source) {
            is ModelSource.Asset -> source.path.substringAfterLast('/')
            is ModelSource.LocalFile -> source.file.name
        }
        var created: FilamentAsset? = null
        try {
            bytes = readSource(source)
            created = createAsset(bytes)
            if (created == null) message = "Файл модели не распознан как glTF/GLB"
        } catch (e: Throwable) {
            message = "Ошибка загрузки модели: ${e.message}"
        }
        if (created == null && source is ModelSource.LocalFile) {
            // своя модель не загрузилась — возвращаемся к учебной заглушке
            try {
                bytes = readSource(ModelSource.Asset(config.model))
                created = createAsset(bytes)
                fellBack = created != null
                name = config.model.substringAfterLast('/')
            } catch (e: Throwable) {
                message = (message ?: "") + "; заглушка тоже не загрузилась: ${e.message}"
            }
        }
        val a = created
        if (a == null || bytes == null) {
            return LoadResult(false, false, name, message ?: "Модель не найдена", 0, 0, emptyList(), fellBack)
        }
        asset = a
        val info = runCatching { GltfInfo.parse(bytes) }.getOrNull()
        scene.addEntities(a.entities)
        indexScene(a, info)
        if (!(info?.isPlaceholder ?: false) && source is ModelSource.LocalFile && !fellBack) normalizeImported(a)
        setupAnimations()
        applyModeVisibility()
        applyAllMaterials()

        val needed = (config.parts.map { it.node } + config.movables.map { it.node }).toSet()
        val missing = needed.filter { it !in nodeEntity }.sorted()
        return LoadResult(
            ok = true,
            placeholder = info?.isPlaceholder ?: false,
            modelName = name,
            message = message,
            nodeCount = nodeEntity.size,
            boundNodes = needed.size - missing.size,
            missingNodes = missing,
            fellBackToPlaceholder = fellBack,
        )
    }

    private fun indexScene(a: FilamentAsset, info: GltfInfo?) {
        for (e in a.entities) {
            val n = a.getName(e) ?: continue
            if (n.isNotBlank() && !nodeEntity.containsKey(n)) nodeEntity[n] = e
            entityName[e] = n
        }
        val under = HashMap<String, MutableList<Int>>()
        val renderables = a.renderableEntities
        allRenderables = renderables
        val box = Box()
        for (r in renderables) {
            inScene += r
            val rInst = rm.getInstance(r)
            rm.setCastShadows(rInst, true)
            rm.setReceiveShadows(rInst, true)
            rm.getAxisAlignedBoundingBox(rInst, box)
            val c = box.center
            val h = box.halfExtent
            localBox[r] = floatArrayOf(c[0], c[1], c[2], h[0], h[1], h[2])
            entityName[r]?.let { own -> info?.nodeMaterials?.get(own)?.let { primInfo[r] = it } }
            // поднимаемся по иерархии: деталь = ближайший предок, описанный в конфиге
            var cur = r
            var guard = 0
            while (cur != 0 && guard < 64) {
                guard++
                val nm = entityName[cur]
                if (nm != null) {
                    under.getOrPut(nm) { mutableListOf() } += r
                    if (!renderableBinding.containsKey(r)) config.binding(nm)?.let { renderableBinding[r] = it }
                }
                val ti = tm.getInstance(cur)
                if (ti == 0) break
                cur = tm.getParent(ti)
            }
        }
        under.forEach { (k, v) -> renderablesUnder[k] = v.toIntArray() }

        bodySet = groupRenderables("body")
        glassSet = groupRenderables("glass")
        groundSet = groupRenderables("ground")
        groundSet.forEach { rm.setCastShadows(rm.getInstance(it), false) }
        bodyIsBlend = bodySet.isNotEmpty() && bodySet.all { r -> primInfo[r]?.all { it.blend } ?: false }
    }

    private fun groupRenderables(group: String): HashSet<Int> {
        val out = HashSet<Int>()
        config.groups[group]?.forEach { n -> renderablesUnder[n]?.let { out.addAll(it.toList()) } }
        return out
    }

    /** Чужая модель может быть в мм или см: вписываем по длине Rezzo (4,35 м) и ставим на землю. */
    private fun normalizeImported(a: FilamentAsset) {
        val bb = a.boundingBox
        val c = bb.center
        val h = bb.halfExtent
        val length = 2f * max(h[0], h[2])
        if (length <= 1e-6f) return
        val s = 4.35f / length
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, -c[0] * s, (h[1] - c[1]) * s, -c[2] * s)
        Matrix.scaleM(m, 0, s, s, s)
        val root = tm.getInstance(a.root)
        if (root != 0) tm.setTransform(root, m)
    }

    private fun setupAnimations() {
        fun animFor(node: String): NodeAnim? {
            val e = nodeEntity[node] ?: return null
            anims[e]?.let { return it }
            val ti = tm.getInstance(e)
            if (ti == 0) return null
            val rest = FloatArray(16)
            tm.getTransform(ti, rest)
            return NodeAnim(e, rest).also { anims[e] = it }
        }
        for (m in config.movables) {
            val a = animFor(m.node) ?: continue
            a.movable = m
            movableById[m.id] = a
        }
        for (step in config.disassembly) for (dn in step.nodes) {
            val a = animFor(dn.node) ?: continue
            a.disOffset = dn.offset
            a.disNodeName = dn.node
        }
    }

    // ------------------------------------------------------------------ жизненный цикл

    fun resume() {
        if (destroyed || resumed) return
        resumed = true
        lastFrameNanos = 0L
        dirtyFrames = 4
        choreographer.postFrameCallback(frameCallback)
    }

    fun pause() {
        resumed = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        if (destroyed) return
        pause()
        destroyed = true
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        asset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        asset = null
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        engine.destroyEntity(sun)
        EntityManager.get().destroy(sun)
        engine.destroyIndirectLight(indirectLight)
        engine.destroySkybox(skybox)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    // ------------------------------------------------------------------ кадр

    private fun frame(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0.016f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = frameTimeNanos
        time += dt

        var changed = orbit.update(dt)
        if (stepAnimations(dt)) changed = true
        if (updateLights()) changed = true
        if (changed) dirtyFrames = max(dirtyFrames, 2)

        if (dirtyFrames > 0) {
            camera.lookAt(
                orbit.eye[0].toDouble(), orbit.eye[1].toDouble(), orbit.eye[2].toDouble(),
                orbit.lookAt[0].toDouble(), orbit.lookAt[1].toDouble(), orbit.lookAt[2].toDouble(),
                0.0, 1.0, 0.0,
            )
            Matrix.setLookAtM(
                viewM, 0,
                orbit.eye[0], orbit.eye[1], orbit.eye[2],
                orbit.lookAt[0], orbit.lookAt[1], orbit.lookAt[2],
                0f, 1f, 0f,
            )
            Matrix.multiplyMM(viewProj, 0, projM, 0, viewM, 0)
            publishHotspots()
            val sc = swapChain
            if (uiHelper.isReadyToRender && sc != null) {
                if (renderer.beginFrame(sc, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                    dirtyFrames--
                }
            }
        }
    }

    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    private fun stepAnimations(dt: Float): Boolean {
        var any = false
        var visibilityChanged = false
        for (a in anims.values) {
            var dirty = false
            val m = a.movable
            if (m != null && a.moveProgress != a.moveTarget) {
                val step = dt / m.durationSec
                a.moveProgress = if (a.moveTarget > a.moveProgress) min(a.moveTarget, a.moveProgress + step)
                else max(a.moveTarget, a.moveProgress - step)
                dirty = true
            }
            if (a.disProgress != a.disTarget) {
                val step = dt / 0.7f
                a.disProgress = if (a.disTarget > a.disProgress) min(a.disTarget, a.disProgress + step)
                else max(a.disTarget, a.disProgress - step)
                dirty = true
                if (a.disProgress >= 1f && a.disTarget >= 1f) {
                    renderablesUnder[a.disNodeName]?.let { disHidden.addAll(it.toList()) }
                    visibilityChanged = true
                }
            }
            if (dirty) {
                applyNodeTransform(a)
                any = true
            }
        }
        if (visibilityChanged) applyVisibility()
        return any
    }

    private fun applyNodeTransform(a: NodeAnim) {
        val local = a.rest.copyOf()
        val m = a.movable
        if (m != null && a.moveProgress > 0f) {
            val p = ease(a.moveProgress)
            val post = FloatArray(16)
            if (m.type == "translate") {
                Matrix.setIdentityM(post, 0)
                Matrix.translateM(post, 0, m.offset[0] * p, m.offset[1] * p, m.offset[2] * p)
            } else {
                Matrix.setRotateM(post, 0, m.angleDeg * p, m.axis[0], m.axis[1], m.axis[2])
            }
            Matrix.multiplyMM(local, 0, a.rest, 0, post, 0)
        }
        if (a.disProgress > 0f) {
            val p = ease(a.disProgress)
            val pre = FloatArray(16)
            Matrix.setIdentityM(pre, 0)
            Matrix.translateM(pre, 0, a.disOffset[0] * p, a.disOffset[1] * p, a.disOffset[2] * p)
            val out = FloatArray(16)
            Matrix.multiplyMM(out, 0, pre, 0, local, 0)
            out.copyInto(local)
        }
        val ti = tm.getInstance(a.entity)
        if (ti != 0) tm.setTransform(ti, local)
    }

    // ------------------------------------------------------------------ видимость и материалы

    private fun applyVisibility() {
        for (r in allRenderables) {
            val show = r !in modeHidden && r !in disHidden && r !in userHidden
            val shown = r in inScene
            if (show && !shown) {
                scene.addEntity(r)
                inScene += r
            } else if (!show && shown) {
                scene.removeEntity(r)
                inScene -= r
            }
        }
        dirtyFrames = max(dirtyFrames, 2)
    }

    private fun applyModeVisibility() {
        modeHidden.clear()
        when (mode) {
            ViewMode.SECTION -> config.groups["section_hidden"]?.forEach { n -> renderablesUnder[n]?.let { modeHidden.addAll(it.toList()) } }
            ViewMode.UNDERSIDE -> modeHidden.addAll(groundSet)
            ViewMode.TRANSPARENT -> if (!bodyIsBlend) modeHidden.addAll(bodySet)
            else -> Unit
        }
        applyVisibility()
    }

    private fun applyAllMaterials() {
        for (r in allRenderables) applyMaterial(r)
        dirtyFrames = max(dirtyFrames, 2)
    }

    private fun applyMaterial(r: Int) {
        val inst = rm.getInstance(r)
        if (inst == 0) return
        val prims = primInfo[r]
        val transparentBody = mode == ViewMode.TRANSPARENT && r in bodySet
        val transparentGlass = mode == ViewMode.TRANSPARENT && r in glassSet
        val lit = litColor[r]
        val hl = r in highlighted
        val count = rm.getPrimitiveCount(inst)
        for (p in 0 until count) {
            val mi = rm.getMaterialInstanceAt(inst, p)
            val info = prims?.getOrNull(p) ?: PrimMaterial.DEFAULT
            var cr = info.color[0]
            var cg = info.color[1]
            var cb = info.color[2]
            var ca = info.color[3]
            var er = info.emissive[0]
            var eg = info.emissive[1]
            var eb = info.emissive[2]
            if (lit != null) {
                cr = max(cr, lit[0] * 0.8f); cg = max(cg, lit[1] * 0.8f); cb = max(cb, lit[2] * 0.8f)
                er += lit[0]; eg += lit[1]; eb += lit[2]
            }
            if (hl) {
                cr = cr * 0.45f + ACCENT[0] * 0.55f
                cg = cg * 0.45f + ACCENT[1] * 0.55f
                cb = cb * 0.45f + ACCENT[2] * 0.55f
                er += ACCENT[0] * 0.22f; eg += ACCENT[1] * 0.22f; eb += ACCENT[2] * 0.22f
            }
            if (transparentBody) ca = info.color[3] * 0.12f
            if (transparentGlass) ca = min(ca, 0.08f)
            val mat = mi.material
            if (mat.hasParameter("baseColorFactor")) mi.setParameter("baseColorFactor", cr, cg, cb, ca)
            if (mat.hasParameter("emissiveFactor")) mi.setParameter("emissiveFactor", er, eg, eb)
            if (info.blend) {
                // непрозрачный «кузов» с режимом BLEND пишет глубину, стекло и прозрачный кузов — нет
                mi.setDepthWrite(r in bodySet && !transparentBody && r !in glassSet)
            }
        }
    }

    private fun updateLights(): Boolean {
        val period = 0.70
        val phase = (time % period) < period / 2
        val blinkChanged = phase != blinkPhaseOn
        blinkPhaseOn = phase
        val anyBlink = config.lights.any { it.blink && lightOn[it.id] == true }
        if (!(blinkChanged && anyBlink) && !lightsDirty) return false
        lightsDirty = false
        val newLit = HashMap<Int, FloatArray>()
        for (l in config.lights) {
            if (lightOn[l.id] != true) continue
            if (l.blink && !blinkPhaseOn) continue
            for (n in l.nodes) {
                val col = l.perNodeColor[n] ?: l.color
                renderablesUnder[n]?.forEach { r ->
                    val cur = newLit[r] ?: FloatArray(3)
                    for (i in 0..2) cur[i] = max(cur[i], col[i] * l.intensity)
                    newLit[r] = cur
                }
            }
        }
        val affected = HashSet<Int>(litColor.keys)
        affected.addAll(newLit.keys)
        litColor.clear()
        litColor.putAll(newLit)
        affected.forEach { applyMaterial(it) }
        return true
    }

    // ------------------------------------------------------------------ команды

    fun setMode(newMode: ViewMode) {
        if (newMode == mode) return
        val leavingDisassembly = mode == ViewMode.DISASSEMBLY
        mode = newMode
        if (leavingDisassembly) assembleAll()
        if (newMode == ViewMode.UNDERSIDE) orbit.preset(CameraPreset.BOTTOM)
        applyModeVisibility()
        applyAllMaterials()
    }

    fun setPreset(p: CameraPreset) {
        orbit.preset(p)
        dirtyFrames = max(dirtyFrames, 2)
    }

    fun drag(dx: Float, dy: Float) = orbit.drag(dx, dy)
    fun zoom(factor: Float) = orbit.zoom(factor)

    fun setLight(id: String, on: Boolean) {
        lightOn[id] = on
        lightsDirty = true
    }

    fun isMovableOpen(id: String): Boolean = (movableById[id]?.moveTarget ?: 0f) > 0.5f

    fun setMovable(id: String, open: Boolean) {
        movableById[id]?.moveTarget = if (open) 1f else 0f
    }

    fun setGroupOpen(group: String, open: Boolean) {
        config.movables.filter { it.group == group }.forEach { setMovable(it.id, open) }
    }

    fun movableIdForNode(node: String): String? =
        config.movableForNode(node)?.id?.takeIf { movableById.containsKey(it) }

    fun select(node: String?) {
        val old = highlighted
        highlighted = HashSet(node?.let { renderablesUnder[it]?.toList() } ?: emptyList())
        (old + highlighted).forEach { applyMaterial(it) }
        dirtyFrames = max(dirtyFrames, 2)
    }

    /** Выделить деталь по id из базы знаний и навести на неё камеру. */
    fun focusPart(partId: String): PickResult? {
        val node = config.nodesForPart(partId).firstOrNull { renderablesUnder.containsKey(it) } ?: return null
        val b = config.binding(node) ?: return null
        select(node)
        worldCenterOf(node)?.let { orbit.focus(it, 3.2f) }
        return PickResult(node, b.partId, b.side)
    }

    fun hideNode(node: String) {
        renderablesUnder[node]?.let { userHidden.addAll(it.toList()) }
        applyVisibility()
    }

    fun isolateNode(node: String) {
        val keep = renderablesUnder[node]?.toSet() ?: return
        userHidden.clear()
        allRenderables.filter { it !in keep }.forEach { userHidden += it }
        applyVisibility()
        worldCenterOf(node)?.let { orbit.focus(it, 2.6f) }
    }

    fun showAll() {
        userHidden.clear()
        applyVisibility()
        orbit.focus(config.home.target, config.home.distance)
    }

    val hasHiddenByUser: Boolean get() = userHidden.isNotEmpty()

    fun disassembleNext() {
        if (mode != ViewMode.DISASSEMBLY || disStep >= config.disassembly.size) return
        val step = config.disassembly[disStep]
        step.nodes.forEach { dn -> nodeEntity[dn.node]?.let { e -> anims[e]?.disTarget = 1f } }
        disStep++
        listener.onDisassemblyChanged(disStep, config.disassembly.size, step.title)
    }

    fun assembleBack() {
        if (disStep <= 0) return
        disStep--
        val step = config.disassembly[disStep]
        step.nodes.forEach { dn ->
            renderablesUnder[dn.node]?.forEach { disHidden.remove(it) }
            nodeEntity[dn.node]?.let { e -> anims[e]?.disTarget = 0f }
        }
        applyVisibility()
        listener.onDisassemblyChanged(disStep, config.disassembly.size, config.disassembly.getOrNull(disStep - 1)?.title)
    }

    fun assembleAll() {
        while (disStep > 0) assembleBack()
    }

    /** Выбор детали касанием: луч из камеры и пересечение с ориентированными габаритами деталей. */
    fun pick(x: Float, y: Float): PickResult? {
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, viewProj, 0)) return null
        val nx = 2f * x / viewportW - 1f
        val ny = 1f - 2f * y / viewportH
        val near = unproject(inv, nx, ny, -1f)
        val far = unproject(inv, nx, ny, 1f)
        val dir = floatArrayOf(far[0] - near[0], far[1] - near[1], far[2] - near[2])

        var bestT = Float.MAX_VALUE
        var best: PartBinding? = null
        val world = FloatArray(16)
        val worldInv = FloatArray(16)
        val o4 = FloatArray(4)
        val d4 = FloatArray(4)
        for ((r, binding) in renderableBinding) {
            if (r !in inScene) continue
            if (mode == ViewMode.TRANSPARENT && (r in bodySet || r in glassSet)) continue
            val b = localBox[r] ?: continue
            val ti = tm.getInstance(r)
            if (ti == 0) continue
            tm.getWorldTransform(ti, world)
            if (!Matrix.invertM(worldInv, 0, world, 0)) continue
            Matrix.multiplyMV(o4, 0, worldInv, 0, floatArrayOf(near[0], near[1], near[2], 1f), 0)
            Matrix.multiplyMV(d4, 0, worldInv, 0, floatArrayOf(dir[0], dir[1], dir[2], 0f), 0)
            val t = rayBox(o4, d4, b) ?: continue
            if (t < bestT) {
                bestT = t
                best = binding
            }
        }
        return best?.let { PickResult(it.node, it.partId, it.side) }
    }

    private fun unproject(inv: FloatArray, nx: Float, ny: Float, nz: Float): FloatArray {
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, inv, 0, floatArrayOf(nx, ny, nz, 1f), 0)
        val w = if (abs(out[3]) < 1e-9f) 1f else out[3]
        return floatArrayOf(out[0] / w, out[1] / w, out[2] / w)
    }

    /** Пересечение луча (o + t·d, t∈[0,1]) с AABB в локальных координатах. */
    private fun rayBox(o: FloatArray, d: FloatArray, b: FloatArray): Float? {
        var tMin = 0f
        var tMax = 1f
        for (i in 0..2) {
            val lo = b[i] - b[i + 3] - 0.01f
            val hi = b[i] + b[i + 3] + 0.01f
            if (abs(d[i]) < 1e-9f) {
                if (o[i] < lo || o[i] > hi) return null
            } else {
                var t1 = (lo - o[i]) / d[i]
                var t2 = (hi - o[i]) / d[i]
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                if (tMin > tMax) return null
            }
        }
        return tMin
    }

    private fun worldCenterOf(node: String): FloatArray? {
        val rs = renderablesUnder[node] ?: return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        val world = FloatArray(16)
        val p = FloatArray(4)
        for (r in rs) {
            val b = localBox[r] ?: continue
            val ti = tm.getInstance(r)
            if (ti == 0) continue
            tm.getWorldTransform(ti, world)
            for (sx in -1..1 step 2) for (sy in -1..1 step 2) for (sz in -1..1 step 2) {
                Matrix.multiplyMV(p, 0, world, 0, floatArrayOf(b[0] + sx * b[3], b[1] + sy * b[4], b[2] + sz * b[5], 1f), 0)
                minX = min(minX, p[0]); minY = min(minY, p[1]); minZ = min(minZ, p[2])
                maxX = max(maxX, p[0]); maxY = max(maxY, p[1]); maxZ = max(maxZ, p[2])
            }
        }
        if (minX == Float.MAX_VALUE) return null
        return floatArrayOf((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
    }

    // ------------------------------------------------------------------ экранные точки

    private fun hotspotLocalPos(h: HotspotDef): FloatArray? {
        hotspotLocal[h]?.let { return it }
        val e = nodeEntity[h.node] ?: return null
        val ti = tm.getInstance(e)
        if (ti == 0) return null
        // точка задана в координатах модели в исходном положении → переводим в локальные координаты узла
        val a = anims[e]
        val world = FloatArray(16)
        tm.getWorldTransform(ti, world)
        if (a != null && (a.moveProgress > 0f || a.disProgress > 0f)) return null
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, world, 0)) return null
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, inv, 0, floatArrayOf(h.pos[0], h.pos[1], h.pos[2], 1f), 0)
        return out.also { hotspotLocal[h] = it }
    }

    private fun publishHotspots() {
        val list = ArrayList<HotspotScreen>()
        if (orbit.mode == OrbitCamera.Mode.ORBIT) {
            val world = FloatArray(16)
            val wp = FloatArray(4)
            val clip = FloatArray(4)
            for (h in config.hotspots) {
                val e = nodeEntity[h.node] ?: continue
                val rs = renderablesUnder[h.node] ?: continue
                if (rs.none { it in inScene }) continue
                val local = hotspotLocalPos(h) ?: continue
                val ti = tm.getInstance(e)
                tm.getWorldTransform(ti, world)
                Matrix.multiplyMV(wp, 0, world, 0, local, 0)
                Matrix.multiplyMV(clip, 0, viewProj, 0, floatArrayOf(wp[0], wp[1], wp[2], 1f), 0)
                if (clip[3] <= 0.01f) continue
                val sx = (clip[0] / clip[3] + 1f) * 0.5f * viewportW
                val sy = (1f - clip[1] / clip[3]) * 0.5f * viewportH
                if (sx < 0 || sy < 0 || sx > viewportW || sy > viewportH) continue
                list += HotspotScreen(h.partId, h.node, sx, sy)
            }
        }
        val same = list.size == lastHotspots.size && list.indices.all { i ->
            val a = list[i]
            val b = lastHotspots[i]
            a.node == b.node && abs(a.x - b.x) < 0.5f && abs(a.y - b.y) < 0.5f
        }
        if (!same) {
            lastHotspots = list
            listener.onHotspots(list)
        }
    }
}
