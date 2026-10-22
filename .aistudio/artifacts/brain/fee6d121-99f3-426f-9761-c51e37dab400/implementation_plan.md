# Cult — Next-Generation Modular Android Streaming App

Cult is a high-performance, dark-themed, cinematic Android streaming application featuring a completely custom media playback engine (pure Java + C/C++ JNI demuxer and decoder pipeline, strictly zero ExoPlayer/Media3), an embedded QuickJS JavaScript extension runtime with network/crypto bridges, dynamic streaming provider plugins (MX Player, AniDap, MovieBox), hardware frosted glassmorphism (Haze backdrop blur), and Material 3 Expressive morphing motion.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed during interactive clarification and govern the implementation:

- **Confirmed Decision 1 (Active Provider)**: MX Player configured as the out-of-the-box primary active provider, with AniDap and MovieBox readily available in the Provider Switcher drawer.
- **Confirmed Decision 2 (Player Engine Architecture)**: Hybrid playback pipeline utilizing hardware-accelerated `AMediaCodec` with native C/C++ FFmpeg JNI demuxing, custom dynamic HTTP header injection (`Referer`, `Origin`, `User-Agent`), audio resampling via `libswresample` to `AudioTrack`, and CPU fallback decoding via `libavcodec`.
- **Confirmed Decision 3 (Extension Cache & Updates)**: Local sandboxed plugin cache under `context.filesDir/plugins/` with automatic background freshness check on launch, alongside manual one-click "Check for Updates" and "Clear Cache" in the Provider Management sheet.

---

## 1. Overview & Core Concept

- **What It Does**: Cult delivers a fluid, console-grade streaming client for movies, anime, and TV series. It loads dynamic provider scrapers (JavaScript modules) from remote provider registries, executes them sandboxed in an on-device QuickJS engine, parses catalogs/streams, and plays HLS (`.m3u8`), DASH, and MP4 streams through a bespoke low-latency native video engine.
- **Target Audience**: Cinephiles and streaming enthusiasts seeking an uncompromising, privacy-respecting, aesthetically breathtaking client free from bloated frameworks.
- **Key Value**: Zero Google ExoPlayer/Media3 dependencies; ultra-responsive glassmorphic UI; dynamic provider plugin ecosystem without recompiling APKs.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Cold Launch & Provider Sync**:
   - Splash displays the brand mark "CULT" with luminous morphing polygon loading animation.
   - Provider manager initializes the local plugin repository, sets MX Player as active, fetches `pro.json`, checks for updates, and populates the Home Feed.
2. **Home Feed & Discovery**:
   - **Spotlight Hero Slider**: Parallax widescreen cards for `slug-` items with dynamic dominant color extraction, title, genre chips, and "Play Now" / "Details" actions.
   - **Horizontal Carousels**: 2:3 ratio poster cards for categorized trays (Trending, Popular, Top Rated) with subtle shimmer placeholders.
3. **Deep Search & Suggestion Pills**:
   - Real-time search with 300ms debounced input.
   - Live query suggestions (`qsearch`) rendered as frosted pill tags.
   - Instant search results grid (`allsearch`) with responsive 3-column posters.
4. **Cinematic Detail & Episode Hub**:
   - Parallax collapsing header with high-resolution backdrop, gradient scrim, and unboxed metadata (`Movie · 2026 · 4K HDR · ★ 8.9`).
   - Horizontal cast rail with circular actor avatars and character roles.
   - Season selector tabs and expandable episode list with episode preview thumbnails, runtimes, and plot summaries.
   - Recommendation tray for similar titles.
5. **Custom Player Experience**:
   - Fullscreen landscape mode with edge-to-edge frosted glass HUD overlays.
   - Dual-axis gesture controls: Vertical swipe on left for screen brightness; vertical swipe on right for system media volume; double-tap left/right for 10s seek.
   - Frosted bottom sheet modals for Server switching (Yuki, Zuna, MegaPlay), audio tracks (Sub/Dub), video quality selection, and WebVTT/SRT subtitle styling.

### Visual Identity & Theme Tokens
- **Aesthetic Direction**: Cinematic Nocturne with Luminous Specular Glassmorphism. Deep space blacks (`#07080D`), midnight blues (`#0D111A`), and cyan/electric-violet luminous accents (`#00E5FF`, `#7C4DFF`).
- **Glassmorphism**: Hardware backdrop blur (radius 28dp) via `dev.chrisbanes.haze` with razor-thin specular border (`1px solid rgba(255, 255, 255, 0.12)`) and ambient gradient orbs.
- **Motion & Typography**: Fluid Material 3 Expressive morphing polygons (Circle ➔ Clover ➔ Rounded Square ➔ Star) for all loading and buffering states; clean sans-serif typography with high contrast hierarchy.

---

## 3. Key Product Decisions & Trade-Offs

- **Bespoke Media Engine over ExoPlayer**:
  - *Chosen Approach*: Pure Java controller (`CultPlayer`) backed by C/C++ JNI bindings to FFmpeg demuxer (`libavformat`), video decoder (`AMediaCodec` + `libavcodec`), audio resampler (`libswresample`), audio sink (`AudioTrack`), and OpenGL ES 2.0 / `ANativeWindow` surface renderer (`CultSurfaceView`).
  - *Why*: Strictly complies with project mandate. Enables dynamic runtime HTTP header injection (Referer, Origin) at the socket level.
