package me.senkuro.unofficial.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Токен входа на сайте. Один на все домены (senkuro.me / senkuro.com): вход, выполненный на любом из них, действует
 * на другом. Хранится в приватных настройках приложения.
 */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val _token = MutableStateFlow(read())

    val token: StateFlow<String?> = _token.asStateFlow()
    val current: String? get() = _token.value

    // Раньше токен хранился отдельно по доменам («token_me»): подхватываем его, чтобы пользователю не входить заново.
    private fun read(): String? =
        (prefs.getString(KEY, null) ?: prefs.getString(LEGACY_KEY_ME, null))?.takeIf { it.isNotBlank() }

    fun save(token: String) {
        prefs.edit().putString(KEY, token).remove(LEGACY_KEY_ME).apply()
        _token.value = token
    }

    fun clear() {
        prefs.edit().remove(KEY).remove(LEGACY_KEY_ME).apply()
        _token.value = null
    }

    /** Вызывается после смены домена. Токен общий, поэтому остаётся прежним. */
    fun onDomainChanged() {
        _token.value = read()
    }

    private companion object {
        const val KEY = "token"
        const val LEGACY_KEY_ME = "token_me"
    }
}
