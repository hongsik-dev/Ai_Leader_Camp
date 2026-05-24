# CatchPro 홈페이지 신청 페이지 운영

작성일: 2026-05-24

## 공개 페이지

- URL: `https://hongsik.blog/catchpro-pro-apply/`
- WordPress 페이지 제목: `CatchPro Pro 신청`
- 페이지 본문은 `[catchpro_apply_form]` 쇼트코드로 렌더링한다.

## 신청 데이터 저장 위치

신청서가 접수되면 WordPress 관리자 메뉴의 `CatchPro 신청` 커스텀 글 타입에 `private` 상태로 저장된다.

저장 항목:

- 이름
- 연락처
- 이메일
- 사용 기기
- 희망 버전: `CatchPro`
- 주 운행 지역
- 문의 내용

테스트 신청은 접수 확인 후 즉시 삭제했다.

## 카카오톡 상담 URL 설정

카카오 채널 또는 오픈채팅 URL을 아래 옵션에 저장하면 신청 페이지의 카카오톡 상담 버튼이 활성화된다.

```bash
sudo /opt/bitnami/wp-cli/bin/wp --path=/opt/bitnami/wordpress option update catchpro_kakao_url 'https://pf.kakao.com/.../chat'
```

또는 WordPress 관리자에서 `설정 > CatchPro 신청` 메뉴로 들어가 URL을 입력한다.

지원하는 URL 예:

- 카카오 채널 채팅 URL
- 카카오 오픈채팅 URL

## 배포 파일

- WordPress 플러그인 원본: `catchpro/server/wordpress/catchpro-apply-form.php`
- 페이지 원본: `catchpro/server/wordpress/catchpro-pro-apply-page.html`

서버 배포 위치:

- `/opt/bitnami/wordpress/wp-content/plugins/catchpro-apply-form/catchpro-apply-form.php`

## 운영 메모

초기 운영은 신청서 접수 후 수동 상담, 수동 결제, 수동 라이선스 승인 방식으로 진행한다.
자동결제와 관리자 페이지 고도화는 라이선스 서버 운영이 안정화된 뒤 붙인다.
