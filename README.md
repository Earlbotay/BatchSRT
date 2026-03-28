# 🤖 AutoPilot AI

AI-powered Android phone automation using **Claude Sonnet 4** via **Bytez API**.

## ✨ Features

- **Main Agent + Sub Agents** — complex tasks auto-split into sequential sub-tasks
- **Smart API Key Management** — add bulk, auto-dedup, auto-isolate exhausted keys, auto-restore monthly
- **Screen Control** — tap, swipe, type via Accessibility Service
- **Screenshots** — capture screen (no recording permission needed, API 30+)
- **App Launching** — open any installed app by name
- **Web Search & Scraping** — background DuckDuckGo search + Jsoup scraping
- **Vision AI** — screenshots sent to Claude for intelligent screen analysis
- **GitHub Actions** — automatic APK build on push

## 🏗️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Database | Room |
| Network | Retrofit + OkHttp |
| AI Model | Claude Sonnet 4 via Bytez API |
| Build | Gradle 8.11.1 + AGP 8.10.0 |
| Target | Android 16 (API 36) |
| Min SDK | 30 (Android 11) |

## 🚀 Setup

1. Clone & push to GitHub → Actions builds APK automatically
2. Install APK → Add Bytez API keys → Enable Accessibility → Chat!

## 📄 License

MIT License