- **Embedded QuickJS Runtime (`app.cash.quickjs`)**:
  - *Chosen Approach*: Sandboxed JavaScript virtual machine with three synchronous Java bridge interfaces (`NetworkBridge`, `CryptoBridge`, `Base64Bridge`).
  - *Why*: Allows dynamic scrapers to bypass typical WebView overhead while running cryptographic unpackers (AES-CBC, AES-GCM, HMAC-SHA256, HKDF) in native C.
- **Resilient Offline-First Plugin Bundling**:
  - *Chosen Approach*: Bundle default fallback scripts for MX Player, AniDap, and MovieBox in the app resources, while updating them seamlessly over-the-air from GitHub.

---

## 4. Technical Architecture & Component Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          :app / UI Layer                               │
│  MainActivity  ──►  CultApp NavHost (Compose M3 Expressive + Haze)     │
│  ├── HomeScreen        (Hero Slider + Palette, Horizontal Trays)       │
│  ├── SearchScreen      (Debounced Input, qsearch Pills, allsearch Grid)│
│  ├── DetailScreen      (Parallax Backdrop, Cast Rail, Episodes Hub)    │
│  ├── PlayerScreen      (CultSurfaceView, Gesture HUD, Sheet Overlays)  │
│  └── ProviderSheet     (Active Provider Selector, Update & Cache Sync) │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         ▼                                                 ▼
┌───────────────────────────────┐     ┌───────────────────────────────────┐
│     :core-js Runtime          │     │          :player Engine           │
│  QuickJsEngineManager         │     │  CultPlayer (Pure Java Controller)│
│  ├── ScriptLoader & Cache     │     │  ├── CultSurfaceView (GL / Window)│
│  ├── NetworkBridge (OkHttp)   │     │  ├── SyncClock (A/V PTS Sync)     │
│  ├── CryptoBridge (JCE/AES)   │     │  ├── AudioTrackSink (PCM 16-bit)  │
│  └── Base64Bridge (RFC 4648)  │     │  └── SubtitleOverlay (VTT/SRT)    │
└───────────────┬───────────────┘     └─────────────────┬─────────────────┘
                │                                       │
                ▼                                       ▼
┌───────────────────────────────┐     ┌───────────────────────────────────┐
│  Remote Provider Ecosystem    │     │      Native JNI C++ (FFmpeg)      │
│  pro.json Registry            │     │  ffmpeg_jni.cpp                   │
│  Plugins: catalog, config,    │     │  ├── avformat (HLS/DASH/MKV/MP4)  │
│  discovery, meta, seasons,    │     │  ├── AVDictionary (Headers/Origin)│
│  streams.js                   │     │  └── AMediaCodec / libavcodec     │
└───────────────────────────────┘     └───────────────────────────────────┘
```

### Component & State Mapping
- `CultPlayerViewModel`: Exposes `PlayerUiState` (playback position, buffer percentage, audio tracks, subtitle tracks, active server). Connects surface callbacks to `CultPlayer`.
- `ProviderViewModel`: Manages active provider, loads categories, triggers paginated discovery feeds, and handles scraper failures with graceful error toasts.
- `DetailViewModel`: Fetches meta, cast, seasons, episodes, and resolves direct stream links with dynamic referer headers.
- `SearchViewModel`: Emits debounced query states, collects qsearch suggestions, and updates 3-column search grid.

---

## 5. Execution Steps Plan

1. **Project Configuration & Dependencies**:
   - Update `app/build.gradle.kts`, `settings.gradle.kts`, and `gradle/libs.versions.toml` with `app.cash.quickjs:quickjs-android`, `dev.chrisbanes.haze`, `androidx.palette:palette-ktx`, `com.google.code.gson:gson`, and NDK / CMake bindings.
   - Configure application package `com.v02.senapi` and set name to "Cult" in `metadata.json` and `strings.xml`.
2. **Custom Player Implementation (`:player`)**:
   - Implement `CultPlayer.java`, `CultSurfaceView.java`, `SyncClock.java`, `SubtitleTrack.java`, `VttParser.java`, and `AudioSink.java`.
   - Implement native C/C++ JNI demuxer and decoder bridge `ffmpeg_jni.cpp` and `CMakeLists.txt` supporting dynamic HTTP headers (`Referer`, `Origin`, `User-Agent`) and `AMediaCodec` hardware pipeline.
3. **QuickJS Engine & Provider Runtime (`:core-js`)**:
   - Implement `NetworkBridge.kt`, `CryptoBridge.kt`, and `Base64Bridge.kt`.
   - Implement `ProviderManager.kt` handling registry downloads (`pro.json`), local caching, automatic launch update checks, and JSON request/response execution (`catalog`, `home`, `qsearch`, `allsearch`, `meta`, `season_info`, `stream`).
   - Bundle robust default provider scripts for offline resilience.
4. **Cinematic UI & Screen Workflows**:
   - Implement Theme tokens, Glassmorphic Haze styles, and Material 3 Expressive morphing polygon `ExpressiveMorphLoadingIndicator`.
   - Build `HomeScreen` with auto-scrolling spotlight hero slider and horizontal media carousels.
   - Build `SearchScreen` with debounced search, query pills, and responsive poster grid.
   - Build `DetailScreen` with collapsing backdrop, cast avatars, season/episode list, and recommendations.
   - Build `PlayerScreen` with gesture detection, brightness/volume overlay HUDs, server picker, and subtitle selector.
   - Build `ProviderDialog` for provider switching and cache management.
5. **Verification**:
   - Run compilation and verify complete feature set.
