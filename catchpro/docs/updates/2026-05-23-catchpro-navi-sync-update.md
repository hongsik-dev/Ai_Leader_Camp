# 2026-05-23 CatchPro 업데이트

## 목적

인성폰은 오더 확정 속도에 집중하고, CatchPro Navi는 지도와 주소 동기화에 집중하도록 역할을 분리한다.
오늘 변경은 오더 확정 루프를 건드리지 않고 Navi 화면과 AWS 주소 동기화 안정성을 개선하는 데 초점을 맞췄다.

## 주요 변경

- CatchPro Navi에서 완료 처리한 주소가 AWS의 오래된 스냅샷으로 다시 살아나는 문제를 차단했다.
- 주소 완료 시 지도 갱신이 중복 호출되던 흐름을 정리했다.
- 네이버 Geocoding과 Directions 결과를 캐시해서 같은 주소와 같은 구간의 반복 API 호출을 줄였다.
- 지도 상태가 갱신되면 행정동 거리 결과를 초기화해 오래된 방문순서가 남지 않도록 했다.
- 출발점 마커와 다음 방문지 점선 화살표 이미지를 재사용해서 지도 갱신 중 비트맵 재생성을 줄였다.

## 안정성 관점

- 인성폰 APK에는 네이버 지도 SDK가 포함되지 않도록 `naviImplementation` 구조를 유지했다.
- 오더 확정 접근성 루프 파일은 변경하지 않았다.
- 무거운 지도 계산은 Navi/TMAP 화면 ViewModel 영역에 묶어 두었다.

## 검증

```text
./gradlew.bat :app:assembleInsungDebug :app:assembleNaviDebug
./gradlew.bat :app:testInsungDebugUnitTest :app:testNaviDebugUnitTest
```

두 명령 모두 성공했다.
