# Выдача бесплатной подписки кнопкой — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** кнопка на экране активации получает ссылку подписки с эндпоинта оператора и активирует её, а свои подписки и конфиги снова добавляются штатным импортом.

**Architecture:** адрес эндпоинта — `buildConfigField` (без токена), тело ответа разбирает чистый `FreeSubscriptionLink`, ссылка уходит в существующий `SubscriptionActivation`; повторная выдача подменяет ссылку в том же бесплатном слоте (`PREF_ACTIVATION_SUBSCRIPTION_ID`); гейт первого запуска получает второе условие через чистый `ActivationGate`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, OkHttp (через `HttpUtil`), MMKV (`MmkvManager`/`SettingsManager`), JUnit 5 Jupiter, Gradle Kotlin DSL.

**Spec:** [docs/superpowers/specs/2026-11-02-free-subscription-provisioning-design.md](../specs/2026-11-02-free-subscription-provisioning-design.md)

## Global Constraints

- `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37` — из `V2rayNG/app/build.gradle.kts:12-16`; поддерживаемые ABI — ровно те, что объявлены там же и в `.github/workflows/build.yml`.
- Никаких новых зависимостей: сборка офлайн (`--offline`), в кэше Gradle нет `kotlinx-coroutines-test`, поэтому сетевые и `Dispatchers.Main`-зависимые ветки на JVM не тестируются — чистая логика выносится в отдельные объекты.
- Тесты пишутся на JUnit 5 Jupiter (`org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.assertEquals`): `app/build.gradle.kts:155` включает `useJUnitPlatform()`, и в `app/src/test/java` файлов на `org.junit.Test` нет вовсе.
- В JVM-тестах нельзя вызывать ничего, что идёт в `android.*` (`android.webkit.URLUtil`, `android.util.Base64`): `returnDefaultValues` не включён, такой тест падает с «Method … not mocked». Поэтому проверка «это ссылка подписки» приходит в чистые объекты предикатом, как уже сделано в `MainImportGuardTest`.
- Весь видимый текст — в ресурсах. Ключи `nerpa_*` добавляются только в `values/strings.xml` и `values-ru/strings.xml`: это зарегистрированная в `FORK.md` задолженность по девяти локалям из `androidResources.localeFilters`, новый план её не закрывает.
- В **новых** строках нет слова «панель» и вообще упоминания инфраструктуры оператора; переписка трёх существующих строк активации — отдельная задача #14, в этот план не входит.
- В лог не пишутся ссылка, токен и тело ответа; разрешены код статуса и длина тела.
- Идентичность групп и профилей — только GUID (`Utils.getUuid()`, `PREF_ACTIVATION_SUBSCRIPTION_ID`), никогда индекс списка.
- Диск, сеть и разбор ответа — вне main-потока; у каждой асинхронной операции есть владелец (`viewModelScope`).
- Сериализованные поля не переименовываются и не удаляются: `SubscriptionItem` и `SubscriptionUsageInfo` пишутся как раньше, добавляется только запись в существующие поля.
- Частичная запись запрещена: если подмена ссылки не удалась, прежний `SubscriptionItem` возвращается той же операцией записи.
- Проверки по корневому `AGENTS.md`: прод-код Kotlin → именованный тест + `:app:testPlaystoreDebugUnitTest` + `:app:compilePlaystoreDebugKotlin`; ресурсы/Gradle → `:app:assemblePlaystoreDebug`; непройденные проверки называются ровно `Not run`.
- Коммиты: только явные пути, без `git add -A`/`.`/`-a`; перед каждым коммитом два гейта по файлам инструкций (см. «Как работать в этом репо»), автор — `keeldj03-sys` (repo-local config уже настроен).

## Как работать в этом репо

Корень Gradle — `V2rayNG/`, не корень репозитория. JDK вне PATH, `<jdk>` — его каталог на машине прогона
(тот же приём, что `<стенд>` и `<клон репозитория>`): наружу путь инструментария не уходит, а команда
остаётся копируемой после подстановки.

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline <tasks> > .build-logs\<имя>.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/<имя>.log
```

`rc` у `cmd //c` может быть 0 и при `BUILD FAILED` — решение только по логу. Логи не прогонять через `tail`: буферизация скрывает прогресс долгого сборки. `.build-logs/` уже в репо и не коммитится.

Устройство для проверок: Samsung `TEST-PHONE` (1440×3040, 411 dp), `applicationId ru.nerpa.mobile`, APK собирается с именем `NerpaMobile_2.3.15_arm64-v8a.apk` (не `app-playstore-debug.apk`). `com.v2ray.ang` (рабочий upstream) не трогать и не удалять.

---

### Task 1: Откат запрета импорта подписочной ссылки

Спека, раздел 5: в новой модели свои подписки — штатный импорт, поэтому вчерашний запрет удаляется целиком, включая строку и тест.

**Files:**
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt:552-570`
- Delete: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainImportGuard.kt`
- Delete: `V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/MainImportGuardTest.kt`
- Modify: `V2rayNG/app/src/main/res/values/strings.xml` (удалить `nerpa_import_subscription_refused`)
- Modify: `V2rayNG/app/src/main/res/values-ru/strings.xml` (то же)

**Interfaces:**
- Consumes: `MainDataSource.importBatchConfig(server: String?, subscriptionId: String, updateUI: Boolean): Pair<Int, Int>`
- Produces: `MainViewModel.importBatchConfig(configText: String)` без проверки на подписочную ссылку; корзина `AppConfig.DEFAULT_SUBSCRIPTION_ID` остаётся точкой импорта ручных узлов.

- [ ] **Step 1: Убрать гейт из `importBatchConfig`**

В `MainViewModel.kt` внутри `launchLoading { withContext(ioDispatcher) { try { ... } } }` заменить блок с `MainImportGuard` на прямую передачу текста. Итог (строки 552-570 после правки):

```kotlin
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.importBatchConfig(
                        // Pasted and scanned nodes are the user's own, so they go to the ungrouped
                        // bucket; the open tab may be a subscription, and a server stored there is
                        // replaced by the next subscription update.
                        configText, AppConfig.DEFAULT_SUBSCRIPTION_ID, true
                    )
```

Ничего больше в блоке не менять: обработка `count`/`countSub` и `catch` остаются как есть.

- [ ] **Step 2: Убрать ставший ненужным импорт**

В шапке `MainViewModel.kt` удалить строку `import com.v2ray.ang.util.Utils` — после удаления гейта в этом файле `Utils` больше не вызывается (проверить: `grep -n "Utils\." app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt`). Если вызовы остались — импорт оставить.

- [ ] **Step 3: Удалить файлы гейта и его тест**

```bash
cd <клон репозитория>
git rm V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainImportGuard.kt \
       V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/MainImportGuardTest.kt
```

