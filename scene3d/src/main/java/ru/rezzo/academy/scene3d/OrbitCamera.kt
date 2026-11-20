package ru.rezzo.academy.scene3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

enum class CameraPreset(val title: String) {
    HOME("Общий вид"),
    FRONT("Спереди"),
    REAR("Сзади"),
    LEFT("Слева"),
    RIGHT("Справа"),
    TOP("Сверху"),
    BOTTOM("Снизу"),
    INTERIOR("Из салона"),
}

/**
 * Камера: орбита вокруг точки или вид от первого лица (из салона).
 * Плавно догоняет целевые значения. Математика своя, чтобы те же матрицы
 * использовать для выбора деталей касанием и для экранных точек (hotspots).
 *
 * Оси: X — вперёд, Y — вверх, Z — влево. yaw = 0 — камера спереди машины.
 */
class OrbitCamera(home: OrbitPose, private val interiorPose: FirstPersonPose) {
    enum class Mode { ORBIT, FIRST_PERSON }

    var mode = Mode.ORBIT
        private set

    private val homePose = home

    // орбита: текущие и целевые значения
    private val target = home.target.copyOf()
    private val targetGoal = home.target.copyOf()
    private var yaw = home.yaw
    private var yawGoal = home.yaw
    private var pitch = home.pitch
    private var pitchGoal = home.pitch
    private var distance = home.distance
    private var distanceGoal = home.distance

    // вид из салона
    private var lookYaw = interiorPose.yaw
    private var lookYawGoal = interiorPose.yaw
    private var lookPitch = interiorPose.pitch
    private var lookPitchGoal = interiorPose.pitch

    // плавный переход между режимами
    private val fromEye = FloatArray(3)
    private val fromTarget = FloatArray(3)
    private var blend = 1f

    val eye = FloatArray(3)
    val lookAt = FloatArray(3)

    val minDistance = 1.6f
    val maxDistance = 14f

    init {
        compute(eye, lookAt)
    }

    fun preset(p: CameraPreset) {
        if (p == CameraPreset.INTERIOR) {
            startBlend()
            mode = Mode.FIRST_PERSON
            lookYawGoal = interiorPose.yaw
            lookPitchGoal = interiorPose.pitch
            lookYaw = lookYawGoal
            lookPitch = lookPitchGoal
            return
        }
        if (mode == Mode.FIRST_PERSON) {
            startBlend()
            mode = Mode.ORBIT
        }
        val (y, pt) = when (p) {
            CameraPreset.HOME -> homePose.yaw to homePose.pitch
            CameraPreset.FRONT -> 0f to 8f
            CameraPreset.REAR -> 180f to 8f
            CameraPreset.LEFT -> 90f to 6f
            CameraPreset.RIGHT -> -90f to 6f
            CameraPreset.TOP -> 90f to 86f
            CameraPreset.BOTTOM -> 90f to -72f
            CameraPreset.INTERIOR -> 0f to 0f
        }
        yawGoal = nearestAngle(yaw, y)
        pitchGoal = pt
        homePose.target.copyInto(targetGoal)
        distanceGoal = homePose.distance
    }

    /** Навести орбиту на точку (например, на выбранную деталь). */
    fun focus(point: FloatArray, dist: Float) {
        if (mode == Mode.FIRST_PERSON) {
            startBlend()
            mode = Mode.ORBIT
        }
        point.copyInto(targetGoal)
        distanceGoal = dist.coerceIn(minDistance, maxDistance)
    }

    fun drag(dxPx: Float, dyPx: Float) {
        if (mode == Mode.ORBIT) {
            yawGoal += dxPx * 0.28f
            pitchGoal = (pitchGoal + dyPx * 0.28f).coerceIn(-85f, 87f)
        } else {
            lookYawGoal += dxPx * 0.18f
            lookPitchGoal = (lookPitchGoal + dyPx * 0.18f).coerceIn(-70f, 60f)
        }
    }

    fun zoom(factor: Float) {
        if (mode == Mode.ORBIT && factor > 0f) {
            distanceGoal = (distanceGoal / factor).coerceIn(minDistance, maxDistance)
        }
    }

    /** @return true, если камера сдвинулась */
    fun update(dt: Float): Boolean {
        val k = 1f - exp(-dt * 9f)
        var moved = false
        fun step(cur: Float, goal: Float): Float {
            val d = goal - cur
            if (kotlin.math.abs(d) < 1e-4f) return goal
            moved = true
            return cur + d * k
        }
        yaw = step(yaw, yawGoal)
        pitch = step(pitch, pitchGoal)
        distance = step(distance, distanceGoal)
        for (i in 0..2) target[i] = step(target[i], targetGoal[i])
        lookYaw = step(lookYaw, lookYawGoal)
        lookPitch = step(lookPitch, lookPitchGoal)

        val e = FloatArray(3)
        val t = FloatArray(3)
        compute(e, t)
        if (blend < 1f) {
            blend = (blend + dt / 0.6f).coerceAtMost(1f)
            val s = smooth(blend)
            for (i in 0..2) {
                e[i] = fromEye[i] + (e[i] - fromEye[i]) * s
                t[i] = fromTarget[i] + (t[i] - fromTarget[i]) * s
            }
            moved = true
        }
        e.copyInto(eye)
        t.copyInto(lookAt)
        return moved
    }

    private fun compute(e: FloatArray, t: FloatArray) {
        if (mode == Mode.ORBIT) {
            val y = Math.toRadians(yaw.toDouble())
            val p = Math.toRadians(pitch.toDouble())
            e[0] = target[0] + (distance * cos(p) * cos(y)).toFloat()
            e[1] = target[1] + (distance * sin(p)).toFloat()
            e[2] = target[2] + (distance * cos(p) * sin(y)).toFloat()
            target.copyInto(t)
        } else {
            interiorPose.eye.copyInto(e)
            val y = Math.toRadians(lookYaw.toDouble())
            val p = Math.toRadians(lookPitch.toDouble())
            t[0] = e[0] + (cos(p) * cos(y)).toFloat()
            t[1] = e[1] + sin(p).toFloat()
            t[2] = e[2] + (cos(p) * sin(y)).toFloat()
        }
    }

    private fun startBlend() {
        eye.copyInto(fromEye)
        lookAt.copyInto(fromTarget)
        blend = 0f
    }

    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    private fun nearestAngle(current: Float, wanted: Float): Float {
        var d = (wanted - current) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return current + d
    }

    @Suppress("unused")
    private val tau = (2 * PI).toFloat()
}
