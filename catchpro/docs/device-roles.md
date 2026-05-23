# CatchPro Device Roles

이 문서는 휴대폰 연결 시 어떤 APK를 설치해야 하는지 빠르게 판단하기 위한 기준이다.

개인 운행에 쓰는 APK는 계속 `insung` / `navi` flavor를 사용한다. Free/Pro 배포판은 별도 패키지로 빌드하며, 개인 운행판과 섞지 않는다.

| ADB serial | 역할 | 설치 APK | 메모 |
| --- | --- | --- | --- |
| R3CN607EZ0B | CatchPro Navi | `app/build/outputs/apk/navi/debug/app-navi-debug.apk` | 지도/네비 전용폰. 네이버 지도, AWS 주소 동기화, 행정동 음성입력 사용. |
| R3CM705EWKZ | CatchPro Insung | `app/build/outputs/apk/insung/debug/app-insung-debug.apk` | 인성데이터 오더리스트/자동상세확정 전용폰. |

기기 역할이 바뀌면 이 파일을 먼저 수정한 뒤 커밋한다.

배포판 구조와 빌드 명령은 [editions.md](editions.md)를 기준으로 관리한다.