- [ ] **Step 4: Удалить строку отказа в двух локалях**

В `values/strings.xml` удалить строку
`<string name="nerpa_import_subscription_refused">A subscription link is added when the app is set up. Here you add single nodes.</string>`,
в `values-ru/strings.xml` —
`<string name="nerpa_import_subscription_refused">Ссылку подписки добавляют при первом запуске. Здесь добавляются только отдельные узлы.</string>`.
`nerpa_group_manual` не трогать.

- [ ] **Step 5: Проверить, что ничего не осталось**

```bash
grep -rn "MainImportGuard\|nerpa_import_subscription_refused" V2rayNG/app/src
```

Ожидаемый результат — пусто (ни одного совпадения).

- [ ] **Step 6: Прогнать компиляцию и тесты**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:compilePlaystoreDebugKotlin :app:testPlaystoreDebugUnitTest > .build-logs/task1.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task1.log
```

Ожидание: `BUILD SUCCESSFUL`, падений тестов нет.

- [ ] **Step 7: Коммит**

```bash
cd <клон репозитория>
git diff --cached --name-only -- ':(glob)**/AGENTS.md' ':(glob)**/CLAUDE.md' ':(glob)**/GEMINI.md' .github/copilot-instructions.md
git diff --name-only upstream/master...HEAD -- ':(glob)**/AGENTS.md' ':(glob)**/CLAUDE.md' ':(glob)**/GEMINI.md' .github/copilot-instructions.md
git add V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt \
        V2rayNG/app/src/main/res/values/strings.xml \
        V2rayNG/app/src/main/res/values-ru/strings.xml
git commit -m "feat(ui): подписочная ссылка снова штатный импорт

Клиент перестроен на «бесплатная выдача + свои подписки», поэтому запрет из
прошлого коммита описывает уже не нужную границу. Корзина ручных узлов остаётся:
она решала другую задачу и верна сама по себе."
```

Оба гейта должны напечатать пусто, иначе снять индексацию правок в файлах инструкций и повторить.

---

### Task 2: Имя группы вместо литерала `import sub`

Спека, раздел 5: дубликат-проблема лечится именем. `AngConfigManager` не имеет доступа к `Context`, поэтому локализованное имя приходит из слоя вызова, а чистая селекция имени выносится в тестируемый объект.

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionImportNaming.kt`
- Create: `V2rayNG/app/src/test/java/com/v2ray/ang/handler/SubscriptionImportNamingTest.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/handler/AngConfigManager.kt:184` (`importBatchConfig`) и `:621-635` (`importUrlAsSubscription`)
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainDataSource.kt:50`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainRepository.kt:195-199`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt:556`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/UrlSchemeActivity.kt:79`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/shortcut/ScScannerActivity.kt:30`
- Modify: `V2rayNG/app/src/main/res/values/strings.xml`, `V2rayNG/app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Consumes: `Utils.fixIllegalUrl(String): String`, `java.net.URI`
- Produces:
  - `SubscriptionImportNaming.remarks(fragment: String?, host: String?, withHost: (String) -> String, plainName: String): String`
  - `AngConfigManager.importBatchConfig(server: String?, subid: String, append: Boolean, subscriptionName: (fragment: String?, host: String?) -> String): Pair<Int, Int>`
  - `MainDataSource.importBatchConfig(server: String?, subscriptionId: String, updateUI: Boolean, subscriptionName: (String?, String?) -> String): Pair<Int, Int>`
  - ресурсы `nerpa_group_imported` (`Subscription · %1$s`) и `nerpa_group_imported_plain` (`Subscription`)

Имя форматируется ресурсом вызывающей стороны (`getString(res, host)`), а не `String.format` внутри чистого объекта: `DefaultLocale` в Android lint — ошибка, и форматная строка остаётся там, где она и читается.

- [ ] **Step 1: Написать падающий тест**

`V2rayNG/app/src/test/java/com/v2ray/ang/handler/SubscriptionImportNamingTest.kt`:

```kotlin
package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionImportNamingTest {

    private val withHost = { host: String -> "Subscription · $host" }
    private val plain = "Subscription"

    @Test
    fun `fragment wins over host`() {
        val remarks = SubscriptionImportNaming.remarks(
            fragment = "Nerpa",
            host = "panel.example.com",
            withHost = withHost,
            plainName = plain,
        )
        assertEquals("Nerpa", remarks)
    }

    @Test
    fun `blank fragment falls back to host`() {
        val remarks = SubscriptionImportNaming.remarks(
            fragment = "   ",
            host = "panel.example.com",
            withHost = withHost,
            plainName = plain,
        )
        assertEquals("Subscription · panel.example.com", remarks)
    }

    @Test
    fun `missing host gives the plain name`() {
        val remarks = SubscriptionImportNaming.remarks(
            fragment = null,
            host = null,
            withHost = withHost,
            plainName = plain,
        )
        assertEquals(plain, remarks)
    }
}
```

- [ ] **Step 2: Убедиться, что тест падает**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest --tests "com.v2ray.ang.handler.SubscriptionImportNamingTest" > .build-logs/task2-red.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)|unresolved" .build-logs/task2-red.log
```

Ожидание: `BUILD FAILED` с `Unresolved: 'SubscriptionImportNaming'` — тест не может пройти до реализации.

- [ ] **Step 3: Реализовать чистый выбор имени**

`V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionImportNaming.kt`:

```kotlin
package com.v2ray.ang.handler

/**
 * Name for a subscription group created from an imported link.
 *
 * Upstream falls back to the literal `import sub`, which is neither localizable nor something a
 * consumer can read as their own group. The fragment wins because panel links carry `#Name` when
 * the operator set one; the host is the next thing a person actually recognizes. The host case is
 * a lambda because the localized pattern is a resource, and resources belong to the caller.
 */
object SubscriptionImportNaming {

