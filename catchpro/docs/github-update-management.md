# CatchPro GitHub Update Management

CatchPro updates are managed inside this repository under `catchpro/`.

## Repository Layout

- `catchpro/app/`: Android app source.
- `catchpro/research.md`: implementation research and behavior notes.
- `catchpro/CHANGELOG.md`: release history.
- `catchpro/local.properties.example`: local-only configuration template.

The parent repository also contains blog and WordPress content. Keep CatchPro app changes scoped to `catchpro/` unless the task is explicitly about blog publishing.

## What Must Not Be Committed

- `local.properties`
- API keys and secrets
- `*.db`, `*.db-wal`, `*.db-shm`
- device logs, captures, diagnostics, analysis exports
- Gradle and Android build output

## Daily Update Flow

1. Implement changes in `catchpro/`.
2. Run verification:

```powershell
cd C:\Users\misoh\Ai_HealthCare\catchpro
.\gradlew.bat testDebugUnitTest assembleDebug
```

3. Install to the connected phone:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

4. Commit only intended files:

```powershell
git status --short
git add catchpro .gitignore
git commit -m "Update CatchPro order confirmation flow"
git push origin main
```

## Branch And Tag Rule

- `main`: stable version that can be used during driving.
- `catchpro/<topic>`: experimental or risky changes.
- Tag stable field versions with:

```powershell
git tag catchpro-v0.1.11-20260520
git push origin catchpro-v0.1.11-20260520
```

## Review Rule

Every CatchPro change should be reviewed against one question first:

> Does this weaken order-list entry speed or detail-confirmation speed?

UI convenience, logging, maps, and sync features must not slow down the core confirmation path.
