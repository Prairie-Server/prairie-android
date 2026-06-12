# Ebook Reader — Phase 3: Fonts + Brightness — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two reflowable-reader controls from the enhancements spec §5: (1) a **font-family** selector (serif / sans / dyslexic-friendly) that applies across every reflowable format (EPUB, TXT/Markdown, FB2/FBZ); and (2) an in-reader **brightness** slider that overrides system brightness while reading and restores it on exit. Both wire into the existing reader settings sheet and are **hidden for fixed-layout formats** (PDF, comic), exactly like the existing text-size / margin / theme controls.

**Architecture:**
- `ReaderDisplaySettings` (KMP `android-shared`) gains a `fontFamily: ReaderFontFamily` enum field (default `Default`). It is serialized through the existing `EbookLocalStateStore.writeDisplaySettings` path; adding a defaulted `@Serializable` field is backward-compatible with already-persisted settings JSON.
- A pure `ReaderFontStack` mapper turns a `ReaderFontFamily` into a CSS `font-family` stack string. The WebView reflowable path (`EpubReader.withReaderCss`, and — if Phase 2's `PaginatedWebReader` has shipped — its epub.js `themes.font()` / register-theme bridge) injects that stack. The Compose reflowable path (`TextReader` / `FictionBookReader` via shared `TextDocumentContent`) maps the same enum to a Compose `FontFamily`.
- The dyslexic-friendly face is bundled as an Android font resource (`res/font/opendyslexic_regular.ttf`); the CSS path references it via a `@font-face` whose `src` is the bundled asset copied to the reader cache, with a `sans-serif` fallback so the control still works if the asset is absent.
- `ReaderCapabilities` gains `supportsFontFamily`, set `true` for the reflowable formats (EPUB, TXT, Markdown, FB2, FBZ) and `false` for PDF / CBZ / unsupported, mirroring `supportsTextSize`.
- Brightness lives in `ReaderScreen`: a `0f..1f` value held in `ReaderUiState` (persisted with display settings so it survives reopen), pushed into the host Activity's `window.attributes.screenBrightness` via a `DisposableEffect` that restores `BRIGHTNESS_OVERRIDE_NONE` when the reader leaves composition. A pure `clampBrightness` helper keeps the value in `0f..1f`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 `Slider`, `FontFamily`), Android `WindowManager.LayoutParams.screenBrightness` / `BRIGHTNESS_OVERRIDE_NONE`, WebView CSS injection (`loadDataWithBaseURL` + `@font-face`), kotlinx.serialization.

Commands assume the repository root (`silo-android`) is the cwd.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt` | `ReaderDisplaySettings` (+`fontFamily`), `ReaderFontFamily` enum, `ReaderCapabilities` (+`supportsFontFamily`) and its `forFormat` matrix. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderFontStack.kt` | **New.** Pure `readerCssFontStack(ReaderFontFamily): String` mapper (enum → CSS font-family stack). Also exposes the dyslexic font-family token shared by CSS `@font-face`. |
| `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderFontStackTest.kt` | **New.** Unit tests for the font-stack mapper. |
| `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderCapabilitiesTest.kt` | **New.** Unit tests for the capabilities matrix (`supportsFontFamily` per format). |
| `androidApp/src/androidMain/res/font/opendyslexic_regular.ttf` | **New asset.** Bundled OpenDyslexic Regular face (SIL OFL) for the dyslexic option. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFonts.kt` | **New.** Compose-side `composeFontFamily(ReaderFontFamily): FontFamily` mapper (loads the bundled font resource for the dyslexic option) and the `@font-face`/asset-copy helper used by the WebView path. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt` | Inject the selected font stack into `withReaderCss`. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt` | Apply `composeFontFamily` in `TextDocumentContent` (shared by TXT/MD and FB2). |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt` | Add the font-family row + brightness slider to `ReaderSettingsSheet`; drive the window brightness override. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderBrightness.kt` | **New.** Pure `clampBrightness(Float): Float` + `applyWindowBrightness`/`restoreWindowBrightness` window helpers. |
| `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderBrightnessTest.kt` | **New.** Unit tests for `clampBrightness`. |
| `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt` | Hold `brightness` in `ReaderUiState`; persist it with display settings. |
| `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/EbookLocalStateStore.kt` | No code change required (serializes `ReaderDisplaySettings` whole); brightness rides along inside it — see Task 6. |

If Phase 2's `PaginatedWebReader` has shipped, also update its epub.js theme/CSS injection in the file that owns it (search: `rg -l "PaginatedWebReader" androidApp/src`); Task 4 covers both the per-chapter `EpubReader` and the `PaginatedWebReader` branches.

---

### Task 1 — `ReaderFontFamily` model + capabilities flag (TDD pure logic)

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderControls.kt`
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderCapabilitiesTest.kt` (new)

- [ ] Add the enum to `ReaderControls.kt` above `ReaderDisplaySettings`:
  ```kotlin
  @Serializable
  enum class ReaderFontFamily { Default, Serif, SansSerif, Dyslexic }
  ```
- [ ] Add `fontFamily` to `ReaderDisplaySettings` as a defaulted field (keeps existing persisted JSON loadable):
  ```kotlin
  @Serializable
  data class ReaderDisplaySettings(
      val theme: ReaderTheme = ReaderTheme.System,
      val textScale: Float = 1f,
      val marginScale: Float = 1f,
      val fontFamily: ReaderFontFamily = ReaderFontFamily.Default,
      val brightness: Float = BRIGHTNESS_SYSTEM,
  ) {
      fun normalized(): ReaderDisplaySettings = copy(
          textScale = textScale.coerceIn(0.8f, 1.6f),
          marginScale = marginScale.coerceIn(0.75f, 1.5f),
          brightness = if (brightness < 0f) BRIGHTNESS_SYSTEM else brightness.coerceIn(0f, 1f),
      )

      companion object {
          /** Sentinel: follow system brightness (no window override). */
          const val BRIGHTNESS_SYSTEM: Float = -1f
      }
  }
  ```
  (`brightness` is added here now so Task 6 persistence is a no-op; `BRIGHTNESS_SYSTEM = -1f` matches Android's `BRIGHTNESS_OVERRIDE_NONE` semantics — "no override".)
- [ ] Add `supportsFontFamily: Boolean` to `ReaderCapabilities` (place it right after `supportsTextSize`, default not needed — set explicitly everywhere):
  ```kotlin
  data class ReaderCapabilities(
      val supportsBookmarks: Boolean,
      val supportsPageJump: Boolean,
      val supportsSections: Boolean,
      val supportsTheme: Boolean,
      val supportsTextSize: Boolean,
      val supportsFontFamily: Boolean,
      val supportsMargins: Boolean,
      val supportsExternalOnly: Boolean = false,
  )
  ```
- [ ] Update every branch of `ReaderCapabilities.forFormat`:
  - `BookFormat.Epub`: `supportsFontFamily = true`.
  - `BookFormat.Pdf, BookFormat.Cbz`: `supportsFontFamily = false`.
  - `BookFormat.Txt, BookFormat.Markdown, BookFormat.Fb2, BookFormat.Fbz`: `supportsFontFamily = true`.
  - `else`: `supportsFontFamily = false`.
- [ ] Write `ReaderCapabilitiesTest.kt` (JUnit 4 via `kotlin-test-junit`, matching the module's existing test idiom). Assert:
  - `forFormat(BookFormat.Epub).supportsFontFamily` is `true`.
  - `forFormat(BookFormat.Txt).supportsFontFamily` and `BookFormat.Fb2` are `true`.
  - `forFormat(BookFormat.Pdf).supportsFontFamily` and `BookFormat.Cbz` are `false`.
  - `forFormat(BookFormat.Unknown).supportsFontFamily` is `false`.
  - `supportsFontFamily` tracks `supportsTextSize` for every `BookFormat` value: `BookFormat.entries.forEach { assertEquals(it.name, forFormat(it).supportsTextSize, forFormat(it).supportsFontFamily) }`.
  ```kotlin
  package com.continuum.app.common.ebook

  import com.continuum.app.model.book.BookFormat
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertFalse
  import kotlin.test.assertTrue

  class ReaderCapabilitiesTest {
      @Test fun epubSupportsFontFamily() {
          assertTrue(ReaderCapabilities.forFormat(BookFormat.Epub).supportsFontFamily)
      }

      @Test fun fixedLayoutHidesFontFamily() {
          assertFalse(ReaderCapabilities.forFormat(BookFormat.Pdf).supportsFontFamily)
          assertFalse(ReaderCapabilities.forFormat(BookFormat.Cbz).supportsFontFamily)
      }

      @Test fun fontFamilyTracksTextSize() {
          BookFormat.entries.forEach { format ->
              val caps = ReaderCapabilities.forFormat(format)
              assertEquals(caps.supportsTextSize, caps.supportsFontFamily, format.name)
          }
      }
  }
  ```
- [ ] Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.ebook.ReaderCapabilitiesTest"` — green.

### Task 2 — `ReaderFontStack` CSS mapper (TDD pure logic)

**Files:**
- `android-shared/src/androidMain/kotlin/com/continuum/app/common/ebook/ReaderFontStack.kt` (new)
- `android-shared/src/androidUnitTest/kotlin/com/continuum/app/common/ebook/ReaderFontStackTest.kt` (new)

- [ ] Create `ReaderFontStack.kt`. The dyslexic stack names a `@font-face`-registered family (`DYSLEXIC_FONT_FAMILY`) the WebView path defines, then falls back to generic `sans-serif`:
  ```kotlin
  package com.continuum.app.common.ebook

  /** CSS family token registered via @font-face for the bundled dyslexic face. */
  const val DYSLEXIC_FONT_FAMILY: String = "ReaderDyslexic"

  /**
   * Maps a [ReaderFontFamily] to a CSS `font-family` stack for the WebView
   * reflowable path (EPUB chapters / epub.js theme). [Default] returns an
   * empty string so the document's own stylesheet wins.
   */
  fun readerCssFontStack(family: ReaderFontFamily): String = when (family) {
      ReaderFontFamily.Default -> ""
      ReaderFontFamily.Serif -> "Georgia, 'Times New Roman', serif"
      ReaderFontFamily.SansSerif -> "'Helvetica Neue', Arial, sans-serif"
      ReaderFontFamily.Dyslexic -> "'$DYSLEXIC_FONT_FAMILY', sans-serif"
  }
  ```
- [ ] Write `ReaderFontStackTest.kt`:
  ```kotlin
  package com.continuum.app.common.ebook

  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class ReaderFontStackTest {
      @Test fun defaultYieldsEmptyStack() {
          assertEquals("", readerCssFontStack(ReaderFontFamily.Default))
      }

      @Test fun serifEndsWithGenericSerif() {
          assertTrue(readerCssFontStack(ReaderFontFamily.Serif).trimEnd().endsWith("serif"))
      }

      @Test fun sansEndsWithGenericSans() {
          assertTrue(readerCssFontStack(ReaderFontFamily.SansSerif).trimEnd().endsWith("sans-serif"))
      }

      @Test fun dyslexicReferencesRegisteredFamilyWithFallback() {
          val stack = readerCssFontStack(ReaderFontFamily.Dyslexic)
          assertTrue(stack.contains(DYSLEXIC_FONT_FAMILY))
          assertTrue(stack.trimEnd().endsWith("sans-serif"))
      }
  }
  ```
- [ ] Run: `./gradlew :android-shared:testDebugUnitTest --tests "com.continuum.app.common.ebook.ReaderFontStackTest"` — green.

### Task 3 — Bundle the dyslexic font + Compose/WebView font mappers

**Files:**
- `androidApp/src/androidMain/res/font/opendyslexic_regular.ttf` (new asset)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderFonts.kt` (new)

- [ ] Add the bundled face. Download OpenDyslexic Regular (SIL Open Font License 1.1) and place the `.ttf` at `androidApp/src/androidMain/res/font/opendyslexic_regular.ttf`. Resource font names must be lowercase/underscore only — the chosen filename is valid. Verify it landed:
  ```bash
  ls -l androidApp/src/androidMain/res/font/opendyslexic_regular.ttf
  ```
- [ ] Create `ReaderFonts.kt` with the Compose mapper. `Default`/`Serif`/`SansSerif` use Compose generic families; `Dyslexic` loads the bundled resource:
  ```kotlin
  package com.continuum.app.android.ui.screens.reader

  import androidx.compose.ui.text.font.Font
  import androidx.compose.ui.text.font.FontFamily
  import com.continuum.app.android.R
  import com.continuum.app.common.ebook.ReaderFontFamily

  private val DyslexicFontFamily: FontFamily =
      FontFamily(Font(R.font.opendyslexic_regular))

  /** Compose [FontFamily] for the reflowable Compose path (TXT/MD/FB2). */
  fun composeFontFamily(family: ReaderFontFamily): FontFamily = when (family) {
      ReaderFontFamily.Default -> FontFamily.Default
      ReaderFontFamily.Serif -> FontFamily.Serif
      ReaderFontFamily.SansSerif -> FontFamily.SansSerif
      ReaderFontFamily.Dyslexic -> DyslexicFontFamily
  }
  ```
- [ ] In the same file, add the WebView `@font-face` CSS helper. It emits a `@font-face` only for the dyslexic option, copying the bundled `.ttf` out of resources into the reader cache so a `file://` URL can reference it (WebView cannot read `res/` directly). It returns an empty string for every other option:
  ```kotlin
  import android.content.Context
  import com.continuum.app.common.ebook.DYSLEXIC_FONT_FAMILY
  import java.io.File

  /**
   * CSS `@font-face` block registering the bundled dyslexic face under
   * [DYSLEXIC_FONT_FAMILY] for the WebView reflowable path. Empty for every
   * non-dyslexic option (those use platform-generic CSS families).
   * Copies the resource to [Context.getCacheDir]/readers/fonts once.
   */
  fun readerFontFaceCss(context: Context, family: ReaderFontFamily): String {
      if (family != ReaderFontFamily.Dyslexic) return ""
      val fontsDir = File(context.cacheDir, "readers/fonts").apply { mkdirs() }
      val target = File(fontsDir, "opendyslexic_regular.ttf")
      if (!target.isFile) {
          context.resources.openRawResource(R.font.opendyslexic_regular).use { input ->
              target.outputStream().use { input.copyTo(it) }
          }
      }
      val url = "file://${target.absolutePath}"
      return """
          <style>
          @font-face {
            font-family: '$DYSLEXIC_FONT_FAMILY';
            src: url('$url');
            font-display: swap;
          }
          </style>
      """.trimIndent()
  }
  ```
  (Manual verify is covered in Task 7; this task only needs to compile.)
- [ ] Compile-check: `./gradlew :androidApp:compileDebugKotlin` — succeeds (confirms `R.font.opendyslexic_regular` resolves, proving the asset is wired).

### Task 4 — Apply font to the WebView reflowable path (EPUB / PaginatedWebReader)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/EpubReader.kt`
- (If shipped) the file owning `PaginatedWebReader` — find with `rg -l "PaginatedWebReader" androidApp/src`.

- [ ] In `EpubReader.kt`, thread a `Context` into `withReaderCss` so the `@font-face` block can be built. `EpubChapter` already has `LocalContext`; pass the cached `@font-face` string and the selected family into the styler. Replace the current `withReaderCss(settings)` call. Update the producer:
  ```kotlin
  val fontFaceCss = remember(settings.fontFamily) { readerFontFaceCss(context, settings.fontFamily) }
  val content by produceState<EpubChapterContent?>(initialValue = null, book, href, settings, fontFaceCss) {
      value = withContext(Dispatchers.IO) {
          EpubChapterContent(href?.let { book.readChapterHtml(it)?.withReaderCss(settings, fontFaceCss) })
      }
  }
  ```
  (`EpubChapter` needs `val context = LocalContext.current` near its top; import `androidx.compose.ui.platform.LocalContext`.)
- [ ] Extend `withReaderCss` to inject the font stack + `@font-face`. The `font-family` is only set on `body` when the stack is non-empty (so `Default` leaves the document stylesheet untouched):
  ```kotlin
  private fun String.withReaderCss(settings: ReaderDisplaySettings, fontFaceCss: String): String {
      val normalized = settings.normalized()
      val marginEm = normalized.marginScale * 1.2f
      val fontPercent = (normalized.textScale * 100).toInt()
      val colors = when (normalized.theme) {
          ReaderTheme.System, ReaderTheme.Light -> "color: #1c1b1f; background: #fffbfe;"
          ReaderTheme.Sepia -> "color: #2b2118; background: #f4ecd8;"
          ReaderTheme.Dark -> "color: #e6e1e5; background: #1c1b1f;"
      }
      val fontStack = readerCssFontStack(normalized.fontFamily)
      val fontRule = if (fontStack.isBlank()) "" else "font-family: $fontStack;"
      val style = """
          $fontFaceCss
          <style>
          html, body { $colors }
          body { font-size: ${fontPercent}%; margin: ${marginEm}em; line-height: 1.55; $fontRule }
          img { max-width: 100%; height: auto; }
          </style>
      """.trimIndent()
      val headMatch = Regex("""<head(\s[^>]*)?>""", RegexOption.IGNORE_CASE).find(this)
          ?: return "$style\n$this"
      val insertAt = headMatch.range.last + 1
      return replaceRange(insertAt, insertAt, "\n$style")
  }
  ```
  (Add imports `com.continuum.app.common.ebook.readerCssFontStack`.)
- [ ] **If `PaginatedWebReader` (Phase 2) exists:** locate where it injects size/theme/margins into epub.js (typically `rendition.themes.register(...)` / `themes.font(...)` / `themes.override("font-family", ...)`). Add a `font-family` override built from `readerCssFontStack(settings.fontFamily)`, and inject the `readerFontFaceCss(...)` block into the epub.js content document (via `rendition.themes.register` CSS or the existing injected-stylesheet bridge — match whatever Phase 2 established). Re-apply on `settings.fontFamily` change exactly as size/theme already re-apply. If `PaginatedWebReader` does **not** exist yet, record it in **Deferred** and ship only the per-chapter `EpubReader` path.
- [ ] Compile-check: `./gradlew :androidApp:compileDebugKotlin` — succeeds.

### Task 5 — Apply font to the Compose reflowable path (TXT / MD / FB2)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/TextReader.kt`

- [ ] In `TextDocumentContent`, set `fontFamily` on the `Text` from the selected setting (this composable is shared by `TextReader` and `FictionBookReader`, so both formats get fonts in one change):
  ```kotlin
  Text(
      text = text,
      color = colors.foreground,
      fontSize = (18.sp * settings.textScale),
      lineHeight = (28.sp * settings.textScale),
      fontFamily = composeFontFamily(settings.fontFamily),
      style = MaterialTheme.typography.bodyLarge,
  )
  ```
  (`composeFontFamily` is in the same package, no import needed; add `androidx.compose.ui.text.font.FontFamily` only if referenced directly — it is not here.)
- [ ] Compile-check: `./gradlew :androidApp:compileDebugKotlin` — succeeds.

### Task 6 — Persist font + brightness in the ViewModel state (no store change)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt`

- [ ] `fontFamily` is already inside `ReaderDisplaySettings`, which `setDisplaySettings` persists via `localStateStore.writeDisplaySettings`. **No new persistence code is needed** for font — confirm `setDisplaySettings` calls `.normalized()` (it does) so the new field round-trips. Verify by reading `setDisplaySettings`; no edit expected.
- [ ] `brightness` also rides inside `ReaderDisplaySettings` (Task 1). It therefore persists and reloads through the same `displaySettings` path with **zero** store changes. No `EbookLocalStateStore` edit is required; delete the placeholder note in the File Structure table mentally — the store serializes the whole settings object.
- [ ] Add a convenience setter so the brightness slider does not have to reconstruct the whole settings object at the screen, keeping the slider callback small:
  ```kotlin
  fun setBrightness(brightness: Float) {
      val current = _uiState.value.displaySettings
      setDisplaySettings(current.copy(brightness = brightness))
  }
  ```
  (Place next to `setDisplaySettings`. `setDisplaySettings` already normalizes, which now clamps brightness via Task 1's `normalized()`.)
- [ ] Compile-check: `./gradlew :androidApp:compileDebugKotlin` — succeeds.

### Task 7 — Brightness window override + clamp helper (TDD pure logic, then device verify)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderBrightness.kt` (new)
- `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/reader/ReaderBrightnessTest.kt` (new)
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] Create `ReaderBrightness.kt` with the pure clamp + window helpers:
  ```kotlin
  package com.continuum.app.android.ui.screens.reader

  import android.app.Activity
  import android.content.Context
  import android.content.ContextWrapper
  import android.view.WindowManager
  import com.continuum.app.common.ebook.ReaderDisplaySettings

  /**
   * Clamps a reader brightness to the window-attribute range. Negative inputs
   * (the [ReaderDisplaySettings.BRIGHTNESS_SYSTEM] sentinel) collapse to
   * [WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE] (-1f = follow system).
   */
  fun clampBrightness(value: Float): Float =
      if (value < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
      else value.coerceIn(0f, 1f)

  /** Pushes [brightness] into the host window, overriding system brightness. */
  fun applyWindowBrightness(context: Context, brightness: Float) {
      val window = context.findActivity()?.window ?: return
      window.attributes = window.attributes.apply {
          screenBrightness = clampBrightness(brightness)
      }
  }

  /** Restores system-controlled brightness (call on reader exit). */
  fun restoreWindowBrightness(context: Context) {
      val window = context.findActivity()?.window ?: return
      window.attributes = window.attributes.apply {
          screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
      }
  }

  private fun Context.findActivity(): Activity? {
      var ctx: Context? = this
      while (ctx is ContextWrapper) {
          if (ctx is Activity) return ctx
          ctx = ctx.baseContext
      }
      return null
  }
  ```
- [ ] Write `ReaderBrightnessTest.kt` — pure assertions on `clampBrightness` (no Android window needed; `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` is the constant `-1f`, available under the module's `unitTests` stub config):
  ```kotlin
  package com.continuum.app.android.ui.screens.reader

  import com.continuum.app.common.ebook.ReaderDisplaySettings
  import kotlin.test.Test
  import kotlin.test.assertEquals
  import kotlin.test.assertTrue

  class ReaderBrightnessTest {
      @Test fun midRangePassesThrough() {
          assertEquals(0.5f, clampBrightness(0.5f))
      }

      @Test fun aboveOneClampsToOne() {
          assertEquals(1f, clampBrightness(1.5f))
      }

      @Test fun systemSentinelMapsToOverrideNone() {
          assertTrue(clampBrightness(ReaderDisplaySettings.BRIGHTNESS_SYSTEM) < 0f)
      }

      @Test fun zeroIsAllowed() {
          assertEquals(0f, clampBrightness(0f))
      }
  }
  ```
  If `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` is unavailable under the unit-test stub (returns 0 instead of -1), replace the helper's reference and the test with a local `const val BRIGHTNESS_OVERRIDE_NONE = -1f` defined in `ReaderBrightness.kt`; this is its documented value.
- [ ] Run: `./gradlew :androidApp:testDebugUnitTest --tests "com.continuum.app.android.ui.screens.reader.ReaderBrightnessTest"` — green.
- [ ] Wire the override into `ReaderScreen`. Near the top of `ReaderScreen`, after `val state by ...`, add a `DisposableEffect` keyed on the brightness value that applies on change and restores on dispose:
  ```kotlin
  val context = LocalContext.current
  DisposableEffect(state.displaySettings.brightness) {
      applyWindowBrightness(context, state.displaySettings.brightness)
      onDispose { restoreWindowBrightness(context) }
  }
  ```
  (Add imports `androidx.compose.runtime.DisposableEffect`, `androidx.compose.ui.platform.LocalContext`.)

### Task 8 — Settings sheet UI: font row + brightness slider (device verify)

**Files:**
- `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderScreen.kt`

- [ ] In `ReaderSettingsSheet`, add a **font-family** row guarded by `capabilities.supportsFontFamily` (so it hides for PDF/comic exactly like text-size). Place it after the theme row, before the text-size slider:
  ```kotlin
  if (capabilities.supportsFontFamily) {
      Text(
          "Font",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(start = 16.dp, top = 8.dp),
      )
      Row(modifier = Modifier.padding(horizontal = 16.dp)) {
          listOf(
              ReaderFontFamily.Default to "Default",
              ReaderFontFamily.Serif to "Serif",
              ReaderFontFamily.SansSerif to "Sans",
              ReaderFontFamily.Dyslexic to "Dyslexic",
          ).forEach { (family, label) ->
              TextButton(onClick = { onSettingsChange(settings.copy(fontFamily = family).normalized()) }) {
                  Text(label)
              }
          }
      }
  }
  ```
  (Add import `com.continuum.app.common.ebook.ReaderFontFamily`.)
- [ ] Add a **brightness** slider at the end of `ReaderSettingsSheet`. It is shown whenever any reflow control is — gate it on the same `supportsSettings` set the sheet already requires; brightness is useful for every in-app reader, but to stay symmetric with "hide for fixed-layout," gate it on `capabilities.supportsTextSize` (true for all reflowable formats, false for PDF/comic). The slider maps the `BRIGHTNESS_SYSTEM` sentinel (`-1f`) to the slider's midpoint default so the thumb is grabbable:
  ```kotlin
  if (capabilities.supportsTextSize) {
      Text(
          "Brightness",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(start = 16.dp, top = 8.dp),
      )
      val sliderValue = settings.brightness.takeIf { it in 0f..1f } ?: 0.5f
      Slider(
          value = sliderValue,
          onValueChange = { onSettingsChange(settings.copy(brightness = it).normalized()) },
          valueRange = 0f..1f,
          modifier = Modifier.padding(horizontal = 16.dp),
      )
  }
  ```
  (`ReaderSettingsSheet` already imports `Slider`, `Text`, `Row`, `TextButton`, `Modifier`, `dp`.)
- [ ] Note: the `supportsSettings` flag in `ReaderScreen` already gates the whole settings IconButton on `supportsTextSize || supportsMargins || supportsTheme`. Add `|| state.capabilities.supportsFontFamily` for completeness (it is redundant today since `supportsFontFamily` tracks `supportsTextSize`, but keeps the gate honest if the matrix diverges later):
  ```kotlin
  val supportsSettings = state.capabilities.supportsTextSize ||
      state.capabilities.supportsMargins ||
      state.capabilities.supportsTheme ||
      state.capabilities.supportsFontFamily
  ```
- [ ] Build + install on a connected device/emulator:
  ```bash
  ./gradlew :androidApp:installDebug
  adb shell am start -n com.continuum.app/com.continuum.app.android.MainActivity
  ```
  (Confirm the launcher activity name first: `rg -n "android.intent.action.MAIN" androidApp/src/androidMain/AndroidManifest.xml -A2`.)
- [ ] **Manual verify — font family.** Open an EPUB, then a TXT and an FB2. Open reader settings (tune icon). For each format:
  - The **Font** row shows `Default / Serif / Sans / Dyslexic`.
  - Tapping `Serif` re-renders the body text in a serif face; `Sans` in sans; `Dyslexic` in the bundled OpenDyslexic face (distinctive weighted bottoms).
  - Open a **PDF** and a **CBZ**: the Font row is **absent** (fixed-layout).
  - Close and reopen the book: the chosen font persists (loaded from `reader-settings.json`).
- [ ] **Manual verify — brightness.** With a reflowable book open:
  - Drag the **Brightness** slider to the far left — screen visibly dims; to the far right — screen visibly brightens, independent of the system brightness setting.
  - Confirm the override is live, not the system value:
    ```bash
    adb shell settings get system screen_brightness   # system value unchanged while overridden
    adb shell dumpsys window | rg -i "screenBrightness"
    ```
    `dumpsys window` should report the window's `screenBrightness` matching the slider (0..1), while `settings get system screen_brightness` stays at its pre-reader value.
  - Press Back to leave the reader. The screen returns to system brightness (override released — `dumpsys window` no longer pins the value).
  - Reopen the book: the last brightness is restored from settings.
  - Open a **PDF**/**CBZ**: the Brightness slider is **absent**.

### Task 9 — Self-review vs spec, full build + lint

- [ ] Re-read spec §5 and §8 phase 3. Confirm each deliverable:
  - Font-family serif/sans/dyslexic added to settings + capabilities — **yes** (Tasks 1, 8).
  - Applied across reflowable formats: EPUB via CSS (Task 4), text/FB2 via Compose (Task 5) — **yes**.
  - Dyslexic font bundled with system fallback — **yes** (Task 3 asset + `sans-serif` CSS fallback / `composeFontFamily`).
  - In-reader brightness slider via window `screenBrightness`, overrides while reading, restores on exit — **yes** (Tasks 7–8 `DisposableEffect` restore).
  - Both wired into the existing settings panel; font controls hidden for fixed-layout — **yes** (`supportsFontFamily` gate, Task 8).
- [ ] Confirm `PaginatedWebReader` handling: if it shipped, Task 4's second bullet applied the font there; if not, it is listed in Deferred and the per-chapter path still delivers EPUB fonts.
- [ ] Full verification:
  ```bash
  ./gradlew :android-shared:testDebugUnitTest :androidApp:testDebugUnitTest
  ./gradlew :androidApp:assembleDebug
  ./gradlew :androidApp:lintDebug
  ```
  All green. Fix any failures inline before marking complete.
- [ ] Confirm no destructive change to persisted settings: a `reader-settings.json` written before this change (theme/textScale/marginScale only) still deserializes — `fontFamily` and `brightness` fall back to their defaults. (kotlinx.serialization tolerates missing keys for defaulted fields by default.)

---

## Deferred / out of scope

- **`PaginatedWebReader` (epub.js) font injection** — this plan assumes Phase 2 shipped it; at authoring time `rg -l "PaginatedWebReader" androidApp/src` returns nothing, so it is **not yet in-tree**. If still absent at execution, apply fonts only to the per-chapter `EpubReader` WebView path (Task 4 bullet 1–2) and re-run Task 4's epub.js bullet when Phase 2 lands. Captured here, not silently dropped.
- **Bold/italic dyslexic variants** — only OpenDyslexic Regular is bundled; styled runs fall back to the regular face. Adding the bold/italic `.ttf`s and a multi-`Font` `FontFamily` is a follow-up if reviewers want true emphasis fidelity.
- **Per-book vs global font/brightness** — these persist per (server, profile) like existing theme/size/margins (global to the profile, via `readerDisplaySettings`), not per-book. Matches current behavior; per-book overrides are out of scope.
- **Fixed-layout (PDF/comic) brightness** — intentionally hidden alongside reflow controls per spec §9 assumptions. If product later wants brightness on PDF/comic, relax the Task 8 `supportsTextSize` gate to a dedicated `supportsBrightness` capability.
