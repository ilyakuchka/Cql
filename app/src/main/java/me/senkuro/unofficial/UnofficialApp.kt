package me.senkuro.unofficial

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import me.senkuro.unofficial.data.AppGraph
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class UnofficialApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        me.senkuro.unofficial.debugtools.DebugTools.installCrashHandler(this)
        AppGraph.init(this)
    }

    /** Повторяет запрос картинки при обрыве, таймауте и ответах 408/429/5xx (с нарастающей паузой). */
    private class RetryInterceptor(private val attempts: Int = 3) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var last: IOException? = null
            for (i in 0 until attempts) {
                if (chain.call().isCanceled()) throw IOException("Canceled")
                try {
                    val r = chain.proceed(chain.request())
                    val retry = r.code == 408 || r.code == 429 || r.code in 500..599
                    if (!retry || i == attempts - 1) return r
                    r.close()
                } catch (e: IOException) {
                    if (chain.call().isCanceled()) throw e
                    last = e
                    if (i == attempts - 1) throw e
                }
                try { Thread.sleep(400L * (i + 1)) } catch (_: InterruptedException) { throw IOException("Interrupted") }
            }
            throw last ?: IOException("Не удалось загрузить картинку")
        }
    }

    // Отдельный клиент для картинок: больше параллельных запросов на хост (стандартные 5 забивают ленты),
    // терпеливые таймауты и повторы — раньше зависший или оборванный запрос просто оставлял пустое место.
    private val imageClient: OkHttpClient by lazy {
        AppGraph.httpClient.newBuilder()
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    // Картинки грузим тем же OkHttp-клиентом (Referer / User-Agent из SenkuroConfig).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                // GIF и анимированный WebP: ImageDecoder на API 28+, на старых — GifDecoder (только GIF).
                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil3.gif.AnimatedImageDecoder.Factory()) else add(coil3.gif.GifDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(256L * 1024 * 1024).build() }
            .build()
}