    fun remarks(
        fragment: String?,
        host: String?,
        withHost: (String) -> String,
        plainName: String,
    ): String {
        val title = fragment?.trim()?.takeIf { it.isNotEmpty() }
        if (title != null) return title
        val server = host?.trim()?.takeIf { it.isNotEmpty() }
        return if (server != null) withHost(server) else plainName
    }
}
```

- [ ] **Step 4: Проверить, что тест проходит**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest --tests "com.v2ray.ang.handler.SubscriptionImportNamingTest" > .build-logs/task2-green.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task2-green.log
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Пробросить имя в `AngConfigManager`**

В `handler/AngConfigManager.kt` у `importBatchConfig` (строка 184) добавить параметр и передать его в вызов импорта ссылки:

```kotlin
    fun importBatchConfig(
        server: String?,
        subid: String,
        append: Boolean,
        subscriptionName: (fragment: String?, host: String?) -> String,
    ): Pair<Int, Int> {
```

Внутри, на месте `count += importUrlAsSubscription(str)` (строка 226):

```kotlin
                        count += importUrlAsSubscription(str, subscriptionName)
```

И заменить тело `importUrlAsSubscription` (строки 621-635) — дедуп по посимвольному сравнению URL остаётся как есть:

```kotlin
    private fun importUrlAsSubscription(
        url: String,
        subscriptionName: (fragment: String?, host: String?) -> String,
    ): Int {
        val subscriptions = MmkvManager.decodeSubscriptions()
        subscriptions.forEach {
            if (it.subscription.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = subscriptionName(uri.fragment, uri.host)
        subItem.url = url
        MmkvManager.encodeSubscription("", subItem)
        return 1
    }
```

- [ ] **Step 6: Обновить слой данных**

`ui/main/MainDataSource.kt:50`:

```kotlin
    suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean,
        subscriptionName: (fragment: String?, host: String?) -> String,
    ): Pair<Int, Int>
```

`ui/main/MainRepository.kt:195-199`:

```kotlin
    override suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean,
        subscriptionName: (fragment: String?, host: String?) -> String,
    ): Pair<Int, Int> = AngConfigManager.importBatchConfig(
        server, subscriptionId, updateUI, subscriptionName
    )
```

- [ ] **Step 7: Обновить три места вызова**

`ui/main/MainViewModel.kt` (внутри `importBatchConfig`, после Task 1):

```kotlin
                    val (count, countSub) = dataSource.importBatchConfig(
                        // Pasted and scanned nodes are the user's own, so they go to the ungrouped
                        // bucket; the open tab may be a subscription, and a server stored there is
                        // replaced by the next subscription update.
                        configText,
                        AppConfig.DEFAULT_SUBSCRIPTION_ID,
                        true,
                        { fragment, host ->
                            SubscriptionImportNaming.remarks(
                                fragment = fragment,
                                host = host,
                                withHost = {
                                    dataSource.getString(R.string.nerpa_group_imported, it)
                                },
                                plainName = dataSource.getString(R.string.nerpa_group_imported_plain),
                            )
                        },
                    )
```

`ui/UrlSchemeActivity.kt:79` и `ui/shortcut/ScScannerActivity.kt:30` — тот же лямбда-блок, но источник строк — контекст активности:

```kotlin
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    decodedUrl, "", false,
                    { fragment, host ->
                        SubscriptionImportNaming.remarks(
                            fragment = fragment,
                            host = host,
                            withHost = { getString(R.string.nerpa_group_imported, it) },
                            plainName = getString(R.string.nerpa_group_imported_plain),
                        )
                    },
                )
```

(в `ScScannerActivity.kt` вместо `decodedUrl` — `scanResult`; остальное идентично)

- [ ] **Step 8: Добавить строки в две локали**

`values/strings.xml` (после `nerpa_group_manual`):

```xml
    <string name="nerpa_group_imported">Subscription · %1$s</string>
    <string name="nerpa_group_imported_plain">Subscription</string>
```

`values-ru/strings.xml` (после `nerpa_group_manual`):

```xml
    <string name="nerpa_group_imported">Подписка · %1$s</string>
    <string name="nerpa_group_imported_plain">Подписка</string>
```

- [ ] **Step 9: Прогнать тесты, компиляцию и сборку ресурсов**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin :app:assemblePlaystoreDebug > .build-logs/task2-build.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task2-build.log
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Коммит**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionImportNaming.kt \
        V2rayNG/app/src/test/java/com/v2ray/ang/handler/SubscriptionImportNamingTest.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/handler/AngConfigManager.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainDataSource.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainRepository.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/UrlSchemeActivity.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/shortcut/ScScannerActivity.kt \
        V2rayNG/app/src/main/res/values/strings.xml \
        V2rayNG/app/src/main/res/values-ru/strings.xml
git commit -m "feat(ui): импортированная подписка называется по-человечески

Upstream писал 'import sub' литералом в коде, когда у ссылки нет #фрагмента, —
нелокализуемо и выглядит как мусор разработчика. Имя собирает чистый
SubscriptionImportNaming: фрагмент, иначе хост, иначе просто «Подписка».
AngConfigManager контекста не видит, поэтому локализованные шаблоны приходят из
слоя вызова; deep-link и сканер из ярлыка получают то же имя."
```

---

### Task 3: Разбор ответа эндпоинта и новые состояния отказа

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/handler/FreeSubscriptionLink.kt`
- Create: `V2rayNG/app/src/test/java/com/v2ray/ang/handler/FreeSubscriptionLinkTest.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionActivation.kt:13-25` (`ActivationFailure`)
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/util/HttpUtil.kt` (sealed-тип `EndpointFetchResult`)

**Interfaces:**
- Consumes: `EndpointFetchResult` (объявляется здесь, наполняется в Task 4), `ActivationFailure`
- Produces:
  - `sealed class EndpointFetchResult { Content(body: String) | HttpStatus(code: Int) | Transport }` в `util/HttpUtil.kt`
  - `FreeSubscriptionLink.from(fetch: EndpointFetchResult, isValidSubUrl: (String) -> Boolean): FreeLinkResult`
  - `sealed class FreeLinkResult { Link(url: String) | Refused(failure: ActivationFailure) }`
  - `ActivationFailure.NotALink`, `ActivationFailure.QuotaClosed`

- [ ] **Step 1: Написать падающие тесты**

`V2rayNG/app/src/test/java/com/v2ray/ang/handler/FreeSubscriptionLinkTest.kt`:

