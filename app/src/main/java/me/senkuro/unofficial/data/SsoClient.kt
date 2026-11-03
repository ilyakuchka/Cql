package me.senkuro.unofficial.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Нативный вход через SSO (протокол восстановлен по js sso.senkuro.net и senkuro.me):
 *  1) GET  /api/v1/captcha-config — какая капча и её site key;
 *  2) капчу проходит WebView (токен возвращается в приложение);
 *  3) POST /api/v1/auth/login {email, password, captcha, fingerprint, two_factor_code} — ставит сессионную куку SSO;
 *  4) GET  /oauth/authorize (PKCE S256) — редирект на redirect_uri с code;
 *  5) POST /oauth/token — обмен code на access_token.
 */
object SsoClient {
    const val SSO = "https://sso.senkuro.net"
    const val REDIRECT = "https://senkuro.me/auth/oauth/callback"

    class CaptchaConfig(val service: String, val siteKey: String)

    /** code — код ошибки SSO (4101 — неверный пароль, 4104 — нужен код 2FA и т.д.). */
    class SsoException(val code: Int?, message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val deviceHash: String by lazy { ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) } }

    private val jar = object : CookieJar {
        private val store = ArrayList<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { c ->
                store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                if (c.expiresAt > System.currentTimeMillis()) store.add(c)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.filter { it.matches(url) }

        @Synchronized
        fun clear() = store.clear()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val errors = mapOf(
        4150 to "Невалидная почта",
        4101 to "Неправильный логин или пароль",
        4102 to "Вы не можете авторизоваться в аккаунт бота.",
        4103 to "Нужно подтвердить почту: войдите через сайт",
        4104 to "Нужен код двухфакторной защиты",
        4105 to "Неправильный двухфакторный код",
        4109 to "Вы уже авторизованы.",
    )

    private fun request(url: String) = Request.Builder().url(url)
        .header("User-Agent", SenkuroConfig.USER_AGENT)
        .header("Origin", SSO)
        .header("Referer", "$SSO/")

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun parseError(body: String, http: Int): SsoException {
        val err = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
        val code = (err?.get("code") as? JsonPrimitive)?.intOrNull
        val message = errors[code] ?: err?.get("message").str() ?: "Ошибка SSO (HTTP $http)"
        return SsoException(code, message)
    }

    suspend fun captchaConfig(): CaptchaConfig = withContext(Dispatchers.IO) {
        client.newCall(request("$SSO/api/v1/captcha-config").get().build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw parseError(body, r.code)
            val root = json.parseToJsonElement(body).jsonObject
            val data = (root["data"] as? JsonObject) ?: root
            val service = data["prefer_service"].str() ?: "turnstile"
            val key = (data["services"] as? JsonObject)?.get(service)?.jsonObject?.get("site_key").str()
                ?: throw SsoException(null, "SSO не сообщил ключ капчи")
            CaptchaConfig(service, key)
        }
    }

    /** Полный вход: возвращает access_token. captchaToken получен из WebView. */
    suspend fun login(
        email: String,
        password: String,
        captcha: CaptchaConfig,
        captchaToken: String,
        twoFactor: String?,
    ): String = withContext(Dispatchers.IO) {
        jar.clear()
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("captcha", buildJsonObject { put(captcha.service, captchaToken) })
            put("fingerprint", buildJsonObject {
                put("application", "Chrome")
                put("version", "124.0")
                put("platform", "mobile")
                put("device", "Android")
                put("hash", deviceHash)
            })
            if (twoFactor.isNullOrBlank()) put("two_factor_code", JsonNull) else put("two_factor_code", twoFactor)
        }.toString()
        client.newCall(
            request("$SSO/api/v1/auth/login").post(body.toRequestBody("application/json".toMediaType())).build(),
        ).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw parseError(text, r.code)
        }
        exchange()
    }

    private fun randomString(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** OAuth code + PKCE с уже установленной сессией SSO, затем обмен на токен. */
    private fun exchange(): String {
        val verifier = randomString(43)
        val state = randomString(43)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        var url: HttpUrl = "$SSO/oauth/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", SenkuroConfig.APP_ID)
            .addQueryParameter("redirect_uri", REDIRECT)
            .addQueryParameter("scope", "identify email")
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()

        var code: String? = null
        repeat(8) {
            if (code != null) return@repeat
            client.newCall(request(url.toString()).get().build()).execute().use { r ->
                val location = r.header("Location")
                if (r.code in 300..399 && location != null) {
                    val next = url.resolve(location) ?: throw SsoException(null, "Некорректный редирект SSO")
                    if (next.toString().startsWith(REDIRECT)) {
                        if (next.queryParameter("state") != state) throw SsoException(null, "Не совпал state")
                        code = next.queryParameter("code") ?: throw SsoException(null, next.queryParameter("error") ?: "SSO не вернул code")
                    } else {
                        url = next
                    }
                } else {
                    throw SsoException(null, "SSO просит подтверждение: войдите через сайт")
                }
            }
        }
        val authCode = code ?: throw SsoException(null, "SSO не вернул code")

        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", authCode)
            .add("redirect_uri", REDIRECT)
            .add("client_id", SenkuroConfig.APP_ID)
            .add("code_verifier", verifier)
            .build()
        client.newCall(request("$SSO/oauth/token").post(form).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw SsoException(null, "Не удалось получить токен: ${text.take(200)}")
            return json.parseToJsonElement(text).jsonObject["access_token"].str()
                ?: throw SsoException(null, "В ответе нет access_token")
        }
    }
}
