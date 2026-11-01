package me.senkuro.unofficial.data

import android.app.Application
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/** Простейший сервис-локатор: один клиент, API, репозиторий и локальная библиотека на процесс. */
object AppGraph {
    lateinit var httpClient: OkHttpClient
        private set
    lateinit var repository: SenkuroRepository
        private set
    lateinit var library: LibraryStore
        private set

    private lateinit var settings: SharedPreferences
    private val _domain = MutableStateFlow(SenkuroConfig.current)

    /** Текущий домен; экраны подписываются и перезагружают данные при смене. */
    val domain: StateFlow<Domain> = _domain.asStateFlow()

    fun init(app: Application) {
        settings = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        SenkuroConfig.current = SenkuroConfig.byId(settings.getString(KEY_DOMAIN, null))
        _domain.value = SenkuroConfig.current

        httpClient = createHttpClient()
        repository = SenkuroRepository(SenkuroApi(app, httpClient))
        library = LibraryStore(app)
    }

    fun setDomain(domain: Domain) {
        if (domain.id == SenkuroConfig.current.id) return
        settings.edit().putString(KEY_DOMAIN, domain.id).apply()
        SenkuroConfig.current = domain
        repository.clearCaches()
        _domain.value = domain
    }

    private const val KEY_DOMAIN = "domain"
}
