package me.senkuro.unofficial

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import me.senkuro.unofficial.data.AppGraph

class UnofficialApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        me.senkuro.unofficial.debugtools.DebugTools.installCrashHandler(this)
        AppGraph.init(this)
    }

    // Картинки грузим тем же OkHttp-клиентом (Referer / User-Agent из SenkuroConfig).
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { AppGraph.httpClient }))
                // GIF и анимированный WebP: ImageDecoder на API 28+, на старых — GifDecoder (только GIF).
                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil3.gif.AnimatedImageDecoder.Factory()) else add(coil3.gif.GifDecoder.Factory())
            }
            .build()
}
