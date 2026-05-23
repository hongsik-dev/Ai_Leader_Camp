# CatchPro Edition Plan

CatchPro는 개인 운행판과 배포판을 같은 코드베이스에서 다른 APK로 빌드한다.

## Edition Matrix

| Gradle flavor | 공식 이름 | 패키지 | 용도 |
| --- | --- | --- | --- |
| `insung` | 인성 CatchPro 개인 운행판 | `com.catchpro.app` | 사용자가 실제 운행에 쓰는 기존 버전. 자동확정, 자동상세확정, AWS 주소 동기화 유지. |
| `navi` | CatchPro Navi 개인 운행판 | `com.catchpro.app` | 사용자가 실제 운행에 쓰는 지도/네비 전용 버전. AWS 주소 동기화, 지도, 행정동 거리 확인 유지. |
| `insungFree` | 인성 CatchPro Free | `com.catchpro.insung.free` | 무료 배포판. 오더 조건 저장과 기본 주소/네비 기능만 제공. |
| `insungPro` | 인성 CatchPro Pro | `com.catchpro.insung.pro` | 유료 배포판. 사용자가 직접 연 인성 상세화면에서 조건이 맞으면 자동확정한다. |
| `naviFree` | CatchPro Navi Free | `com.catchpro.navi.free` | 무료 지도/네비 배포판. 기본 지도 확인용. |
| `naviPro` | CatchPro Navi Pro | `com.catchpro.navi.pro` | 유료 지도/네비 배포판. AWS 주소 동기화, 방문순서, 행정동 거리 확인을 제공한다. |

## Version Separation

고객 배포판은 개인 운행판과 설치 패키지뿐 아니라 Android `versionCode` 대역도 분리한다.

| Gradle flavor | versionCode 대역 | versionName 예시 |
| --- | ---: | --- |
| `insung` | `12` | `0.1.11` |
| `navi` | `50012` | `0.1.11-navi` |
| `insungFree` | `10012` | `0.1.11-insung-free` |
| `insungPro` | `20012` | `0.1.11-insung-pro` |
| `naviFree` | `30012` | `0.1.11-navi-free` |
| `naviPro` | `40012` | `0.1.11-navi-pro` |

새 버전을 배포할 때는 `app/build.gradle.kts`의 `catchProVersionCode`, `catchProVersionName`을 올리고, 각 배포판은 위 대역 규칙으로 자동 계산되게 둔다.

## Feature Rules

- `자동확정`: `insung`, `insungPro`에서만 켠다.
- `자동상세확정`: 안정성 검증 중인 개인 운행판 기능이므로 `insung`에서만 켠다. Free/Pro 배포판에는 포함하지 않는다.
- `오더추적`: 개인 운행판 내부 분석용으로만 남기고 Free/Pro에서는 설정값이 켜지지 않게 막는다.
- `AWS 주소 동기화`: 개인 운행판과 Pro에서만 켠다.
- `네비 최적화/행정동 거리 확인`: CatchPro Navi 개인 운행판과 Navi Pro에서만 켠다.

## Build Commands

```powershell
.\gradlew.bat :app:assembleInsungDebug
.\gradlew.bat :app:assembleNaviDebug
.\gradlew.bat :app:assembleInsungFreeDebug
.\gradlew.bat :app:assembleInsungProDebug
.\gradlew.bat :app:assembleNaviFreeDebug
.\gradlew.bat :app:assembleNaviProDebug
```

고객에게 전달할 파일은 아래 태스크로 별도 폴더에 모은다.

```powershell
.\gradlew.bat :app:collectCustomerDebugApks
.\gradlew.bat :app:collectCustomerReleaseApks
```

출력 위치:

- Debug: `app/build/customer-apks/debug`
- Release: `app/build/customer-apks/release`

파일명은 `CatchPro-Insung-Free-v0.1.11-debug.apk`처럼 고객 배포판 이름과 버전이 드러나도록 고정한다.

## Naver Maps Package Registration

Naver Dynamic Map을 사용하는 배포판은 네이버 Cloud Console Android 앱 패키지 이름에 아래 값을 추가해야 한다.

- `com.catchpro.navi.free`
- `com.catchpro.navi.pro`

개인 운행판은 기존 `com.catchpro.app` 등록을 그대로 사용한다.

## Store Monetization Note

현재 Pro 분리는 컴파일 타임 APK 분리 구조다. Google Play 결제, 구독, 서버 라이선스 검증은 아직 붙이지 않았다. 배포 전에는 Play Billing 또는 서버 라이선스 검증을 추가해서 Free APK에서 Pro 기능이 열리지 않도록 관리해야 한다.
