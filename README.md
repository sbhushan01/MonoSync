<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="128" alt="MonoSync icon" />
</p>

<h1 align="center">MonoSync</h1>

<p align="center">
  <b>A hybrid Android music player that pairs YouTube Music metadata with high-fidelity Monochrome audio.</b>
</p>

<p align="center">
  <a href="https://github.com/sbhushan01/MonoSync/actions"><img src="https://github.com/sbhushan01/MonoSync/actions/workflows/build.yml/badge.svg" alt="Build Status" /></a>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/github/license/sbhushan01/MonoSync" alt="License" />
</p>

---

## ✨ What is MonoSync?

MonoSync is an Android music streaming app built around a **Hybrid Resolver** architecture:

| Layer | Source | Purpose |
|-------|--------|---------|
| **Metadata** | YouTube Music (InnerTube API) | Track titles, artists, album art, duration, synchronized lyrics |
| **Audio** | [monochrome.tf](https://monochrome.tf) | High-quality FLAC / lossless streaming |
| **Fallback** | YouTube Music (adaptive formats) | If no Monochrome match is found, stream the best available YTM audio |

The result is **YouTube Music's rich catalog** paired with **audiophile-grade streams** — without ever touching YouTube's video bandwidth.

---

## 🔀 Hybrid Resolver — How It Works

```
┌──────────────────────────────────────────────┐
│            User searches / plays             │
└────────────────────┬─────────────────────────┘
                     │
        ┌────────────▼────────────┐
        │  YouTube Music InnerTube │
        │  (MetrolistExtractor)    │
        └────────────┬────────────┘
                     │  title + artist
        ┌────────────▼────────────┐
        │  monochrome.tf API      │
        │  Fuzzy match via        │
        │  Levenshtein distance   │
        └────────────┬────────────┘
                     │
            match ≤ threshold?
           ╱                ╲
         yes                 no
          │                   │
  ┌───────▼───────┐  ┌───────▼────────┐
  │ Stream FLAC   │  │ YTM Fallback   │
  │ from Mono­    │  │ (highest-      │
  │ chrome.tf     │  │  bitrate audio)│
  └───────────────┘  └────────────────┘
```

1. **InnerTube extraction** — `MetrolistExtractor` calls the YouTube Music `player` endpoint with
   `WEB_REMIX` client context, returning track metadata and adaptive streaming formats.
2. **Monochrome search** — The track's `title + artist` is sent to the `monochrome.tf` search API.
3. **Fuzzy matching** — A Levenshtein-distance algorithm compares results; matches within a 20 %
   relative threshold (minimum edit distance of 3) are accepted.
4. **Fallback** — When no Monochrome match passes the threshold, the resolver extracts the
   highest-bitrate `audio/*` adaptive format straight from InnerTube.

> The extraction and InnerTube client logic is **inspired by** the
> [Metrolist](https://github.com/MetrolistGroup/Metrolist) project — see
> [Acknowledgements](#-acknowledgements) below.

---

## 🚀 Features

- 🎵 **Lossless streaming** — FLAC audio from Monochrome with automatic YTM fallback
- 🖼️ **Immersive Now Playing** — Gaussian-blurred dynamic backgrounds from album art
- 📝 **Synchronized lyrics** — LRC parser with auto-scrolling, highlighted current line
- 🎚️ **Audio processing** — Built-in silence skipping and loudness normalization
- 🔎 **Search & discovery** — YouTube Music catalog with instant results
- 📚 **Library & playlists** — Offline-ready via Room database caching
- 🎨 **Material 3 theming** — Dynamic color from wallpaper, dark mode by default
- 📱 **Background playback** — Media3 `MediaSessionService` with lock-screen controls

---

## 🏗️ Architecture

```
com.monosync
├── data
│   ├── db              # Room database, DAOs, entities
│   ├── remote          # MetrolistExtractor, MonochromeApiService
│   ├── repository      # TrackRepository (cache + network)
│   └── resolver        # HybridResolver (fuzzy matching engine)
├── di                  # Hilt modules
├── model               # LyricLine, LRC parser
├── playback            # Media3 service, MediaController, AudioProcessor
└── ui
    ├── components      # Reusable Compose components
    ├── home            # Home screen
    ├── search          # Search screen
    ├── library         # Library screen
    ├── player          # Now Playing screen
    ├── settings        # Settings screen
    ├── navigation      # Nav graph & bottom bar
    └── theme           # Material 3 color / typography / shapes
```

| Layer | Libraries |
|-------|-----------|
| UI | Jetpack Compose, Material 3, Coil, Navigation Compose |
| Playback | Media3 ExoPlayer, Media3 Session, HLS / DASH |
| Network | Retrofit 2, OkHttp, Gson |
| Persistence | Room |
| DI | Hilt |
| Async | Kotlin Coroutines |

---

## 🛠️ Building

### Prerequisites

- **Android Studio Hedgehog** (2023.1.1) or later
- **JDK 17** (Zulu / Adoptium recommended)
- **Android SDK 34**

### Steps

```bash
# Clone the repository
git clone https://github.com/sbhushan01/MonoSync.git
cd MonoSync

# Build a debug APK
./gradlew assembleDebug

# Install to a connected device / emulator
./gradlew installDebug
```

> **Tip:** A pre-built debug APK is generated on every push to `main` by the
> [CI workflow](.github/workflows/build.yml) — download it from the
> **Actions → Build → Artifacts** tab.

---

## ⚙️ CI / CD

| Workflow | Trigger | Artifact |
|----------|---------|----------|
| [`build.yml`](.github/workflows/build.yml) | Push to `main` | `MonoSync-Debug-APK` |

The GitHub Actions pipeline:
1. Checks out the repo
2. Sets up JDK 17 with Gradle caching
3. Runs `./gradlew assembleDebug`
4. Uploads the debug APK as a downloadable **build artifact**

---

## 🙏 Acknowledgements

### Metrolist

MonoSync's YouTube Music extraction layer — including the InnerTube client context, player request
construction, adaptive format parsing, and synchronized lyrics extraction — is **directly inspired by**
the open-source **[Metrolist](https://github.com/MetrolistGroup/Metrolist)** project by the
[MetrolistGroup](https://github.com/MetrolistGroup) team.

Specifically, the `MetrolistExtractor` class in this repository adapts patterns from Metrolist's
InnerTube integration to build `WEB_REMIX` client requests, parse `streamingData.adaptiveFormats`,
and extract LRC lyrics from browse responses.

> **Thank you to the Metrolist contributors** for making their extraction logic open and
> well-documented — MonoSync would not exist without that foundation. 🙌

### Monochrome

High-fidelity audio streams are provided by [monochrome.tf](https://monochrome.tf).

---

## 📄 License

```
Copyright (c) 2026 sbhushan01

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
