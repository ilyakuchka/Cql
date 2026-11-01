package me.senkuro.unofficial.data

import android.app.Application
import okhttp3.OkHttpClient

/** Простейший сервис-локатор: один клиент, API, репозиторий и локальная библиотека на процесс. */
object AppGraph {
    lateinit var httpClient: OkHttpClient
        private set
    lateinit var repository: SenkuroRepository
        private set
    lateinit var library: LibraryStore
        private set

    fun init(app: Application) {
        httpClient = createHttpClient()
        repository = SenkuroRepository(SenkuroApi(app, httpClient))
        library = LibraryStore(app)
    }
}
