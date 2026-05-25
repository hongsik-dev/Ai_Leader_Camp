# CatchPro 라이선스 관리 CLI

`catchpro-license-admin.mjs`는 WordPress 관리자 화면을 열지 않고도 CatchPro Pro 구독을 확인하고 처리하기 위한 로컬 운영 스크립트다.

## 목적

- 신규 신청자에게 30일 체험을 빠르게 열어준다.
- 결제 확인 후 1개월 연장을 처리한다.
- 폰 2대 사용, 기기 변경, 잘못 등록된 기기 초기화를 처리한다.
- 만료, 차단, 등록 기기 상태를 SSH 없이 확인한다.
- 처리 내역을 Notion 운영 로그로 남길 수 있다.

## 환경변수

```powershell
$env:CATCHPRO_LICENSE_API_BASE="https://hongsik.blog"
$env:CATCHPRO_LICENSE_ADMIN_TOKEN="server-admin-token"
$env:NOTION_TOKEN="ntn_..."
$env:NOTION_DATABASE_ID="36bd426b-def3-810d-9a95-dbb02d1aaf7f"
```

`CATCHPRO_LICENSE_ADMIN_TOKEN`은 서버의 `CATCHPRO_LICENSE_ADMIN_TOKEN`과 같아야 한다. 이 값은 GitHub, Notion, 블로그에 기록하지 않는다.

## 공통 확인

실제 변경 전에는 `--dry-run`으로 먼저 확인한다.

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs trial --name "홍길동" --phone "01012345678" --memo "신규 신청 체험" --dry-run
```

dry-run 출력에는 고객명, 연락처, 기기 ID가 마스킹되어야 한다.

## 라이선스 목록 확인

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs list
```

7일 이내 만료 고객만 확인:

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs list --within-days 7
```

특정 상태만 확인:

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs list --status active
```

## 신규 30일 체험

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs trial --name "홍길동" --phone "01012345678" --memo "신규 신청 체험" --notion
```

- 상태: `trial`
- 기본 기간: 30일
- 기본 등록 가능 기기: 2대
- `--notion`을 붙이면 마스킹된 처리 기록이 Notion에 남는다.

## 1개월 연장

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs extend --phone "01012345678" --memo "5월 결제 확인" --notion
```

- 상태: `active`
- 현재 만료일이 미래면 그 날짜 기준으로 30일 연장한다.
- 이미 만료된 고객이면 오늘 기준으로 30일을 부여한다.

## 기기 초기화

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs reset-device --phone "01012345678" --memo "기기 변경 요청" --notion
```

- 등록된 Insung Pro / Navi Pro 기기값을 비운다.
- 고객이 앱에서 라이선스 확인을 다시 누르면 새 폰이 등록된다.
- 폰 2대 사용자는 기본적으로 최대 2대까지 등록된다.

## 만료 처리

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs expire --phone "01012345678" --memo "구독 종료" --notion
```

- 상태를 `expired`로 바꾼다.
- 만료일을 현재 시각으로 기록한다.
- 앱은 다음 라이선스 확인 시 Pro 기능을 비활성화한다.

## 차단

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs block --phone "01012345678" --memo "운영 중지" --notion
```

- 상태를 `blocked`로 바꾼다.
- 환불, 악용, 지원 중단처럼 즉시 차단이 필요한 경우에만 사용한다.

## 앱 라이선스 확인

고객이 입력한 연락처와 기기 ID로 앱이 받을 결과를 확인한다.

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs check --edition insung-pro --phone "01012345678" --device "R3CN607EZ0B"
```

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs check --edition navi-pro --phone "01012345678" --device "R3CN607EZ0B"
```

## 기기 수동 등록

일반적으로 앱의 라이선스 확인 과정에서 자동 등록된다. 수동 등록은 고객 지원 중 필요한 경우에만 사용한다.

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs register-device --edition navi-pro --phone "01012345678" --device "R3CN607EZ0B" --notion
```

## 운영 루틴

1. WordPress 신청 내역을 확인한다.
2. `trial --dry-run`으로 값이 맞는지 확인한다.
3. `trial --notion`으로 체험을 열고 기록한다.
4. 고객에게 설치 안내를 보낸다.
5. 고객이 두 폰에서 라이선스 확인을 누르게 한다.
6. `list --within-days 7`을 주 1회 확인한다.
7. 결제 확인 시 `extend --notion`으로 연장한다.
8. 기기 변경 요청은 `reset-device --notion`으로 처리한다.

## 주의

- 이 스크립트는 관리자 토큰이 없으면 쓰기 작업을 하지 않는다.
- `check`는 공개 앱 확인 API를 사용하므로 관리자 토큰 없이도 동작한다.
- 메모에는 API key, 결제정보, 카드번호, 전체 기기 ID를 넣지 않는다.
- 상세 고객 원본 정보는 WordPress 신청 내역과 라이선스 서버에만 둔다.
