# CatchPro Operations HQ

CatchPro 작업본부는 개발, 배포, 고객 신청, 라이선스, 상담, 블로그 기록을 하나의 흐름으로 묶는 운영 체계다.

## 목표

- 작업 시작 전 해야 할 일을 Notion에 등록한다.
- 구현은 GitHub branch와 PR로 추적한다.
- 서버, WordPress, 카카오 챗봇 변경은 작업 단위로 기록한다.
- 배포가 끝나면 Notion 체크, GitHub 링크, 블로그 링크를 남긴다.
- Insung 오더확정 속도에 영향을 줄 수 있는 작업은 항상 최우선으로 관리한다.

## 본부 구조

| 영역 | 역할 | 기준 도구 |
| --- | --- | --- |
| 작업본부 | 할 일, 우선순위, 상태, 완료 체크 | Notion |
| 코드본부 | 코드 변경, PR, release, tag | GitHub |
| 실행본부 | AWS sync, license, Naver proxy, Kakao skill | AWS |
| 고객창구 | Pro 신청, 안내 페이지, 개발 블로그 | WordPress |
| 상담창구 | FAQ 자동응답, 상담 연결 | Kakao Channel |

## P0 운영 흐름

1. Notion에 작업을 등록하고 우선순위를 정한다.
2. Git branch를 만든다.
3. 구현한다.
4. Android/AWS/WordPress 중 영향을 받는 영역을 테스트한다.
5. Git commit과 push를 남긴다.
6. PR 또는 merge 상태를 Notion에 기록한다.
7. 고객에게 설명이 필요한 변경은 WordPress 블로그로 남긴다.
8. Notion 작업을 완료 체크한다.

## 작업 분류

### P0

서비스 운영과 오더확정 속도에 직접 영향을 주는 작업이다.

- Insung 오더확정 경로
- 접근성 서비스 안정성
- 고객 Pro 라이선스 인증
- 서버 장애와 API key 보호
- 배포 파일/패키지 오류

### P1

고객 경험과 운영 효율에 큰 영향을 주는 작업이다.

- Navi 지도/방문순서
- WordPress 신청 페이지
- 카카오 챗봇 FAQ
- GitHub release 정리
- Notion 자동화

### P2

편의성, 문서, UI 개선 작업이다.

- 사용설명 문구 개선
- 블로그 정리
- 관리자 작업 단축
- 통계와 리포트 자동화

## 운영 규칙

- `catchpro/`를 기준 소스로 사용한다.
- root shadow copy는 수정하지 않는다.
- 민감정보는 GitHub와 Notion에 직접 저장하지 않는다.
- Free/Pro 권한 변경 시 사용설명과 신청 페이지 문구를 같이 점검한다.
- Insung hot path에는 지도, 네트워크, 무거운 DB 작업을 넣지 않는다.

## 당장 진행할 작업

- GitHub branch, PR, release 운영 규칙 고정
- Notion CLI 상세 페이지 기록 기능 강화
- AWS 라이선스/챗봇 변경 이력 Notion 연동
- WordPress 신청 접수 처리 흐름 Notion 연동
