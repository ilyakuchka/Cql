# Senkuro Unofficial

Неофициальное нативное Android-приложение для чтения манги с [senkuro.me](https://senkuro.me).
Пакет: `me.senkuro.unofficial`. Kotlin + Jetpack Compose, данные берутся из GraphQL `https://api.senkuro.me/graphql`.

## Возможности

- Главная: свежие обновления, популярное за день, новые тайтлы, рекомендации
- Каталог: поиск, сортировка, фильтры (тип, формат, статус, статус перевода, возраст, метки; включить/исключить)
- Страница тайтла: описание, метки, ветки переводов, список глав, «Продолжить чтение»
- Ридер: вертикальная лента страниц, переход к предыдущей/следующей главе
- Уведомления аккаунта (колокольчик на главной со счётчиком, «Новые» и «Прочитанные»)
- Вход на сайт (через WebView, с капчей) и серверные закладки: вкладка «Закладки», коллекции, добавление/удаление со страницы тайтла (звезда)
- Закладки сайта вместо локального избранного и истории; прочитанные главы отмечаются на устройстве

## Дизайн

Оформление адаптировано из UI-кита Figma «Webtoon App UI (Free UI Kit)»: светлые страницы (главная, поиск, библиотека) с текстом `#3C3C3C`,
серым `#8C8C8C`, линиями `#E0E0E0` и фоном полей `#F8F8F8`; страница тайтла и ридер тёмные (`#161616`, строки глав `#2C2C2C`, линии `#464545`);
акцент зелёный `#00DC64`. Главная: пронумерованные строки «Trending» по три в колонке и ряды постеров со скруглением 4;
нижняя панель белая с линией сверху. Страница тайтла: обложка под затемнением, жанр, название, автор, кнопка-«таблетка», строки глав с миниатюрой и «#N».
Картинки и обложки из кита в приложение не копировались, шрифт системный вместо Arial.

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
| `fetchViewer` | аккаунт и коллекции закладок |
| `fetchViewerUnreadNotifications`, `fetchNotificationFeeds` | уведомления |
| `bulkReadNotifications`, `bulkDeleteNotifications` | «Прочитать все», удаление уведомлений |
| `fetchUser`, `fetchUserAchievements` | экран профиля (аватар в шапке) и достижения |
| SSO `/api/v1/captcha-config`, `/api/v1/auth/login`, `/oauth/authorize`, `/oauth/token` | нативный вход: форма почта/пароль/2FA, капча в WebView, OAuth+PKCE делает приложение |
| `fetchMangaChapters` | добор глав, которых нет в tachiyomi-ответе |
| `fetchThreadComments`, `createComment`, `updateComment`, `deleteComment` | комментарии к странице в ридере |
| `fetchConversations`, `fetchConversationMessages`, `createConversationMessage`, `readConversationMessage`, `bulkDeleteConversationMessages` | личная переписка (Настройки → Сообщения) |
| `fetchGuilds`, `fetchGuild`, `fetchGuildMembers` | гильдии (Настройки → Гильдии) |
| `fetchUserCards` | мои карточки (Настройки → Мои карточки) |
| `fetchCards`, `fetchCard`, `viewCard` | каталог карточек (Настройки → Каталог карточек), страница карточки |
| `fetchCharacter`, `fetchMangas` | страница персонажа: тайтлы и карточки (нажатие на персонажа в тайтле или карточке) |
| `fetchUserTasks` | задания (Настройки → Задания) |
| `fetchCardOffers`, `acceptCardOffer`, `closeCardOffer` | обмены карточек (Настройки → Обмены карточек) |
| `fetchMainUserTops` | лента топов дня на главной |
| `fetchMainAd`, `viewAd` | тонкий рекламный баннер на главной под «Популярным за день» |
| `voteComment`, `deleteCommentVote` | лайк/дизлайк комментария |
| `fetchMangaBookmarkChapter` | кнопка «К закладке» в тайтле (`viewerBookmark`) |
| `createUserBookmarkCollection`, `updateUserBookmarkCollection`, `deleteUserBookmarkCollection` | свои коллекции закладок |
| `search` | поиск по сайту: тайтлы, персонажи, люди, команды, гильдии, пользователи |
| `fetchTeams`, `fetchTeam`, `fetchTeamMembers`, `viewTeam` | команды переводчиков |
| `fetchPerson`, `fetchMangaStaff` | авторы тайтла и страница автора |
| `fetchPosts`, `fetchPost`, `viewPost`, `fetchFeed` | посты и лента новых глав |
| `voteCard`, `deleteCardVote` | голос за карточку |
| `viewGuild`, `requestGuildMember`, `deleteGuildMember`, `depositGuildPoints` | действия в гильдии |
| `updateConversationMessage` | правка своего сообщения |
| `viewManga` | засчитывает просмотр тайтла |
| `voteManga`, `fetchMangaViewerState` | оценка тайтла 1–10 («★ Оценка» на экране тайтла) |
| `deleteMangaVote` | «Убрать оценку» |
| `fetchMangaRelations`, `fetchMangaCharacters` | блоки «Связанное» и «Персонажи» на экране тайтла |
| `fetchPopularTodaySearchQueries` | популярные запросы чипами в каталоге |
| `readMangaChapter`, `updateMangaReaderSetting` | прогресс чтения: глава прочитана, конец главы, открыты комментарии |
| `fetchMangaBookmarkNextChapters` | «Продолжить чтение» на главной |
| `fetchUserBookmarks`, `updateUserBookmark`, `deleteUserBookmark` | закладки на сайте |
| `homeLatest`, `homeNew`, `homePopular`, `homeRecommendations` | секции главной |

Запросы вынесены из приложенных расширений Senkuro (Tachiyomi/Aidoku). Заголовки клиента и адреса — в
[`SenkuroConfig.kt`](app/src/main/java/me/senkuro/unofficial/data/SenkuroConfig.kt).

## Сборка

Только вручную: **Actions → Android build → Run workflow**, в поле `build_type` выберите `debug` или `release`.
APK появится в артефактах запуска (`me.senkuro.unofficial-debug` / `me.senkuro.unofficial-release`).

### Отладочные инструменты (только debug)

В debug-сборке на вкладке «Библиотека» есть кнопка «Отладка: перехват запросов сайта». Она открывает senkuro.me
во встроенном браузере и записывает все GraphQL-вызовы (операция, запрос, переменные, ответ).
Кроме GraphQL ловятся не-GET запросы к хостам senkuro (вход, OAuth) и переходы между страницами; кнопки `senkuro.me` и `sso.senkuro.net` открывают нужный домен в браузере. Запросы, уже реализованные в приложении (имена из `assets/graphql`), скрыты и показаны счётчиком; переключатель включает их обратно. Пароли, токены, `code`/`state` и cookie вырезаются. Лог можно скопировать или отправить файлом («Поделиться»).

В release-сборке этого нет:
- вкладка «Файлы» в перехватчике: статические файлы сайта (js, css, json, карты, шрифты) скачиваются приложением и сохраняются по одному или ZIP-архивом; кнопка «Найти GraphQL в js» вытаскивает тексты запросов из бандлов;
- код перехвата лежит в `app/src/debug`, в `app/src/release` только пустая заглушка;
- `BuildConfig.DEBUG_TOOLS` равен `true` в debug и `false` в release, кнопка скрыта;
- библиотека `androidx.webkit` подключена только как `debugImplementation`;
- workflow при `release` проверяет APK и падает, если находит в нём код перехвата.

### Постоянная подпись debug

Все сборки (debug и release без своих секретов) подписываются ключом `app/debug.keystore` из репозитория (пароль `android`, alias `androiddebugkey`).
Ключ один и тот же при каждой сборке в Actions, поэтому новую версию можно ставить поверх старой без удаления.
Внимание: ключ публичный, для публикации приложения не подходит.

### Подпись release (необязательно)

Если в секретах репозитория заданы `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`, release подписывается вашим ключом. Без них он подписывается постоянным debug-ключом.
