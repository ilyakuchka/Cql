# Senkuro Unofficial

Неофициальное нативное Android-приложение для чтения манги с [senkuro.me](https://senkuro.me).
Пакет: `me.senkuro.unofficial`. Kotlin + Jetpack Compose, данные берутся из GraphQL `https://api.senkuro.me/graphql`.

## Возможности

- Главная: свежие обновления, популярное за день, новые тайтлы, рекомендации
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

Только вручную: **Actions → Android build → Run workflow**. APK появится в артефактах запуска
(`me.senkuro.unofficial-debug`).
