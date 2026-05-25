# Ai_Leader_Camp

CatchPro Android 앱, AWS 동기화 서버, WordPress 운영 자료를 함께 관리하는 저장소입니다.

## 정식 소스 위치

- `catchpro/`: CatchPro Android 앱, 서버 코드, 운영 문서의 기준 위치
- `hongsik-log/`: hongsik.blog WordPress 테마
- `wp-content-seed/`: WordPress 초기 콘텐츠 자료

새로운 CatchPro 코드 수정은 항상 `catchpro/` 하위에서 진행합니다. 루트에 생긴 `app/`, `docs/`, `gradle/` 같은 복사본은 기준 소스가 아니므로 작업 전에 감사해야 합니다.

## 작업트리 관리

- 기준 문서: `catchpro/docs/operations/catchpro-worktree-management.md`
- 감사 스크립트: `catchpro/scripts/workspace/catchpro-worktree-audit.ps1`
- 로컬 APK, DB, 로그, 스크린샷, Gradle cache, 휴대폰 추출 파일은 Git에 올리지 않습니다.
- 토큰, API key, pem 파일은 저장소에 저장하지 않습니다.

## 운영 메모

WordPress 글에는 실제로 사용한 태그만 붙입니다. 빈 태그는 화면에 노출하지 않습니다.
CatchPro 개발 기록과 배포 기록은 GitHub, Notion, WordPress를 함께 사용해 추적합니다.
