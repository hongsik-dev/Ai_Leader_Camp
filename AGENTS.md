# CatchPro Workspace Rules

## Source Of Truth

- The canonical CatchPro project is `catchpro/`.
- Do not edit root-level shadow copies such as `app/`, `docs/`, `gradle/`, `server/`, or root Gradle files if they reappear.
- Before archiving or deleting any shadow copy, run `catchpro/scripts/workspace/catchpro-worktree-audit.ps1` from the repository root.

## Git Hygiene

- Keep runtime files out of Git: APK outputs, device databases, preferences, screenshots, Gradle caches, diagnostics, and temporary logs.
- Keep secrets out of Git: Notion tokens, Naver API secrets, license admin tokens, SSH keys, `.env` files, and server config dumps.
- Use focused commits. Do not mix Android feature work, server migration, blog content, and workspace cleanup in one commit unless the task explicitly requires it.

## CatchPro Safety

- Insung CatchPro changes must not slow the order detail entry or order confirmation path.
- Navi map, routing, and Redis work must stay isolated from the Insung accessibility hot path.
- For customer-facing Free/Pro behavior, update the user guide and license-gating notes with the code change.

## Operating Notes

- WordPress content lives on the AWS/Bitnami server and should be updated through WP-CLI when requested.
- Notion task automation should use environment variables for tokens and database IDs.
- When in doubt, preserve local artifacts in `.workspace-archive/` instead of deleting them.
