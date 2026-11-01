# Senkuro Unofficial

Неофициальное нативное Android-приложение для чтения манги с [senkuro.me](https://senkuro.me).
Пакет: `me.senkuro.unofficial`. Kotlin + Jetpack Compose, данные берутся из GraphQL `https://api.senkuro.me/graphql`.

## Возможности

- Главная: свежие обновления, популярное за день, новые тайтлы, рекомендации; сверху переключатель домена (senkuro.me / sso.senkuro.net)
- Каталог: поиск, сортировка, фильтры (тип, формат, статус, статус перевода, возраст, метки; включить/исключить)
- Страница тайтла: описание, метки, ветки переводов, список глав, «Продолжить чтение»
- Ридер: вертикальная лента страниц, переход к предыдущей/следующей главе
- Локальная библиотека: избранное, история, прочитанные главы (хранятся на устройстве)

## GraphQL-запросы

Все запросы лежат в [`app/src/main/assets/graphql`](app/src/main/assets/graphql) и загружаются по имени файла:

| Файл | Назначение |
| --- | --- |
| `searchTachiyomiManga` | каталог и поиск (offset-пагинация, по 10) |
| `fetchTachiyomiSearchFilters` | список меток для фильтров |
| `fetchTachiyomiManga` | карточка тайтла |
| `fetchTachiyomiChapters` | главы, ветки и команды |
| `fetchTachiyomiChapterPages` | страницы главы |
| `readerChapter` | запасной вариант получения страниц по slug главы |
| `homeLatest`, `homeNew`, `homePopular`, `homeRecommendations` | секции главной |

Запросы вынесены из приложенных расширений Senkuro (Tachiyomi/Aidoku). Заголовки клиента и адреса — в
[`SenkuroConfig.kt`](app/src/main/java/me/senkuro/unofficial/data/SenkuroConfig.kt).

## Сборка

Только вручную: **Actions → Android build → Run workflow**, в поле `build_type` выберите `debug` или `release`.
APK появится в артефактах запуска (`me.senkuro.unofficial-debug` / `me.senkuro.unofficial-release`).

### Отладочные инструменты (только debug)

В debug-сборке на вкладке «Библиотека» есть кнопка «Отладка: перехват запросов сайта». Она открывает senkuro.me
во встроенном браузере и записывает все GraphQL-вызовы (операция, запрос, переменные, ответ).
Пароли, токены и cookie вырезаются. Лог можно скопировать или отправить файлом («Поделиться»).

В release-сборке этого нет:
- код перехвата лежит в `app/src/debug`, в `app/src/release` только пустая заглушка;
- `BuildConfig.DEBUG_TOOLS` равен `true` в debug и `false` в release, кнопка скрыта;
- библиотека `androidx.webkit` подключена только как `debugImplementation`;
- workflow при `release` проверяет APK и падает, если находит в нём код перехвата.

### Подпись release (необязательно)

Если в секретах репозитория заданы `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, release подписывается вашим ключом. Без них он подписывается debug-ключом: ставится,
но обновить поверх другой сборки нельзя.
