package me.senkuro.unofficial.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

/**
 * Сетевой перехватчик: статичные картинки перекодируются в WebP (quality ≈ 82) и ужимаются по ширине до [maxWidth],
 * поэтому в дисковый кэш Coil попадает уменьшенная версия. GIF, анимированный WebP, SVG и мелкие файлы не трогаем;
 * при любой ошибке отдаём оригинал.
 */
class ImageShrinkInterceptor(private val maxWidth: Int = 1080, private val quality: Int = 82) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val resp = chain.proceed(chain.request())
        val body = resp.body ?: return resp
        val type = body.contentType()?.toString().orEmpty().lowercase()
        if (!resp.isSuccessful || !type.startsWith("image/") || "gif" in type || "svg" in type) return resp
        val len = body.contentLength()
        if (len in 1..60_000 || len > 12_000_000) return resp
        val bytes = try { body.bytes() } catch (e: java.io.IOException) { throw e }
        val out = shrink(bytes)
        val newBody = if (out != null) out.toResponseBody("image/webp".toMediaType()) else bytes.toResponseBody(body.contentType())
        return resp.newBuilder().body(newBody).build()
    }

    private fun shrink(src: ByteArray): ByteArray? {
        try {
            // анимированный WebP: в заголовке есть чанк ANIM
            if (String(src, 0, minOf(src.size, 128), Charsets.ISO_8859_1).contains("ANIM")) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(src, 0, src.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            val scale = minOf(1f, maxWidth.toFloat() / w)
            val tw = (w * scale).toInt().coerceAtLeast(1)
            val th = (h * scale).toInt().coerceAtLeast(1)
            if (th > 16000 || tw > 16000) return null // предел размера WebP
            var sample = 1
            while (w / (sample * 2) >= tw) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(src, 0, src.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val bmp = if (decoded.width != tw || decoded.height != th) Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() } else decoded
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            val bos = ByteArrayOutputStream(src.size / 2)
            bmp.compress(fmt, quality, bos)
            bmp.recycle()
            val res = bos.toByteArray()
            return if (res.size < src.size * 0.9) res else null // не выиграли — оставляем оригинал
        } catch (_: Throwable) {
            return null
        }
    }
}
