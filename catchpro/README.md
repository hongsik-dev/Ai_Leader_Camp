# CatchPro

Android project scaffold for the CatchPro return-route assistant.

## Korean guide

- [사용설명서](./사용설명서.md)
- [작업기록](./작업기록.md)
- [Gemini 하이브리드 판단 계획서](./Gemini하이브리드판단계획서.md)
- [CatchPro 작업본부](./docs/operations/catchpro-operations-hq.md)
- [GitHub 운영 규칙](./docs/operations/catchpro-github-release-process.md)
- [Notion 작업 관리](./docs/operations/catchpro-notion-task-management.md)
- [Notion 모바일 라이선스 처리](./docs/operations/catchpro-notion-license-mobile-workflow.md)
- [운영 로그 Notion 기록](./docs/operations/catchpro-ops-notion-log.md)
- [라이선스 관리 CLI](./docs/operations/catchpro-license-admin-cli.md)
- [작업트리 관리](./docs/operations/catchpro-worktree-management.md)

## Current scope

- Single `app` module based on Jetpack Compose.
- Hilt-enabled application entry point.
- Navigation graph with onboarding, dashboard, destinations, presets, history, settings, and a match-confirm screen.
- Build dependencies prepared for Room, DataStore, Navigation, and Hilt.

## Folder layout

- `app/src/main/java/com/catchpro/app`
- `app/src/main/res`

## Environment needed before first build

- JDK 17
- Android SDK / compileSdk 36
- Gradle wrapper or local Gradle installation compatible with AGP 9.1.0

## Next build steps

1. Open the project in Android Studio.
2. Let Android Studio create or sync the Gradle wrapper.
3. Add local SDK configuration through `local.properties`.
4. Replace placeholder screens with feature implementations.
