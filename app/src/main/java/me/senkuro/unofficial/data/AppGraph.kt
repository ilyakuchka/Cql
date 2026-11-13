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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Простейший сервис-локатор: один клиент, API, репозиторий, вход и локальная библиотека на процесс. */
object AppGraph {
    /** Название метки, выбранной на странице тайтла: каталог откроется с ней в фильтре. */
    val pendingCatalogLabel = MutableStateFlow<String?>(null)

    lateinit var httpClient: OkHttpClient
        private set
    lateinit var repository: SenkuroRepository
        private set
    lateinit var social: SocialRepository
        private set
    lateinit var cards: CardsRepository
        private set
    lateinit var offers: OffersRepository
        private set
    lateinit var eco: EcosystemRepository
    lateinit var title: TitleRepository
        private set
    lateinit var directory: DirectoryRepository
        private set
    lateinit var posts: PostsRepository
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

    private val _streamer = MutableStateFlow(false)

    /** Переключатель «Режим стримера» из настроек (само по себе ещё не включает режим: см. [streamerActive]). */
    val streamerMode: StateFlow<Boolean> = _streamer.asStateFlow()

    /**
     * Режим стримера действует, только если переключатель включён и пользователь вошёл. От темы он не зависит.
     * После выхода личные данные снова показываются, а сам переключатель сохраняется и вернётся в силу при входе.
     */
    lateinit var streamerActive: StateFlow<Boolean>
        private set

    private val _unread = MutableStateFlow(0)

    /** Число непрочитанных уведомлений (0, если не вошли). */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _marks = MutableStateFlow<Map<String, String>>(emptyMap())

    /** id тайтла -> название коллекции закладок (для подписей на постерах). */
    val marks: StateFlow<Map<String, String>> = _marks.asStateFlow()

    private var marksJob: Job? = null

    /** Загружает все закладки аккаунта, чтобы подписывать постеры («Брошено», «Прочитано»…). */
    fun refreshMarks() {
        marksJob?.cancel()
        val acc = _account.value
        if (acc == null) {
            _marks.value = emptyMap()
            return
        }
        val names = acc.collections.associate { it.id to it.name }
        marksJob = scope.launch {
            try {
                val result = HashMap<String, String>()
                var cursor: String? = null
                var pages = 0
                do {
                    val page = repository.bookmarks(acc.userId, null, cursor)
                    page.items.forEach { e -> e.collectionId?.let { names[it] }?.let { result[e.manga.id] = it } }
                    cursor = page.next
                    pages++
                } while (cursor != null && pages < 60)
                _marks.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Локально обновляет подпись после изменения закладки (name == null — закладка снята). */
    fun setMark(mangaId: String, name: String?) {
        _marks.value = if (name == null) _marks.value - mangaId else _marks.value + (mangaId to name)
    }

    private val _account = MutableStateFlow<Account?>(null)
    private val _accountError = MutableStateFlow<String?>(null)

    /** Текущий домен; экраны подписываются и перезагружают данные при смене. */
    val domain: StateFlow<Domain> = _domain.asStateFlow()

    /** Аккаунт на сайте (null — не вошли или ещё загружается). */
    val account: StateFlow<Account?> = _account.asStateFlow()
    val accountError: StateFlow<String?> = _accountError.asStateFlow()

    fun init(app: Application) {
        Updater.init(app)
        settings = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        SenkuroConfig.current = SenkuroConfig.byId(settings.getString(KEY_DOMAIN, null))
        _domain.value = SenkuroConfig.current

        _themeDark.value = settings.getBoolean(KEY_DARK, false)
        _streamer.value = settings.getBoolean(KEY_STREAMER, false)
        auth = AuthStore(app)
        streamerActive = combine(_streamer, auth.token) { on, token -> on && token != null }
            .stateIn(scope, SharingStarted.Eagerly, false)
        httpClient = createHttpClient { auth.current }
        val api = SenkuroApi(app, httpClient)
        repository = SenkuroRepository(api)
        social = SocialRepository(api)
        cards = CardsRepository(api)
        offers = OffersRepository(api)
        eco = EcosystemRepository(api)
        title = TitleRepository(api)
        directory = DirectoryRepository(api)
        posts = PostsRepository(api)
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
        _unread.value = 0
        _domain.value = domain
        refreshAccount()
    }

    /** Показано ли стартовое предупреждение о неофициальности приложения. */
    fun disclaimerSeen(): Boolean = settings.getBoolean("disclaimer_seen", false)

    fun markDisclaimerSeen() {
        settings.edit().putBoolean("disclaimer_seen", true).apply()
    }

    fun setDarkTheme(dark: Boolean) {
        settings.edit().putBoolean(KEY_DARK, dark).apply()
        _themeDark.value = dark
    }

    fun setStreamerMode(on: Boolean) {
        settings.edit().putBoolean(KEY_STREAMER, on).apply()
        _streamer.value = on
    }

    fun refreshUnread() {
        if (auth.current == null) {
            _unread.value = 0
            return
        }
        scope.launch {
            try {
                _unread.value = repository.unreadNotifications()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Загружает аккаунт по сохранённому токену. */
    fun refreshAccount() {
        accountJob?.cancel()
        _accountError.value = null
        if (auth.current == null) {
            _account.value = null
            _marks.value = emptyMap()
            return
        }
        accountJob = scope.launch {
            try {
                val account = repository.viewer()
                if (account == null) auth.clear()
                _account.value = account
                if (account != null) {
                    refreshUnread()
                    refreshMarks()
                }
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
                refreshUnread()
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
        _unread.value = 0
        // Сессия сайта в WebView тоже сбрасывается, иначе следующий вход подхватит старый аккаунт.
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    private const val KEY_DOMAIN = "domain"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_STREAMER = "streamer_mode"
}
