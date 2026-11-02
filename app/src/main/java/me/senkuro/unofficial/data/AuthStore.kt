package me.senkuro.unofficial.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Токен входа на сайте (отдельный для каждого домена). Хранится в приватных настройках приложения. */
class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val _token = MutableStateFlow(read())

    val token: StateFlow<String?> = _token.asStateFlow()
    val current: String? get() = _token.value

    private fun key() = "token_${SenkuroConfig.current.id}"
    private fun read(): String? = prefs.getString(key(), null)?.takeIf { it.isNotBlank() }

    fun save(token: String) {
        prefs.edit().putString(key(), token).apply()
        _token.value = token
    }

    fun clear() {
        prefs.edit().remove(key()).apply()
        _token.value = null
    }

    /** Вызывать после смены SenkuroConfig.current. */
    fun onDomainChanged() {
        _token.value = read()
    }
}