```kotlin
package com.v2ray.ang.handler

import com.v2ray.ang.util.EndpointFetchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val LINK = "https://panel.example.com/sub/token"

private fun pretendIsSubUrl(line: String) = line.startsWith("https://")

class FreeSubscriptionLinkTest {

    @Test
    fun `first non-blank line is the link`() {
        val result = FreeSubscriptionLink.from(
            EndpointFetchResult.Content("\r\n  $LINK  \r\n"),
            ::pretendIsSubUrl,
        )
        assertEquals(FreeLinkResult.Link(LINK), result)
    }

    @Test
    fun `body that is not a link is refused`() {
        val result = FreeSubscriptionLink.from(
            EndpointFetchResult.Content("<html><body>login</body></html>"),
            ::pretendIsSubUrl,
        )
        assertEquals(FreeLinkResult.Refused(ActivationFailure.NotALink), result)
    }

    @Test
    fun `empty body is its own refusal`() {
        val result = FreeSubscriptionLink.from(
            EndpointFetchResult.Content("   \n"),
            ::pretendIsSubUrl,
        )
        assertEquals(FreeLinkResult.Refused(ActivationFailure.EmptyResponse), result)
    }

    @Test
    fun `closed issuance is told apart from no network`() {
        for (code in listOf(403, 429)) {
            val result = FreeSubscriptionLink.from(
                EndpointFetchResult.HttpStatus(code),
                ::pretendIsSubUrl,
            )
            assertTrue(result is FreeLinkResult.Refused)
            assertEquals(ActivationFailure.QuotaClosed, (result as FreeLinkResult.Refused).failure)
        }
    }

    @Test
    fun `other statuses and transport failures are unreachable`() {
        val statuses = listOf(500, 503, 404)
        for (code in statuses) {
            val result = FreeSubscriptionLink.from(
                EndpointFetchResult.HttpStatus(code),
                ::pretendIsSubUrl,
            )
            assertEquals(ActivationFailure.Unreachable, (result as FreeLinkResult.Refused).failure)
        }
        val transport = FreeSubscriptionLink.from(EndpointFetchResult.Transport, ::pretendIsSubUrl)
        assertEquals(ActivationFailure.Unreachable, (transport as FreeLinkResult.Refused).failure)
    }
}
```

- [ ] **Step 2: Убедиться, что тесты падают**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest --tests "com.v2ray.ang.handler.FreeSubscriptionLinkTest" > .build-logs/task3-red.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)|Unresolved" .build-logs/task3-red.log
```

Ожидание: `BUILD FAILED` (`Unresolved reference 'EndpointFetchResult'`, `FreeSubscriptionLink`).

- [ ] **Step 3: Добавить sealed-тип результата эндпоинта**

В `util/HttpUtil.kt` — на уровень файла, сразу после `import`-блока (там уже несколько top-level объявлений):

```kotlin
/**
 * Answer from a one-shot operator endpoint.
 *
 * [getUrlContent] collapses every failure into `null`, which cannot tell "the operator closed
 * issuance" from "there is no network" — and the free-access screen has to say one of the two.
 */
sealed class EndpointFetchResult {
    data class Content(val body: String) : EndpointFetchResult()
    data class HttpStatus(val code: Int) : EndpointFetchResult()
    data object Transport : EndpointFetchResult()
}
```

- [ ] **Step 4: Добавить два состояния отказа активации**

В `handler/SubscriptionActivation.kt` внутри `enum class ActivationFailure` (строка 13), после `NoParsableNodes`:

```kotlin
    /** The endpoint answered, but the body was not a subscription link. */
    NotALink,

    /** The operator closed issuance: quota, or the free tier is paused. */
    QuotaClosed,
```

- [ ] **Step 5: Реализовать разбор**

`handler/FreeSubscriptionLink.kt`:

```kotlin
package com.v2ray.ang.handler

import com.v2ray.ang.util.EndpointFetchResult

/** What the operator endpoint gave us, reduced to either a link or one user-facing refusal. */
sealed class FreeLinkResult {
    /** A trimmed, subscription-shaped link. */
    data class Link(val url: String) : FreeLinkResult()

    /** No link; carries the message the activation screen shows. */
    data class Refused(val failure: ActivationFailure) : FreeLinkResult()
}

/**
 * Reads the free-subscription endpoint response.
 *
 * The contract is plain text, so the first non-blank line is the link and anything else is a
 * refusal: importing an HTML page as a subscription would create a dead group and a second tab.
 * `isValidSubUrl` arrives as a predicate because `Utils.isValidSubUrl` runs on `android.webkit`
 * and cannot be called from JVM tests.
 */
object FreeSubscriptionLink {

    fun from(
        fetch: EndpointFetchResult,
        isValidSubUrl: (String) -> Boolean,
    ): FreeLinkResult = when (fetch) {
        is EndpointFetchResult.HttpStatus -> FreeLinkResult.Refused(refusalForStatus(fetch.code))
        EndpointFetchResult.Transport -> FreeLinkResult.Refused(ActivationFailure.Unreachable)
        is EndpointFetchResult.Content -> parse(fetch.body, isValidSubUrl)
    }

    private fun parse(body: String, isValidSubUrl: (String) -> Boolean): FreeLinkResult {
        val line = body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return FreeLinkResult.Refused(ActivationFailure.EmptyResponse)
        return if (isValidSubUrl(line)) {
            FreeLinkResult.Link(line)
        } else {
            FreeLinkResult.Refused(ActivationFailure.NotALink)
        }
    }

    private fun refusalForStatus(code: Int): ActivationFailure = when (code) {
        403, 429 -> ActivationFailure.QuotaClosed
        else -> ActivationFailure.Unreachable
    }
}
```

- [ ] **Step 6: Проверить зелёный прогон и компиляцию**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin > .build-logs/task3-green.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task3-green.log
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Коммит**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/handler/FreeSubscriptionLink.kt \
        V2rayNG/app/src/test/java/com/v2ray/ang/handler/FreeSubscriptionLinkTest.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionActivation.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/util/HttpUtil.kt
git commit -m "feat(activation): разбор ответа эндпоинта бесплатной выдачи

Тело — чистый текст, поэтому ссылкой считается первая непустая строка, а всё
остановленное — отказ: импортировать HTML-страницу как подписку значит завести
мёртвую вкладку. 403 и 429 отделены от сетевого сбоя: «выдача закрыта» и «сервер
не отвечает» — разные предложения для разного экрана. HttpUtil до этого терял код
статуса в тексте IOException."
```

---

### Task 4: Запрос к эндпоинту

Чистая часть уже покрыта Task 3; здесь появляется только сеть, и она проверяется на устройстве.

