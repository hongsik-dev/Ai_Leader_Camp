# CatchPro 운영 로그 Notion 기록

CatchPro 운영 로그는 고객 신청, 라이선스 처리, 카카오 챗봇, WordPress 글 발행, AWS 서버 변경, APK 배포 같은 운영 이벤트를 Notion 작업본부에 남기기 위한 절차다.

## 목적

- 고객 문의와 처리 결과를 같은 기준으로 추적한다.
- 라이선스 등록, 체험 시작, 1개월 연장, 기기 초기화, 차단 같은 작업을 빠르게 확인한다.
- 서버/챗봇/WordPress 변경 후 무엇을 바꿨는지 Notion에서 바로 볼 수 있게 한다.
- 연락처, 이메일, 기기 ID, 토큰 같은 민감정보는 Notion과 GitHub에 직접 남기지 않는다.

## 기본 원칙

- 원본 고객 정보는 WordPress 신청 내역과 라이선스 서버에만 둔다.
- Notion에는 마스킹된 고객명, 연락처, 기기값만 기록한다.
- 토큰, API key, 비밀번호, Notion token은 메모에 입력하지 않는다.
- 서버 변경은 배포 전후 health check 결과를 같이 남긴다.
- APK 배포는 빌드 flavor, 설치 대상, 테스트 결과를 같이 남긴다.

## 환경변수

```powershell
$env:NOTION_TOKEN="ntn_..."
$env:NOTION_DATABASE_ID="36bd426b-def3-810d-9a95-dbb02d1aaf7f"
```

`.env` 파일을 만들 경우 GitHub에 올리지 않는다. 루트 `.gitignore`에서 `.env`와 `.env.*`는 제외되어 있다.

## Dry Run

실제 Notion에 쓰기 전에는 항상 `--dry-run`으로 확인한다.

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs license --action trial --customer "홍길동" --contact "01012345678" --device "R3CN607EZ0B" --memo "30일 체험 시작" --dry-run
```

출력에서 연락처와 기기값이 마스킹되어 있으면 실제 등록해도 된다.

## 라이선스 처리 기록

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs license --action trial --customer "홍길동" --contact "01012345678" --device "R3CN607EZ0B" --memo "30일 체험 시작"
```

권장 action:

- `trial`: 체험 30일 시작
- `extend-1m`: 1개월 연장
- `device-reset`: 등록 기기 초기화
- `expire`: 만료 처리
- `block`: 차단
- `unblock`: 차단 해제

## WordPress 신청 처리 기록

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs wordpress --action application-received --customer "홍길동" --application-id "1234" --memo "신청 접수 확인"
```

권장 action:

- `application-received`: 신청 접수 확인
- `application-reviewed`: 신청 검토 완료
- `license-created`: 신청에서 라이선스 등록
- `guide-sent`: 설치 안내 발송

## 카카오 챗봇 기록

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs chatbot --action faq-update --memo "요금/체험 답변 문구 수정"
```

권장 action:

- `faq-update`: FAQ 문구 수정
- `skill-test`: 스킬 테스트 완료
- `channel-link-check`: 채널 연결 확인
- `fallback-review`: 폴백 답변 개선

## AWS 서버 변경 기록

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs server --action deploy --memo "catchpro-sync 재시작, /health 정상"
```

권장 action:

- `deploy`: 서버 배포
- `restart`: 서비스 재시작
- `health-check`: API/WebSocket health 확인
- `proxy-change`: Apache proxy 변경
- `naver-proxy-check`: Naver proxy 확인

## 블로그/WP CLI 기록

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs blog --action published --title "CatchPro 고도화 12" --url "https://hongsik.blog/..."
```

블로그 글은 고객 안내가 필요한 변경, 배포 정책, 기능 분리, 운영 정책이 바뀐 경우 남긴다.

## APK/릴리즈 기록

```powershell
node catchpro/scripts/notion/catchpro-ops-log.mjs release --action apk-installed --version "navi-pro" --memo "R3CN607EZ0B 설치 및 실행 확인"
```

권장 action:

- `apk-built`: APK 빌드 완료
- `apk-installed`: 휴대폰 설치 완료
- `github-pushed`: GitHub push 완료
- `pr-merged`: PR merge 완료

## 운영 체크리스트

1. 작업 전 Notion에 작업이 있는지 확인한다.
2. 처리 후 `catchpro-ops-log.mjs`로 운영 이벤트를 남긴다.
3. 민감정보가 메모에 들어가지 않았는지 확인한다.
4. GitHub 변경이 있으면 PR 또는 commit 링크를 작업에 붙인다.
5. 고객 안내가 필요하면 WordPress 블로그 링크를 작업에 붙인다.
