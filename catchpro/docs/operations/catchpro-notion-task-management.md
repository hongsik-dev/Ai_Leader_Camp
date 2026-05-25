# CatchPro Notion Task Management

CatchPro 업데이트 과제는 Notion의 `CatchPro 작업 관리` 데이터베이스에서 관리한다.
기본 운영 화면은 Notion 모바일이며, CLI는 보조 도구다.

## Notion Database

- Parent page: `CatchPro`
- Database name: `CatchPro 작업 관리`
- Database ID: `36bd426b-def3-810d-9a95-dbb02d1aaf7f`

## Required Environment Variables

Notion secret은 GitHub에 저장하지 않는다. 로컬 터미널 또는 서버 환경변수로만 설정한다.

```powershell
$env:NOTION_TOKEN = "ntn_..."
$env:NOTION_DATABASE_ID = "36bd426b-def3-810d-9a95-dbb02d1aaf7f"
```

운영 전 토큰이 노출됐거나 공유됐다면 Notion Integration에서 토큰을 재발급한다.

## Mobile First Rule

- 작업 등록, 우선순위 변경, 완료 체크는 Notion 모바일에서 먼저 처리한다.
- 라이선스 운영은 [Notion 모바일 라이선스 처리](./catchpro-notion-license-mobile-workflow.md)를 기준으로 한다.
- PowerShell CLI는 대량 등록, 장애 대응, dry-run 검증 때만 사용한다.

## Backup CLI Commands

아래 명령은 PC에서 빠르게 보정할 때 쓰는 보조 명령이다. 일상 운영의 기준은 Notion 모바일이다.

작업 등록:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs add --title "Navi Pro AWS 동기화 검증" --status "예정" --priority P1 --type Android --version Navi --sort-order 20 --status-order 2 --memo "배포 전 실제 폰 기준으로 확인"
```

작업 상태 변경:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs update --title "Navi Pro AWS 동기화 검증" --status "진행중" --status-order 1
```

작업 완료:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs done --title "Navi Pro AWS 동기화 검증" --github-pr "https://github.com/hongsik-dev/Ai_Leader_Camp/pull/1" --blog-url "https://hongsik.blog/example/"
```

작업 상세 페이지에 내용 추가:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs append --title "CatchPro 작업본부 구축 프로젝트" --heading "진행 내용" --bullets "GitHub 운영 규칙 문서화|Notion CLI 상세 기록 지원"
```

완료 체크만 바꿀 때:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs update --title "Navi Pro AWS 동기화 검증" --checked true
```

작업 목록 확인:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs list --status "진행중"
```

운영 처리 기록:

```powershell
node .\scripts\notion\catchpro-ops-log.mjs license --action trial --customer "홍길동" --contact "01012345678" --device "R3CN607EZ0B" --memo "30일 체험 시작" --dry-run
```

`catchpro-ops-log.mjs`는 고객명, 연락처, 이메일, 기기값을 마스킹해서 기록한다. 라이선스, WordPress 신청, 카카오 챗봇, AWS 서버, 블로그, APK 배포 작업은 이 스크립트로 운영 이력을 남긴다.

## Operating Rule

- 작업 시작 전 Notion에 과제를 등록한다.
- 구현 중이면 상태를 `진행중`으로 바꾼다.
- 코드 리뷰나 사용자 확인이 필요하면 `검토중`으로 둔다.
- GitHub PR, 블로그 글, 배포가 끝나면 `완료`로 바꾸고 링크를 남긴다.
- Insung 오더확정 속도에 영향을 줄 수 있는 작업은 `P1` 이상으로 관리한다.
- 목록 첫 화면은 `이름`, `체크`, `우선순위` 중심으로 보고, 자세한 내용은 각 작업 하위 페이지에 적는다.
- `정렬순서`는 낮을수록 위에 보이게 둔다. P0 작업은 보통 1~99 범위를 사용한다.
