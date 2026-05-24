# CatchPro License Server Operations

## Server

The current production license API runs on the CatchPro sync server.

- Host: `43.200.8.165`
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
```

This endpoint requires the `X-CatchPro-Admin-Token` header and the server-side `CATCHPRO_LICENSE_ADMIN_TOKEN` environment variable. If the token is not configured, the endpoint stays closed.

## License Store Example

```json
{
  "appVersions": {
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
      "edition": "insung-pro",
      "deviceId": "",
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

## Deployment Check

After editing the server:

```bash
node -c /opt/catchpro-sync/server.js
sudo /opt/bitnami/apache/bin/apachectl -t
sudo systemctl restart catchpro-sync
sudo /opt/bitnami/ctlscript.sh restart apache
curl http://127.0.0.1:3001/api/license/app-version
curl http://43.200.8.165/api/license/app-version
```

Expected response includes `ok: true`.
