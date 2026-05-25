# CatchPro License Server Operations

## Server

The current production license API runs on the CatchPro sync server.

- Host: `43.201.95.165`
- Public domain: `https://hongsik.blog`
- Legacy host: `43.200.8.165` proxies the CatchPro API paths to `hongsik.blog` during migration.
- Node app: `/opt/catchpro-sync/server.js`
- License store: `/opt/catchpro-sync/licenses.json`
- Apache proxy: `/opt/bitnami/apache/conf/bitnami/catchpro-sync.conf`

## Public App Endpoints

```text
GET  /api/license/app-version
POST /api/license/check
```

The Android app calls `POST /api/license/check` at app start/settings/manual refresh only. Accessibility order confirmation logic reads only the local cache and must not call this API.

## Admin Endpoint

```text
POST /api/license/register-device
GET  /api/license/list
POST /api/license/upsert
```

This endpoint requires the `X-CatchPro-Admin-Token` header and the server-side `CATCHPRO_LICENSE_ADMIN_TOKEN` environment variable. If the token is not configured, the endpoint stays closed.

`register-device` binds an existing license to a device. `list` and `upsert` are for the WordPress admin license manager. They should only be called from an authenticated admin page or trusted maintenance script.

## Local Admin CLI

WordPress 관리자 화면을 열지 않고 처리할 때는 [CatchPro 라이선스 관리 CLI](./catchpro-license-admin-cli.md)를 사용한다.

```powershell
node catchpro/scripts/license/catchpro-license-admin.mjs list --within-days 7
node catchpro/scripts/license/catchpro-license-admin.mjs trial --name "홍길동" --phone "01012345678" --memo "신규 신청 체험" --notion
node catchpro/scripts/license/catchpro-license-admin.mjs extend --phone "01012345678" --memo "결제 확인" --notion
node catchpro/scripts/license/catchpro-license-admin.mjs reset-device --phone "01012345678" --memo "기기 변경" --notion
```

이 CLI는 고객명, 연락처, 기기값을 출력과 Notion 운영 로그에서 마스킹한다.

## WordPress Admin Flow

The CatchPro apply plugin can manage licenses without SSH editing.

1. Open WordPress admin.
2. Go to `CatchPro 신청 > 라이선스 관리`.
3. Configure `설정 > CatchPro 신청` first:
   - `라이선스 API 주소`: `https://hongsik.blog`
   - `라이선스 관리자 토큰`: same value as server `CATCHPRO_LICENSE_ADMIN_TOKEN`
4. Use quick registration or row actions. The managed product is a single `CatchPro Pro 구독`; it grants both Insung CatchPro Pro and CatchPro Navi Pro access, with up to two registered Android devices per subscription by default.
   - `라이선스 등록/갱신`
   - `1개월 연장`
   - `체험 30일`
   - `기기 초기화`
   - `만료 처리`
   - `차단`
5. From an individual `CatchPro 신청` post, use the `라이선스 등록` side box to register a 30-day trial directly.

The Android customer then opens the app settings and checks the license with the same email or phone number.

## License Store Example

```json
{
  "appVersions": {
    "catchpro-pro": {
      "minimumVersionCode": 0,
      "latestVersionName": ""
    },
    "insung-pro": {
      "minimumVersionCode": 0,
      "latestVersionName": ""
    },
    "navi-pro": {
      "minimumVersionCode": 0,
      "latestVersionName": ""
    }
  },
  "licenses": [
    {
      "id": "driver-001",
      "name": "Driver Name",
      "email": "driver@example.com",
      "phone": "01000000000",
      "edition": "catchpro-pro",
      "maxDevices": 2,
      "devices": [],
      "deviceIds": {
        "insung-pro": "",
        "navi-pro": ""
      },
      "allowDeviceBind": true,
      "status": "trial",
      "startedAt": "2026-05-24T00:00:00+09:00",
      "expiresAt": "2026-06-24T23:59:59+09:00",
      "memo": "manual trial"
    }
  ]
}
```

## Status Values

- `trial`: trial access
- `active`: paid active access
- `expired`: subscription ended
- `blocked`: blocked customer/device
- `device_change_pending`: waiting for manual device transfer
- `device_limit`: the subscription already has the maximum registered devices

## Admin Action Meaning

- `1개월 연장`: add 30 days. If the current expiry is in the future, it extends from that expiry. If already expired, it starts a new 30-day period from now. Use after paid renewal.
- `체험 30일`: set status to `trial` and grant 30 days. Use for first-month trials.
- `기기 초기화`: clear registered devices. Use when a customer changes phones, bound the wrong device, or needs to re-bind the two allowed phones.
- `만료 처리`: set status to `expired` and set expiry to now. Use when the subscription has ended or payment is not continued.
- `차단`: set status to `blocked`. Use for immediate stop cases such as abuse, refund handling, or support termination.

## Deployment Check

After editing the server:

```bash
node -c /opt/catchpro-sync/server.js
sudo /opt/bitnami/apache/bin/apachectl -t
sudo systemctl restart catchpro-sync
sudo /opt/bitnami/ctlscript.sh restart apache
curl http://127.0.0.1:3001/api/license/app-version
curl https://hongsik.blog/api/license/app-version
curl http://43.200.8.165/api/license/app-version
```

Expected response includes `ok: true`.
