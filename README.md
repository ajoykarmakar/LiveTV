# Live TV (Android & Android TV)

A highly optimized, blazing-fast Live TV application built entirely with modern Android development standards. Designed to run flawlessly on both Android Mobile and Android TV devices, this app provides a seamless streaming experience with intelligent caching, dynamic playlist parsing, and an adaptive user interface.

Download and try from here [Live TV](https://github.com/ajoykarmakar/LiveTV/blob/main/app/release/app-release.apk)

## ✨ Key Features

*   **📱📺 Adaptive Jetpack Compose UI**: A unified, responsive codebase utilizing Compose Material 3 and Compose for TV to provide a native experience across phones, tablets, and televisions.
*   **⚡ Instant Boot & Micro-Caching**: The app launches instantly by relying on a highly optimized, disk-backed JSON cache. Massive network payloads (like global logo databases) are surgically filtered using fast Regex and micro-cached to prevent memory (GC) thrashing on low-end TVs.
*   **🎬 Advanced ExoPlayer Integration**: Powered by AndroidX Media3 (ExoPlayer). Includes resilience against CDN blocking (via custom `User-Agent` and `Referer` headers) and handles audio-only stream fallbacks gracefully.
*   **🤖 Intelligent M3U Parsing**: Automatically parses remote `.m3u` playlists, deduplicates channel variants, identifies high-definition streams (adding inline "HD" badges), and categorizes channels into logical folders (Movies, Kids, News, etc.).
*   **📈 Engagement Engine**: A local `DataStore` engine tracks your watch time and seamlessly reorganizes the dashboard to prioritize your "Most Watched" channels.
*   **🖼️ Dynamic High-Quality Logos**: Integrates with global APIs to accurately match and render high-resolution channel icons using Coil.

## 🛠️ Tech Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Mobile & TV)
*   **Media Player**: AndroidX Media3 (ExoPlayer)
*   **Networking**: OkHttp3
*   **Image Loading**: Coil (with SVG and crossfade support)
*   **Storage**: Jetpack DataStore (Preferences)
*   **Serialization**: Kotlinx Serialization

## ⚠️ Disclaimer & Legal Notice

**This application acts solely as a client-side media player and parser.**

*   **Third-Party Content**: This project utilizes third-party APIs, publicly available M3U playlists (such as those provided by `iptv-org`), and external JSON data sources to fetch, map, and render channel streams and logos.
*   **No Hosting**: The developers of this application **do not host, upload, distribute, or control** any of the media streams, videos, or logos displayed within the app. 
*   **Copyright Objections**: If you are a copyright owner and have objections regarding the media content, streams, or logos being rendered, **please contact the respective third-party API authorities and playlist maintainers directly**. Removing the content from the source APIs will automatically reflect in this application, as we do not cache or host video data on our servers.

---
*Built with ❤️ for Android.*