**Files:**
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/util/HttpUtil.kt` (после `getUrlContentWithUserAgent`, строка ~210)

**Interfaces:**
- Consumes: `UrlContentRequest(url, timeout, httpPort, proxyUsername, proxyPassword, userAgent, requestHeaders)`, приватный `buildOkHttpClient(...)`
- Produces: `HttpUtil.fetchEndpoint(request: UrlContentRequest): EndpointFetchResult`

- [ ] **Step 1: Реализовать один запрос с кодом статуса**

```kotlin
    /**
     * Calls a one-shot operator endpoint.
     *
     * Redirects are left to OkHttp (the panel domain may answer from another path), and a non-2xx
     * keeps its status code. The body is never logged: for the free-access endpoint it carries a
     * subscription token, and root AGENTS.md forbids logging credentials.
     */
    fun fetchEndpoint(request: UrlContentRequest): EndpointFetchResult {
        val url = request.url
        if (url.isNullOrEmpty()) return EndpointFetchResult.Transport
        val client = buildOkHttpClient(
            request.timeout, request.httpPort, request.proxyUsername, request.proxyPassword,
            followRedirects = true,
        )
        return try {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .header("User-agent", "NerpaMobile/${BuildConfig.VERSION_NAME}")
                    .header("Connection", "close")
                    .build()
            ).execute().use { response ->
                if (response.isSuccessful) {
                    EndpointFetchResult.Content(response.body?.string().orEmpty())
                } else {
                    LogUtil.w(AppConfig.TAG, "Free access endpoint failed, code=${response.code}")
                    EndpointFetchResult.HttpStatus(response.code)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Free access endpoint transport error", e)
            EndpointFetchResult.Transport
        }
    }
```

- [ ] **Step 2: Проверить компиляцию**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:compilePlaystoreDebugKotlin > .build-logs/task4.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task4.log
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Коммит**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/util/HttpUtil.kt
git commit -m "feat(activation): запрос к эндпоинту бесплатной выдачи с кодом статуса

Отдельная функция, а не обёртка getUrlContent: та сводит любой сбой к null, а
экрану нужно отличить закрытую выдачу от отсутствия сети. Тело ответа в лог не
пишется — в нём токен подписки."
```

---

### Task 5: Повторная выдача в тот же бесплатный слот

Спека, раздел 3: слот — одна группа, ссылка подменяется, при провале возвращается прежняя.

**Files:**
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionActivation.kt:50-107`
- Modify: `V2rayNG/app/src/test/java/com/v2ray/ang/handler/SubscriptionActivationTest.kt`

**Interfaces:**
- Consumes: `MmkvManager.decodeSubscription(subscriptionId: String): SubscriptionItem?`, `MmkvManager.encodeSubscription(guid: String, subItem: SubscriptionItem)`, `AngConfigManager.updateConfigViaSub(SubscriptionCache): SubscriptionUpdateResult`, `SubscriptionUpdater.syncOne(subId: String)`
- Produces: `SubscriptionActivation.reactivate(subscriptionId: String, url: String): ActivationOutcome` (существующий `activate(url, remarks, updateIntervalMinutes)` сохраняет сигнатуру и поведение)

- [ ] **Step 1: Закрепить тестом чистую часть решения «нет ссылки»**

В `SubscriptionActivationTest.kt` добавить тест на хелпер, который вводит Task 5. Проверка формы URL в JVM не тестируется: `Utils.isValidUrl` и `isValidSubUrl` идут в `android.webkit` и на JVM падают, поэтому тестируется только обрезка и пустота — то, что решает «не писать в хранилище вообще»:

```kotlin
    @Test
    fun `blank link trims to nothing before any storage write`() {
        assertEquals(null, SubscriptionActivation.trimLink(""))
        assertEquals(null, SubscriptionActivation.trimLink("   "))
        assertEquals("https://panel.example.com/sub/token", SubscriptionActivation.trimLink("  https://panel.example.com/sub/token  "))
    }
```

Если `SubscriptionActivationTest` уже покрывает `InvalidUrl` для `activate`, новый тест добавляется к ним, а не заменяет их.

- [ ] **Step 2: Убедиться, что тест падает**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest --tests "com.v2ray.ang.handler.SubscriptionActivationTest" > .build-logs/task5-red.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)|Unresolved" .build-logs/task5-red.log
```

Ожидание: `BUILD FAILED` (`Unresolved reference 'trimLink'`).

- [ ] **Step 3: Вынести общую часть `activate` в `accept`**

В `SubscriptionActivation.kt` заменить тело `activate` и добавить функции (порядок «зонд → запись → коммит» сохраняется как был):

```kotlin
    fun activate(
        url: String,
        remarks: String,
        updateIntervalMinutes: Long = SubscriptionItem().updateInterval,
    ): ActivationOutcome {
        val link = trimLink(url)?.takeIf { isUsableLink(it) }
            ?: return ActivationOutcome.Failed(ActivationFailure.InvalidUrl)
        val guid = Utils.getUuid()
        return accept(
            guid = guid,
            item = SubscriptionItem(
                remarks = remarks,
                url = link,
                autoUpdate = true,
                updateInterval = updateIntervalMinutes,
            ),
            // A link the operator's panel refused must not linger as a group the user never agreed to.
            onFailure = { MmkvManager.removeSubscription(guid) },
        )
    }

    /**
     * Re-issues the free slot: the same group keeps its remarks and gets the new link.
     *
     * Restoring the previous item on refusal is required, not cosmetic — a group left with an empty
     * or half-written url reads as the app corrupting the user's subscription.
     */
    fun reactivate(subscriptionId: String, url: String): ActivationOutcome {
        val link = trimLink(url)?.takeIf { isUsableLink(it) }
            ?: return ActivationOutcome.Failed(ActivationFailure.InvalidUrl)
        val previous = MmkvManager.decodeSubscription(subscriptionId)
            ?: return ActivationOutcome.Failed(ActivationFailure.InvalidUrl)
        return accept(
            guid = subscriptionId,
            item = previous.copy(url = link),
            onFailure = { MmkvManager.encodeSubscription(subscriptionId, previous) },
        )
    }

    /** Trimmed link, or null when the text is blank. Pure, so JVM-testable. */
    fun trimLink(url: String): String? = url.trim().takeIf { it.isNotEmpty() }

    /** Device-only: `Utils.isValidUrl` and `isValidSubUrl` run on `android.webkit.URLUtil`. */
    private fun isUsableLink(link: String): Boolean =
        Utils.isValidUrl(link) && Utils.isValidSubUrl(link)

    private fun accept(
        guid: String,
        item: SubscriptionItem,
        onFailure: () -> Unit,
    ): ActivationOutcome {
        val body = probe(item.url)
            ?: return ActivationOutcome.Failed(ActivationFailure.Unreachable)
        if (body.isBlank()) {
            return ActivationOutcome.Failed(ActivationFailure.EmptyResponse)
        }
        val total = countNodeLines(Utils.decode(body) ?: body)
        if (total == 0) {
            return ActivationOutcome.Failed(ActivationFailure.NoParsableNodes)
        }

        MmkvManager.encodeSubscription(guid, item)
        val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
        val outcome = classify(guid, total, result)
        if (outcome is ActivationOutcome.Failed) {
            onFailure()
            return outcome
        }
        SubscriptionUpdater.syncOne(subId = guid)
        return outcome
    }
```

KDoc над `object SubscriptionActivation` дополнить одной строкой: `reactivate` — тот же путь для повторной выдачи в существующий слот.

- [ ] **Step 4: Проверить зелёный прогон и компиляцию**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin > .build-logs/task5-green.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task5-green.log
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Коммит**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/handler/SubscriptionActivation.kt \
        V2rayNG/app/src/test/java/com/v2ray/ang/handler/SubscriptionActivationTest.kt
git commit -m "feat(activation): повторная выдача подменяет ссылку в том же слоте

Бесплатная подписка — слот, а не коллекция: вторая выдача обновляет ту же группу,
иначе мёртвая вкладка с протухшей ссылкой выглядит багом приложения. Провал
возвращает прежний SubscriptionItem, чтобы группа не осталась с пустым url.
Общая часть activate/reactivate вынесена в accept, порядок «зонд → запись →
коммит» прежний."
```

---

### Task 6: Кнопка «Получить бесплатный доступ»

**Files:**
- Modify: `V2rayNG/app/build.gradle.kts` (в `defaultConfig`, после `versionName`, строка 17)
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/activation/ActivationContract.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/activation/ActivationViewModel.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/activation/ActivationActivity.kt:96-216`
- Modify: `V2rayNG/app/src/main/res/values/strings.xml`, `values-ru/strings.xml`

**Interfaces:**
- Consumes: `BuildConfig.FREE_SUBSCRIPTION_ENDPOINT: String`, `HttpUtil.fetchEndpoint`, `FreeSubscriptionLink.from`, `SubscriptionActivation.activate/reactivate`, `SettingsManager.activationSubscriptionId()`
- Produces: `ActivationAction.FetchFree`, `ActivationUiState.freeEnabled: Boolean`

- [ ] **Step 1: Добавить адрес эндпоинта в сборку**

В `app/build.gradle.kts` внутри `defaultConfig`, сразу после `versionName = "2.3.15"` — в том же стиле, что уже используется для `ABI_FILTERS` (строка 17):

```kotlin
        // The operator's free-access endpoint. Only the address is compiled in; the personal
        // subscription link arrives in the response. Empty means the build offers no button.
        val freeEndpoint = (properties["NERPA_FREE_ENDPOINT"] as? String)?.trim() ?: ""
        buildConfigField("String", "FREE_SUBSCRIPTION_ENDPOINT", "\"$freeEndpoint\"")
```

Значение задаётся в `V2rayNG/gradle.properties` (или `-P NERPA_FREE_ENDPOINT=...` при сборке релиза). Рабочий домен в `gradle.properties` не коммитится: публичные исходники не должны его содержать.

- [ ] **Step 2: Расширить контракт экрана**

`ActivationContract.kt`:

```kotlin
data class ActivationUiState(
    val busy: Boolean = false,
    val outcome: ActivationOutcome? = null,
    /** False when the build carries no endpoint address; then the button is not rendered. */
    val freeEnabled: Boolean = BuildConfig.FREE_SUBSCRIPTION_ENDPOINT.isNotBlank(),
)

sealed class ActivationAction {
    data class Activate(val link: String) : ActivationAction()
    data object FetchFree : ActivationAction()
    data object ClearOutcome : ActivationAction()
    data object Finish : ActivationAction()
}
```

Добавить `import com.v2ray.ang.BuildConfig`.

- [ ] **Step 3: Добавить выдачу во ViewModel**

В `ActivationViewModel.kt` в `when` добавить ветку `ActivationAction.FetchFree -> fetchFree()` и функции:

```kotlin
    private fun fetchFree() {
        if (_state.value.busy) return
        val endpoint = BuildConfig.FREE_SUBSCRIPTION_ENDPOINT
        if (endpoint.isBlank()) return
        _state.update { it.copy(busy = true, outcome = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val fetch = HttpUtil.fetchEndpoint(UrlContentRequest(url = endpoint))
                when (val free = FreeSubscriptionLink.from(fetch) { Utils.isValidSubUrl(it) }) {
                    is FreeLinkResult.Refused -> ActivationOutcome.Failed(free.failure)
                    is FreeLinkResult.Link -> activateInSlot(free.url)
                }
            }
            _state.update { it.copy(busy = false, outcome = outcome) }
        }
    }

    /**
     * The free button always serves one slot: the first issuance creates the group, a later one
     * replaces the link inside it. The pasted link keeps creating its own group — that path is a
     * subscription the user brought, not the operator's free tier.
     */
    private fun activateInSlot(link: String): ActivationOutcome {
        val slot = SettingsManager.activationSubscriptionId()
        val remarks = getString(R.string.nerpa_activation_group_name)
        val outcome = if (slot.isNullOrEmpty()) {
            SubscriptionActivation.activate(url = link, remarks = remarks)
        } else {
            SubscriptionActivation.reactivate(slot, link)
        }
        if (outcome is ActivationOutcome.Accepted && slot.isNullOrEmpty()) {
            SettingsManager.setActivationSubscriptionId(outcome.subscriptionId)
        }
        return outcome
    }
```

Импорты: `com.v2ray.ang.BuildConfig`, `com.v2ray.ang.dto.UrlContentRequest`, `com.v2ray.ang.handler.FreeLinkResult`, `com.v2ray.ang.handler.FreeSubscriptionLink`, `com.v2ray.ang.util.HttpUtil`, `com.v2ray.ang.util.Utils`. Существующую ветку `Activate` не переписывать — она по-прежнему зовёт `SubscriptionActivation.activate` напрямую.

- [ ] **Step 4: Отрисовать кнопку**

В `ActivationScreen`, между двумя `Text` (заголовок и подсказка) и `FormTextField`:

```kotlin
                if (state.freeEnabled) {
                    Button(
                        onClick = { onAction(ActivationAction.FetchFree) },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) {
                        Text(stringResource(R.string.nerpa_activation_free_action))
                    }
                    Text(
                        text = stringResource(R.string.nerpa_activation_own_link),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
```

У идущего следом `FormTextField` уменьшить отступ, чтобы он не сложился с отступом кнопки: `modifier = Modifier.padding(top = 24.dp)` → `modifier = Modifier.padding(top = 8.dp)`. Больше в поле ничего не менять: `enabled = !state.busy` там уже стоит, то есть на время запроса блокируются и кнопка, и поле.

- [ ] **Step 5: Подписать новые состояния отказа**

В `ActivationActivity.kt` в `private fun ActivationFailure.messageRes()` добавить:

```kotlin
    ActivationFailure.NotALink -> R.string.nerpa_activation_error_not_a_link
    ActivationFailure.QuotaClosed -> R.string.nerpa_activation_error_quota
```

- [ ] **Step 6: Строки в двух локалях**

`values/strings.xml`:

```xml
    <string name="nerpa_activation_free_action">Get free access</string>
    <string name="nerpa_activation_own_link">Or add a subscription link you already have</string>
    <string name="nerpa_activation_error_not_a_link">The server sent something that isn\'t a subscription link. Try again in a moment.</string>
    <string name="nerpa_activation_error_quota">Free access can\'t be issued right now. Try again later.</string>
```

`values-ru/strings.xml`:

```xml
    <string name="nerpa_activation_free_action">Получить бесплатный доступ</string>
    <string name="nerpa_activation_own_link">Или добавьте ссылку подписки, которая у вас уже есть</string>
    <string name="nerpa_activation_error_not_a_link">Сервер прислал не ссылку подписки. Попробуйте через минуту.</string>
    <string name="nerpa_activation_error_quota">Бесплатный доступ сейчас выдать не можем. Попробуйте позже.</string>
```

- [ ] **Step 7: Проверить сборку ресурсов и компиляцию**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:compilePlaystoreDebugKotlin :app:assemblePlaystoreDebug > .build-logs/task6.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task6.log
```

Ожидание: `BUILD SUCCESSFUL`. Отдельно проверить, что при пустом `NERPA_FREE_ENDPOINT` сборка проходит и `freeEnabled == false` (значение по умолчанию в `gradle.properties` не задано).

- [ ] **Step 8: Коммит**

```bash
git add V2rayNG/app/build.gradle.kts \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/activation/ActivationContract.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/activation/ActivationViewModel.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/activation/ActivationActivity.kt \
        V2rayNG/app/src/main/res/values/strings.xml \
        V2rayNG/app/src/main/res/values-ru/strings.xml
git commit -m "feat(activation): кнопка бесплатного доступа на экране активации

В сборке только адрес эндпоинта, токен приходит в ответе — разданный APK и
публичные исходники ссылку не раскрывают. Пустой адрес = кнопки нет, а не
падающая кнопка. Вставка своей ссылки остаётся вторичным входом и по-прежнему
создаёт отдельную группу: бесплатная подписка и подписка пользователя — разные
вещи."
```

---

### Task 7: Гейт возвращается на экран, когда рабочих подписок не осталось

**Files:**
- Create: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/ActivationGate.kt`
- Create: `V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/ActivationGateTest.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainContract.kt:20-32` (`MainUiState`) и `sealed interface MainAction`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainDataSource.kt` + `MainRepository.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt`
- Modify: `V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt:111-120` и `ScreenContent`

**Interfaces:**
- Consumes: `MainDataSource.getSubscriptions(): List<SubscriptionCache>`, `MainDataSource.getServerGuidList(groupId: String): List<String>`, `SubscriptionUsageInfo.expire: Long`
- Produces: `ActivationGate.isExpired(expireSeconds: Long, nowSeconds: Long): Boolean`, `ActivationGate.shouldShow(slotId: String?, subscriptions: List<SubscriptionFacts>): Boolean`, `data class SubscriptionFacts(guid: String, hasNodes: Boolean, expired: Boolean)`, `MainUiState.showActivation: Boolean`, `MainAction.CheckActivation`, `MainAction.ActivationShown`, `MainDataSource.activationSubscriptionId(): String?`

- [ ] **Step 1: Написать падающие тесты правила**

`ActivationGateTest.kt`:

```kotlin
package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val NOW = 1_700_000_000L

private fun facts(vararg pairs: Pair<String, Pair<Boolean, Boolean>>) = pairs.map { (guid, flags) ->
    SubscriptionFacts(guid = guid, hasNodes = flags.first, expired = flags.second)
}

class ActivationGateTest {

    @Test
    fun `empty slot shows the screen`() {
        assertTrue(ActivationGate.shouldShow(null, emptyList()))
        assertTrue(ActivationGate.shouldShow("", emptyList()))
    }

    @Test
    fun `live free slot does not`() {
        assertFalse(
            ActivationGate.shouldShow("free-slot-guid", facts("free-slot-guid" to (true to false)))
        )
    }

    @Test
    fun `expired slot with no other subscription shows the screen`() {
        assertTrue(
            ActivationGate.shouldShow("free-slot-guid", facts("free-slot-guid" to (true to true)))
        )
    }

    @Test
    fun `the user's own live subscription keeps the screen away`() {
        assertFalse(
            ActivationGate.shouldShow(
                "free-slot-guid",
                facts(
                    "free-slot-guid" to (true to true),
                    "own-sub" to (true to false),
                ),
            )
        )
    }

    @Test
    fun `a subscription without nodes is not live`() {
        assertTrue(
            ActivationGate.shouldShow("free-slot-guid", facts("free-slot-guid" to (false to false)))
        )
    }

    @Test
    fun `expire of zero means the panel sent no expiry`() {
        assertFalse(ActivationGate.isExpired(0L, NOW))
        assertTrue(ActivationGate.isExpired(NOW - 1, NOW))
        assertFalse(ActivationGate.isExpired(NOW + 1, NOW))
    }
}
```

- [ ] **Step 2: Убедиться, что тесты падают**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest --tests "com.v2ray.ang.ui.main.ActivationGateTest" > .build-logs/task7-red.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)|Unresolved" .build-logs/task7-red.log
```

Ожидание: `BUILD FAILED` (`Unresolved reference 'ActivationGate'`).

- [ ] **Step 3: Реализовать правило**

`ui/main/ActivationGate.kt`:

```kotlin
package com.v2ray.ang.ui.main

/** One subscription reduced to the two things the first-run gate asks about. */
data class SubscriptionFacts(
    val guid: String,
    val hasNodes: Boolean,
    val expired: Boolean,
)

/**
 * Decides whether the activation screen has to be on top.
 *
 * Expiry is only as fresh as the last attempt to update, so a dead free subscription next to a live
 * one the user added does not pull them back to this screen — that is the whole point of checking
 * every subscription rather than the slot alone.
 */
object ActivationGate {

    /** Panels publish expiry in the userinfo header; `0` means the header was absent. */
    fun isExpired(expireSeconds: Long, nowSeconds: Long): Boolean =
        expireSeconds > 0L && expireSeconds < nowSeconds

    fun shouldShow(slotId: String?, subscriptions: List<SubscriptionFacts>): Boolean =
        slotId.isNullOrEmpty() || subscriptions.none { it.hasNodes && !it.expired }
}
```

- [ ] **Step 4: Проверить зелёный прогон**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest --tests "com.v2ray.ang.ui.main.ActivationGateTest" > .build-logs/task7-green.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task7-green.log
```

Ожидание: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Пробросить данные через слой**

В `MainDataSource.kt` рядом с `getSubscriptions()`:

```kotlin
    fun activationSubscriptionId(): String?
```

В `MainRepository.kt`:

```kotlin
    override fun activationSubscriptionId(): String? = SettingsManager.activationSubscriptionId()
```

В `MainContract.kt` в `MainUiState` (после `routingMode`):

```kotlin
    val showActivation: Boolean = false,
```

и в `sealed interface MainAction`:

```kotlin
    data object CheckActivation : MainAction
    data object ActivationShown : MainAction
```

- [ ] **Step 6: Считать правило на IO-поточке**

В `MainViewModel.kt` в `when (action)` добавить:

```kotlin
            is MainAction.CheckActivation -> checkActivation()
            is MainAction.ActivationShown -> _uiState.update { it.copy(showActivation = false) }
```

и функцию (в том же стиле, что `setupGroupTab`):

```kotlin
    private fun checkActivation() {
        launchLoading {
            withContext(ioDispatcher) {
                val slot = dataSource.activationSubscriptionId()
                val now = System.currentTimeMillis() / 1000L
                val facts = dataSource.getSubscriptions().map { cache ->
                    SubscriptionFacts(
                        guid = cache.guid,
                        hasNodes = dataSource.getServerGuidList(cache.guid).isNotEmpty(),
                        expired = ActivationGate.isExpired(
                            cache.subscription.usageInfo?.expire ?: 0L,
                            now,
                        ),
                    )
                }
                _uiState.update { it.copy(showActivation = ActivationGate.shouldShow(slot, facts)) }
            }
        }
    }
```

- [ ] **Step 7: Перенести запуск экрана в наблюдателя**

В `MainActivity.kt` `onResume` заменяется на запрос проверки (чтение MMKV и список узлов с диска больше не происходят на main):

```kotlin
    override fun onResume() {
        super.onResume()
        // The gate lives in the ViewModel: deciding it needs the stored node lists, and that is
        // disk work. onResume rather than onCreate, because backing out returns here and has to put
        // the screen back on top.
        mainViewModel.onAction(MainAction.CheckActivation)
    }
```

В `ScreenContent` (до `MainScreen(...)`) — одноразовый запуск по тому же шаблону, что `locateTarget`:

```kotlin
        val state by mainViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.showActivation) {
            if (state.showActivation) {
                mainViewModel.onAction(MainAction.ActivationShown)
                activationLauncher.launch(Intent(this@MainActivity, ActivationActivity::class.java))
            }
        }
