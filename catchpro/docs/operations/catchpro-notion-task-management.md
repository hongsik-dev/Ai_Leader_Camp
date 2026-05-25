# CatchPro Notion Task Management

CatchPro 업데이트 과제는 Notion의 `CatchPro 작업 관리` 데이터베이스에서 관리한다.

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

## Commands

작업 등록:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs add --title "Navi Pro AWS 동기화 검증" --status "예정" --priority P1 --type Android --version Navi --memo "배포 전 실제 폰 기준으로 확인"
```

작업 상태 변경:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs update --title "Navi Pro AWS 동기화 검증" --status "진행중"
```

작업 완료:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs done --title "Navi Pro AWS 동기화 검증" --github-pr "https://github.com/hongsik-dev/Ai_Leader_Camp/pull/1" --blog-url "https://hongsik.blog/example/"
```

작업 목록 확인:

```powershell
node .\scripts\notion\catchpro-notion-tasks.mjs list --status "진행중"
```

## Operating Rule

- 작업 시작 전 Notion에 과제를 등록한다.
- 구현 중이면 상태를 `진행중`으로 바꾼다.
- 코드 리뷰나 사용자 확인이 필요하면 `검토중`으로 둔다.
- GitHub PR, 블로그 글, 배포가 끝나면 `완료`로 바꾸고 링크를 남긴다.
- Insung 오더확정 속도에 영향을 줄 수 있는 작업은 `P1` 이상으로 관리한다.
