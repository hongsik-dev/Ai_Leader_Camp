# CatchPro GitHub Release Process

CatchPro 작업은 GitHub branch와 PR을 기준으로 추적한다. 개인용, Free, Pro가 함께 움직이기 때문에 브랜치 이름과 완료 기록을 일정하게 유지해야 한다.

## 기본 원칙

- `main`은 기준 브랜치다.
- 새 작업은 `codex/` prefix branch에서 시작한다.
- 작업 단위는 작게 유지한다.
- Android, AWS, WordPress, Notion 작업은 가능하면 커밋을 분리한다.
- 고객 배포 파일을 만드는 작업은 Notion 작업과 GitHub PR을 반드시 연결한다.

## 브랜치 이름

| 작업 유형 | 예시 |
| --- | --- |
| Android 기능 | `codex/android-navi-route-cache` |
| Insung 속도/안정성 | `codex/insung-confirm-speed-guard` |
| AWS 서버 | `codex/aws-license-admin` |
| WordPress | `codex/wp-pro-apply-page` |
| 운영/문서 | `codex/operations-hq` |

## 작업 시작 순서

1. Notion에 작업을 등록한다.
2. 작업 상태를 `진행중`으로 바꾼다.
3. `main`을 최신으로 맞춘다.
4. `codex/...` 브랜치를 만든다.
5. 구현한다.

## 커밋 기준

커밋 메시지는 무엇을 바꿨는지 바로 알 수 있게 쓴다.

좋은 예:

```text
Separate Navi route calculation from marker refresh
Add license admin trial extension command
Document CatchPro operations HQ workflow
```

피해야 할 예:

```text
fix
update
misc
```

## 검증 기준

### Android

- 빌드 성공
- 대상 flavor 설치 성공
- 실제 폰에서 핵심 화면 실행 확인
- Insung hot path 변경이면 오더상세/오더확정 속도 영향 확인

### AWS

- `catchpro-health` 확인
- 관련 API curl 테스트
- systemd 상태 확인
- 로그에 secret/address가 노출되지 않는지 확인

### WordPress

- WP-CLI 실행 성공
- 페이지 URL 확인
- 신청/챗봇/블로그 링크 확인

## PR 기준

PR에는 아래를 남긴다.

- 변경 내용
- 확인한 내용
- 주의사항
- 관련 Notion 작업
- 필요하면 블로그 URL

## Merge 후

1. GitHub PR을 merge한다.
2. 로컬 `main`을 최신화한다.
3. Notion 작업에 PR 링크를 남긴다.
4. 배포가 끝났으면 `완료`와 `체크`를 true로 바꾼다.
5. 고객에게 설명이 필요한 변경이면 WordPress 블로그를 작성한다.

## 고객 배포 산출물

Free/Pro 고객 배포 파일은 일반 작업과 구분한다.

- Navi Free
- Navi Pro
- Insung Pro
- Personal/Internal

APK 파일 자체는 Git에 올리지 않는다. GitHub Release, 서버 다운로드 링크, 또는 별도 배포 저장소를 사용한다.