```

Импорты при отсутствии: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.getValue`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`.

- [ ] **Step 8: Полная проверка этапа**

```bash
cd V2rayNG
JAVA_HOME="<jdk>" cmd //c gradlew.bat --offline :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin :app:assemblePlaystoreDebug > .build-logs/task7-build.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" .build-logs/task7-build.log
```

Ожидание: `BUILD SUCCESSFUL`. Итоговое число тестов — **158**: на старте плана 147, Task 1 снимает 4 (`MainImportGuardTest`), Task 2 добавляет 3, Task 3 — 5, Task 5 — 1, Task 7 — 6. Сверять по фактическому числу в логе/`app/build/test-results/`, а не по арифметике: расхождение означает, что какой-то тест не запустился.

- [ ] **Step 9: Коммит**

```bash
git add V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/ActivationGate.kt \
        V2rayNG/app/src/test/java/com/v2ray/ang/ui/main/ActivationGateTest.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainContract.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainDataSource.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainRepository.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainViewModel.kt \
        V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainActivity.kt
git commit -m "feat(ui): экран активации возвращается, когда рабочих подписок нет

Гейт смотрит не только на маркер слота: бесплатная могла истечь, и тогда человек
должен вернуться к кнопке выдачи. Правило вынесено в ActivationGate и покрыто
тестом, расчёт идёт на IO-поточке — раньше onResume читал MMKV напрямую, а
список узлов с диска там вообще не появлялся."
```

---

### Task 8: Реестр форка и итоговая сверка

**Files:**
- Modify: `FORK.md` (главы этапа 5: «Подписочная ссылка не добавляется импортом узлов» → отменена; новая глава про выдачу; «Проверено / не проверено»)

- [ ] **Step 1: Исправить отменённую главу**

В главе «Подписочная ссылка не добавляется импортом узлов» заменить вывод на фактический: запрет отменён следующим коммитом, потому что клиент перестроен на «бесплатная выдача + свои подписки»; дубликат лечится именем группы. Отдельно вычеркнуть неверную строчку «менять ссылку больше нечем — остаётся `pm clear`»: ссылка меняется и через drawer «Подписки», и кнопкой выдачи.

- [ ] **Step 2: Добавить главу про выдачу**

Новая глава «Бесплатная подписка выдаётся кнопкой» с тем же набором, что у соседних глав: причина (токен не должен лежать в разданном APK и в публичных исходниках; отзыв остаётся у оператора), граница (`FREE_SUBSCRIPTION_ENDPOINT` пуст → кнопки нет; слот один; `activate` vs `reactivate`), что осталось закрытым этапам 6 (drawer «Подписки», `SubEditActivity`).

- [ ] **Step 3: Обновить счётчики и «Проверено / не проверено»**

Число ключей `nerpa_*` (после Task 2 и Task 6 — плюс 6, минус 1), число тестов по факту прогона, список `Not run`: запрос к живому эндпоинту, коды 403/429, TLS домена панели, подмена ссылки в мёртвом слоте, возврат гейта на экране — всё это проверяется только на устройстве и только после того, как оператор укажет рабочий адрес.

- [ ] **Step 4: Проверка диффа документации**

```bash
cd <клон репозитория>
git diff --check
```

Ожидание: пусто.

- [ ] **Step 5: Коммит**

```bash
git add FORK.md
git commit -m "docs(fork): выдача бесплатной подписки и отмена запрета импорта"
```

---

## Проверка на устройстве (после Task 7, требует рабочего адреса)

Условия, которые считаются проверенными только здесь:

1. `gradle.properties` с `NERPA_FREE_ENDPOINT=https://<домен панели>/<путь>`; сборка, установка `NerpaMobile_2.3.15_arm64-v8a.apk` на `TEST-PHONE`.
2. Чистая установка (`pm clear`) → экран активации → тап «Получить бесплатный доступ» → появляется группа `Nerpa` с узлами, кнопка подключения активна.
3. `adb exec-out run-as ru.nerpa.mobile cat files/mmkv/MAIN` → `SUB_IDS` (последняя запись) содержит ровно две группы: корзина и выданная; токен в `logcat` не встречается.
4. Повторный тап по кнопке (когда слот жив) → количество групп не меняется.
5. Ссылку в слоте менять на заведомо мёртвую нельзя из интерфейса — проверка отказа делается на тестовом адресе, который выдаёт 403/429, и на выключенном интернете.
6. Возврат с экрана активации по «Назад» при живой подписке не уводит в цикл; при мертвых всех — экран возвращается.

## Что остаётся вне этого плана

- Этап 6: удаление drawer «Подписки», `SubEditActivity` и остальных power-user экранов.
- Задача #14: три существующие строки активации со словом «панель».
- Задача #11: дыра по ABI (валиден только `arm64-v8a`).
- Задолженность по девяти локалям для ключей `nerpa_*`.
