# CatchPro Worktree Management

CatchPro 저장소의 정식 소스 위치는 repository root의 `catchpro/` 디렉터리다. 루트는 Git 저장소와 운영 자료의 입구이고, Android/AWS/문서 작업은 `catchpro/`를 기준으로 한다.

## 현재 구조

```text
C:\Users\misoh\TTJ\catchpro
  .git/
  .gitignore
  README.md
  catchpro/              # 정식 Android/AWS/문서 소스
  hongsik-log/           # WordPress 테마
  wp-content-seed/       # WordPress 초기 콘텐츠, Git에는 ignore
  .workspace-archive/    # 정리 전 보존한 로컬 산출물, Git에는 ignore
  analysis*/             # 로컬 분석 산출물
  tmp_*/                 # 임시 산출물
  *.db, *.preferences_pb # 휴대폰/앱 런타임 산출물
```

## 원칙

- 새 코드 수정은 항상 `catchpro/` 하위에서 한다.
- 루트의 `app/`, `docs/`, `gradle/`, `server/`는 shadow copy로 취급한다.
- shadow copy는 정식 소스와 차이가 없는지 감사한 뒤 보관 또는 삭제한다.
- DB, Gradle cache, APK build output, 휴대폰 preference, 분석 로그는 Git에 올리지 않는다.
- Notion, WordPress, 서버 토큰은 파일에 저장하지 않고 환경변수로만 사용한다.
- 작업자와 Codex는 repository root의 `AGENTS.md` 규칙을 따른다.

## 감사 명령

정식 소스와 루트 shadow copy 차이를 확인한다.

```powershell
.\catchpro\scripts\workspace\catchpro-worktree-audit.ps1
```

JSON으로 확인하려면:

```powershell
.\catchpro\scripts\workspace\catchpro-worktree-audit.ps1 -Json
```

## 정리 순서

1. `git status --short catchpro`로 정식 소스 변경만 확인한다.
2. `catchpro-worktree-audit.ps1`로 shadow copy 차이를 확인한다.
3. shadow copy에만 있는 변경이 실제로 필요한지 판단한다.
4. 필요한 변경은 `catchpro/` 하위 정식 소스로 옮긴다.
5. 확인이 끝난 shadow copy는 repository 밖 보관 폴더로 이동하거나 삭제한다.
6. `git status --short`가 정식 변경만 보여주는지 확인한다.

## 현재 정리 상태

2026-05-25에 루트 shadow copy를 `.workspace-archive/root-shadow-20260525-210807/`로 이동했다. 삭제가 아니라 보존 이동이므로 필요하면 manifest를 보고 복구할 수 있다.

## 주의

루트에 `app/`, `docs/`, `gradle/`, 루트 Gradle 파일이 다시 생기면 기준 소스로 보지 않는다. 먼저 감사 스크립트로 차이를 확인하고, 필요한 변경만 `catchpro/` 하위로 옮긴다.
