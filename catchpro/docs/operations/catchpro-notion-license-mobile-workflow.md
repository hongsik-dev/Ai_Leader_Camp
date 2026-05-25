# CatchPro Notion Mobile License Workflow

CatchPro 라이선스 운영의 기본 화면은 Notion 모바일이다. PowerShell CLI는 비상 처리와 일괄 처리용으로만 둔다.

## Goal

핸드폰 Notion에서 고객 행을 열고 `요청작업`을 고른 뒤 `실행`만 체크하면 AWS 서버가 라이선스를 처리한다.

```text
Notion 모바일
  -> 실행 체크
  -> Notion webhook
  -> AWS /api/notion/license-command
  -> licenses.json 업데이트
  -> Notion 처리상태/처리결과 자동 기록
```

## Server Endpoint

```text
POST https://hongsik.blog/api/notion/license-command
GET  https://hongsik.blog/api/notion/license-command
```

`GET`은 설정 확인용이다. `POST`는 Notion webhook 또는 Notion Automation webhook이 호출한다.

Apache 프록시에는 `/api/notion/` 경로가 Node sync 서버로 전달되도록 추가되어야 한다.

## Required Server Environment

서버 환경변수에만 저장한다. GitHub, Notion 페이지 본문, WordPress 글에는 저장하지 않는다.

```bash
NOTION_TOKEN=ntn_...
NOTION_LICENSE_DATABASE_ID=...
NOTION_WEBHOOK_VERIFICATION_TOKEN=secret_...
```

Notion Automation의 일반 webhook으로 보낼 때는 공유 토큰 방식을 쓸 수 있다.

```bash
CATCHPRO_NOTION_WEBHOOK_SECRET=...
```

이 경우 요청 헤더에 아래 값을 넣는다.

```text
X-CatchPro-Notion-Token: <CATCHPRO_NOTION_WEBHOOK_SECRET>
```

## Notion Database Schema

라이선스 처리용 데이터베이스에는 아래 속성을 둔다.

| 속성 | 타입 | 용도 |
| --- | --- | --- |
| 고객명 | Title | 고객 이름 또는 식별명 |
| 연락처 | Phone 또는 Text | 라이선스 조회 키 |
| 이메일 | Email 또는 Text | 라이선스 조회 키 |
| 요청작업 | Select | 처리할 작업 |
| 처리상태 | Status 또는 Select | 대기, 처리중, 완료, 실패 |
| 실행 | Checkbox | 체크하면 서버가 처리 |
| 처리결과 | Text | 서버 처리 결과 |
| 만료일 | Date | 구독 만료일 |
| 처리일시 | Date | 마지막 처리 시각 |
| 최대기기수 | Number | 기본 2대, 필요 시 조정 |
| 기기ID | Text | 기기 수동등록 때 사용 |
| 등록앱 | Select | 기기 수동등록 대상: 인성 또는 Navi |
| 메모 | Text | 운영 메모 |

## Request Actions

`요청작업`은 아래 이름으로 만든다.

| 요청작업 | 서버 처리 |
| --- | --- |
| 30일 체험 | `trial`, 만료일 30일 연장 |
| 1개월 연장 | `active`, 만료일 30일 연장 |
| 기기 초기화 | 등록 기기 목록 초기화, 새 기기 바인딩 허용 |
| 만료 처리 | `expired` 상태로 변경 |
| 차단 | `blocked` 상태로 변경 |
| 기기 수동등록 | `기기ID`를 인성 또는 Navi 슬롯에 등록 |

## Phone Operation

1. Notion 모바일에서 고객 행을 연다.
2. `요청작업`을 선택한다.
3. `처리상태`를 `대기`로 둔다.
4. 연락처 또는 이메일이 들어 있는지 확인한다.
5. `실행`을 체크한다.
6. 잠시 후 `처리상태`가 `완료` 또는 `실패`로 바뀌는지 확인한다.
7. 실패하면 하위 페이지에 기록된 실패 문구를 보고 수정 후 다시 실행한다.

## Safety Rules

- `실행` 체크가 없으면 서버는 아무 작업도 하지 않는다.
- `처리상태`가 `대기`가 아니면 서버는 건너뛴다.
- 연락처와 이메일이 모두 없으면 실패 처리한다.
- 고객명, 연락처, 기기ID 원문은 서버 로그에 남기지 않는다.
- 같은 webhook 이벤트는 짧은 시간 안에 중복 처리하지 않는다.
- Notion 처리 결과 업데이트가 실패해도 라이선스 파일 원문은 Git에 저장하지 않는다.

## Verification

서버 배포 후 아래 순서로 확인한다.

1. `GET /api/notion/license-command`가 `configured: true`를 반환하는지 확인한다.
2. Notion webhook 구독 생성 시 서버 로그에서 `NOTION_WEBHOOK_VERIFICATION` 토큰을 확인한다.
3. 해당 토큰을 Notion webhook 검증 화면에 입력한다.
4. 테스트 고객 행에서 `30일 체험`과 `실행`을 체크한다.
5. Notion 행이 `완료`로 바뀌고 `만료일`이 들어가는지 확인한다.
6. 앱에서 같은 연락처로 라이선스 확인이 되는지 확인한다.

## CLI Role

PowerShell CLI는 계속 남겨 둔다. 다만 역할은 아래로 제한한다.

- Notion webhook 장애 때 임시 처리
- 여러 고객 일괄 점검
- 서버 배포 전 dry-run 검증
- 라이선스 API 직접 진단

일상 운영은 Notion 모바일을 기준으로 한다.
