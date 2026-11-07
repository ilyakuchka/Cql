package me.senkuro.unofficial.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Зацикленное беззвучное видео без управления — так сайт показывает анимированные баннеры, обои и аватарки
 * (BaseImageAnimation.vue: <video autoplay muted loop> с mp4/webm-вариантом нужной ширины). Это в разы легче GIF:
 * кадры декодирует видеодекодер, а не процессор. Если видео не запустилось, вызывается [fallback].
 * crop = true — заполняет область с обрезкой (как object-fit: cover), false — вписывает целиком.
 */
@Composable
fun LoopVideo(url: String, modifier: Modifier = Modifier, crop: Boolean = true, fallback: @Composable () -> Unit = {}) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) { fallback(); return }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                var player: MediaPlayer? = null
                var vw = 0
                var vh = 0
                fun transform() {
                    if (vw <= 0 || vh <= 0 || width <= 0 || height <= 0) return
                    val videoAspect = vw.toFloat() / vh
                    val viewAspect = width.toFloat() / height
                    val wider = videoAspect >= viewAspect
                    val m = Matrix()
                    // TextureView растягивает видео на всю область — возвращаем пропорции.
                    val sx: Float
                    val sy: Float
                    if (crop) { sx = if (wider) videoAspect / viewAspect else 1f; sy = if (wider) 1f else viewAspect / videoAspect }
                    else { sx = if (wider) 1f else videoAspect / viewAspect; sy = if (wider) viewAspect / videoAspect else 1f }
                    m.setScale(sx, sy, width / 2f, height / 2f)
                    setTransform(m)
                }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val p = MediaPlayer()
                        player = p
                        try {
                            p.setSurface(Surface(st))
                            p.setDataSource(url)
                            p.isLooping = true
                            p.setVolume(0f, 0f)
                            p.setOnVideoSizeChangedListener { _, w2, h2 -> vw = w2; vh = h2; transform() }
                            p.setOnPreparedListener { it.start() }
                            p.setOnErrorListener { _, _, _ -> failed = true; true }
                            p.prepareAsync()
                        } catch (_: Exception) {
                            failed = true
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = transform()
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { player?.release() }
                        player = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
    )
}
