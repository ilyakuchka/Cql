package me.senkuro.unofficial.data

import android.app.Application
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Простейший сервис-локатор: один клиент, API, репозиторий, вход и локальная библиотека на процесс. */
object AppGraph {
    lateinit var httpClient: OkHttpClient
        private set
    lateinit var repository: SenkuroRepository
        private set
    lateinit var library: LibraryStore
        private set
    lateinit var auth: AuthStore
        private set

    private lateinit var settings: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var accountJob: Job? = null

    private val _domain = MutableStateFlow(SenkuroConfig.current)
    private val _themeDark = MutableStateFlow(false)

    /** Тёмная тема для обычных страниц (страница тайтла и ридер всегда тёмные). */
    val themeDark: StateFlow<Boolean> = _themeDark.asStateFlow()

    private val _account = MutableStateFlow<Account?>(null)
    private val _accountError = MutableStateFlow<String?>(null)

    /** Текущий домен; экраны подписываются и перезагружают данные при смене. */
    val domain: StateFlow<Domain> = _domain.asStateFlow()

    /** Аккаунт на сайте (null — не вошли или ещё загружается). */
    val account: StateFlow<Account?> = _account.asStateFlow()
    val accountError: StateFlow<String?> = _accountError.asStateFlow()

    fun init(app: Application) {
        settings = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        SenkuroConfig.current = SenkuroConfig.byId(settings.getString(KEY_DOMAIN, null))
        _domain.value = SenkuroConfig.current

        _themeDark.value = settings.getBoolean(KEY_DARK, false)
        auth = AuthStore(app)
        httpClient = createHttpClient { auth.current }
        repository = SenkuroRepository(SenkuroApi(app, httpClient))
        library = LibraryStore(app)
        refreshAccount()
    }

    fun setDomain(domain: Domain) {
        if (domain.id == SenkuroConfig.current.id) return
        settings.edit().putString(KEY_DOMAIN, domain.id).apply()
        SenkuroConfig.current = domain
        repository.clearCaches()
        auth.onDomainChanged()
        _account.value = null
        _domain.value = domain
        refreshAccount()
    }

    fun setDarkTheme(dark: Boolean) {
        settings.edit().putBoolean(KEY_DARK, dark).apply()
        _themeDark.value = dark
    }

    /** Загружает аккаунт по сохранённому токену. */
    fun refreshAccount() {
        accountJob?.cancel()
        _accountError.value = null
        if (auth.current == null) {
            _account.value = null
            return
        }
        accountJob = scope.launch {
            try {
                val account = repository.viewer()
                if (account == null) auth.clear()
                _account.value = account
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _accountError.value = e.message ?: "Ошибка сети"
            }
        }
    }

    /** Проверяет токен, найденный после входа на сайте; при успехе запоминает его. */
    suspend fun tryLogin(token: String): Boolean {
        val previous = auth.current
        auth.save(token)
        return try {
            val account = repository.viewer()
            if (account == null) {
                restore(previous)
                false
            } else {
                _account.value = account
                _accountError.value = null
                true
            }
        } catch (e: CancellationException) {
            restore(previous)
            throw e
        } catch (e: Exception) {
            restore(previous)
            false
        }
    }

    private fun restore(previous: String?) {
        if (previous == null) auth.clear() else auth.save(previous)
    }

    fun logout() {
        accountJob?.cancel()
        auth.clear()
        _account.value = null
        _accountError.value = null
        // Сессия сайта в WebView тоже сбрасывается, иначе следующий вход подхватит старый аккаунт.
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    private const val KEY_DOMAIN = "domain"
    private const val KEY_DARK = "dark_theme"
}
