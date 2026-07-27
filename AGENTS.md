# CatchPro Codex Rules

## Update Workflow

- Always treat `main` as the stable, deployable branch.
- Before starting CatchPro work, switch to `main` and pull the latest GitHub state.
- Create a task branch with the `codex/` prefix for every implementation task.
- Do not develop directly on `main` unless the user explicitly asks for it.
- Preserve unrelated local files, logs, device dumps, and user changes.

Recommended start:

```bash
git switch main
git pull --ff-only origin main
git switch -c codex/<task-name>
```

## Verification Before Delivery

- Build the affected APK flavors before installing or publishing changes.
- For Insung/CatchPro changes, verify that order-confirmation speed is not degraded.
- For Navi changes, verify map rendering, AWS sync behavior, and route calculation behavior.
- For server changes, back up the remote file before deployment and confirm health/API responses after restart.
- Do not deploy a customer-facing APK when build, install, or core runtime checks fail.

## GitHub Workflow

- Stage only source, docs, and intentional server/app files.
- Do not stage local Android/Gradle caches, device databases, screenshots, temporary logs, or generated diagnostics.
- Commit with a short, clear English message.
- Push the `codex/` branch to GitHub.
- Create a pull request into `main`.
- After merge, switch local workspace back to `main` and sync with `origin/main`.

Recommended finish:

```bash
git status --short
git add <intentional-files>
git commit -m "<short change summary>"
git push origin codex/<task-name>
```

## Release Notes

- For meaningful CatchPro updates, update or create a document under `docs/updates/`.
- When the user requests a blog post, use SSH and WP-CLI to publish it.
- Blog posts should describe the operational purpose, not just the code changes.

## Product Priorities

- Protect the core CatchPro purpose: fast capture of useful orders and stable route execution.
- Do not add UI, logging, map, or sync work that slows the Insung order-confirmation loop.
- Keep Insung CatchPro and CatchPro Navi roles separate.
- Keep customer Free/Pro behavior explicit and consistent with license state.
- Never put Naver API secrets or other billable secrets directly into customer APKs.

## Default Deployment Targets

- Active customer APKs are usually:
  - `insungPro`
  - `naviPro`
  - `naviFree`
- `insungFree` is normally not installed unless the user specifically asks for it.
- Confirm the connected device with `adb devices` before installing.
