# GraphQL-запросы

Все запросы лежат в [`app/src/main/assets/graphql`](../app/src/main/assets/graphql) и загружаются по имени файла
(`SenkuroApi.execute(operation, file, variables, serializer)`). Запросы вынесены из приложенных расширений Senkuro
(Tachiyomi/Aidoku) и из JS сайта. Заголовки клиента и адреса доменов — в
[`SenkuroConfig.kt`](../app/src/main/java/me/senkuro/unofficial/data/SenkuroConfig.kt).

## Описанные запросы

| Файл | Назначение |
| --- | --- |
| `searchTachiyomiManga` | каталог и поиск (offset-пагинация, по 10); сортировки по популярности и по рейтингу |
| `fetchCatalogMangas` | каталог «как на сайте» (запрос `mangas`, курсорная пагинация, по 20): сортировки по датам, главам и просмотрам, а также фильтры, которых нет в Tachiyomi (первоисточник, страна, диапазоны глав и года), и серверный «Статус закладок» |
| `fetchMangaFilters` | группы меток каталога (`allLabels`: корни «Основной каст», «Черты»…, популярные метки первыми) |
| `fetchTachiyomiSearchFilters` | список меток для фильтров |
| `fetchTachiyomiManga` | карточка тайтла |
| `fetchTachiyomiChapters` | главы, ветки и команды |
| `fetchTachiyomiChapterPages` | страницы главы |
| `readerChapter` | запасной вариант получения страниц по slug главы |
| `fetchViewer` | аккаунт и коллекции закладок |
| `fetchViewerUnreadNotifications`, `fetchNotificationFeeds` | уведомления |
| `bulkReadNotifications`, `bulkDeleteNotifications` | «Прочитать все», удаление уведомлений |
| `fetchUser`, `fetchUserAchievements` | экран профиля (аватар в шапке) и достижения |
| SSO `/api/v1/captcha-config`, `/api/v1/auth/login`, `/oauth/authorize`, `/oauth/token` | нативный вход: форма почта/пароль/2FA, капча в WebView, OAuth+PKCE делает приложение (хост SSO зависит от домена: `sso.senkuro.net` для senkuro.me, `sso.senkuro.org` для senkuro.com) |
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

## Запросы без подробного описания

Эти файлы есть в `assets/graphql`, но в таблице выше их пока нет. Названия сгруппированы по смыслу, исходя из имён
файлов; сверяйтесь с кодом (`CardsRepository`, `SocialRepository`, `EcosystemRepository` и др.).

- **Колоды, паки, кузница, магазин:** `fetchCardDecks`, `fetchCardDeck`, `createCardDeck`, `deleteCardDeck`, `voteCardDeck`,
  `deleteCardDeckVote`, `fetchCardPacks`, `fetchCardPack`, `openCardPack`, `bulkChooseCardPackOpen`, `fetchPendingPackOpens`,
  `fetchPendingCardContest`, `votePendingCard`, `fetchForgeCardsSlots`, `forgeCards`, `fetchCollectibles`,
  `equipUserCollectible`, `createCollectibleOrder`
- **Настройки уведомлений:** `fetchUserNotificationPreferences`, `updateUserNotificationPreference`,
  `deleteUserNotificationPreference`, `fetchNotificationFeedsBasic`, `fetchNotificationFeedsRich`
- **Профиль:** `fetchActivities`, `fetchUserRanks`, `fetchUserAppearance`, `fetchUserProfileExtra`, `fetchViewerBalance`
- **Прочее:** `fetchMangaPage`, `fetchShortsMangaChapters`, `homePopularPeriod`
