package ru.rezzo.academy.feature.car

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.rezzo.academy.scene3d.CarSceneController
import ru.rezzo.academy.scene3d.ModelSource
import ru.rezzo.academy.scene3d.SceneConfig

/**
 * Встраивает 3D-сцену (SurfaceView + Filament) в Compose.
 * Связь только через [CarSceneController]: интерфейс не знает, как устроен рендер.
 */
@Composable
fun CarViewport(
    config: SceneConfig,
    source: ModelSource,
    listener: CarSceneController.Listener,
    onController: (CarSceneController?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { arrayOfNulls<CarSceneController>(1) }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                val controller = CarSceneController(ctx, sv, config, source, listener)
                holder[0] = controller
                sv.post { onController(controller) }
            }
        },
        modifier = modifier,
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.resume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder[0]?.destroy()
            holder[0] = null
            onController(null)
        }
    }
}
