# API Reference

Base path: `/api/v1`

All authenticated endpoints require a **Firebase ID token** in the `Authorization: Bearer <token>` header. The client obtains tokens via the Firebase JS SDK (`user.getIdToken()`).

Reactive endpoints return JSON; internally they are implemented as `Uni<T>` on the server.

## Auth

Sign-in and registration are handled **client-side** by Firebase Authentication. The backend only verifies tokens and syncs app user records.

### `POST /auth/sync`

Verify Firebase ID token, upsert app user by `firebase_uid`, return profile. Call after sign-in or on app load.

**Headers:** `Authorization: Bearer <firebase-id-token>`

**Response:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "firebaseUid": "abc123firebase",
  "email": "user@example.com",
  "displayName": "Dennis"
}
```

### `GET /auth/me`

Returns the current app user linked to the verified Firebase token.

**Headers:** `Authorization: Bearer <firebase-id-token>`

**Errors:**

- `401` — missing, expired, or invalid Firebase token

---

## Favorites

Firebase ID token required.

### `GET /favorites`

List all favorites for the current user, ordered by `sort_order`.

### `POST /favorites`

Create a favorite. **Route is required** for `KMB`, `CTB`, `NLB`, `GMB`, `LRT`, and `MTR_BUS`.

**Request body:**

```json
{
  "transportType": "KMB",
  "label": "Morning commute — 720",
  "config": {
    "stopId": "0B150F9A4BFF8F5F",
    "route": "720",
    "serviceType": "1"
  }
}
```

**Errors:**

- `400` — missing required route fields for bus/GMB type
- `409` — duplicate stop+route for this user

### `PUT /favorites/{id}`

Update `label` and/or `sort_order` only.

### `DELETE /favorites/{id}`

Remove a favorite.

### `PUT /favorites/reorder`

Bulk update sort order.

**Request body:**

```json
{
  "order": [
    { "id": "uuid-1", "sortOrder": 0 },
    { "id": "uuid-2", "sortOrder": 1 }
  ]
}
```

---

## ETA

Firebase ID token required.

### `GET /eta/favorites`

Primary dashboard endpoint. Fetches ETAs for all user favorites in parallel (`Uni.combine().all()`), returns a normalized list.

**Response:**

```json
[
  {
    "favoriteId": "550e8400-e29b-41d4-a716-446655440000",
    "transportType": "KMB",
    "label": "720 @ Admiralty",
    "route": "720",
    "stopName": "Admiralty Station",
    "fetchedAt": "2026-06-30T10:00:00+08:00",
    "status": "ok",
    "entries": [
      {
        "route": "720",
        "destination": "Central (Ferry Pier)",
        "etaMinutes": 3,
        "etaTime": "2026-06-30T10:03:00+08:00",
        "platform": null,
        "remarks": ""
      }
    ]
  }
]
```

**Status values:** `ok`, `error`, `stale`

Bus/GMB entries show up to 3 arrivals for the **saved route only**. MTR entries include `platform` and are filtered by saved `direction`/`platform` when set.

### `GET /eta/preview`

Live ETA preview before saving a favorite.

**Query parameters:**

| Param | Required | Description |
|-------|----------|-------------|
| `transportType` | Yes | `KMB`, `CTB`, `NLB`, `GMB`, `MTR`, `LRT`, `MTR_BUS` |
| `stopId` | Yes* | Stop ID (*not for MTR/LRT) |
| `route` | Yes* | Route number (*KMB/CTB) |
| `routeId` | Yes* | Route ID (*NLB/GMB) |
| `stationId` | LRT only | LRT station ID (integer) |
| `routeNo` | LRT only | LRT route number |
| `routeName` | MTR_BUS only | MTR Bus route variant string |
| `busStopId` | MTR_BUS only | MTR Bus stop ID |
| `serviceType` | KMB only | Service type |
| `line` | MTR only | Line code |
| `station` | MTR only | Station code |

---

## Search

Firebase ID token required. Responses are cached (24h TTL for static metadata).

### `GET /search/stops`

Search stops or stations by name.

**Query parameters:** `q`, `type` (`KMB` | `CTB` | `GMB` | `MTR` | `LRT` | `MTR_BUS`)

### `GET /search/routes-at-stop`

**Routes serving a given stop** — required for bus/GMB/MTR Bus add-favorite route picker.

**Query parameters:** `stopId`, `type` (`KMB` | `CTB` | `GMB` | `MTR_BUS`)

### `GET /search/routes-at-station`

**Routes at a Light Rail station** — required for LRT add-favorite route picker.

**Query parameters:** `stationId`, `withSpecial` (optional, `0` | `1`)

**Response example:**

```json
[
  { "routeNo": "614", "destination": "Tuen Mun Ferry Pier", "platformId": 1 },
  { "routeNo": "615", "destination": "Siu Hong", "platformId": 2 }
]
```

### `GET /search/routes`

Search routes by number (alternative flow: pick route first, then stop).

**Query parameters:** `q`, `type` (`KMB` | `CTB` | `GMB` | `MTR_BUS`)

### `GET /meta/mtr/lines`

MTR heavy rail line list with station codes.

### `GET /meta/lrt/stations`

LRT station list (`stationId`, name EN/zh).

### `GET /meta/mtr-bus/routes`

MTR Bus route variants — `routeName` strings with destination descriptions for the route picker.

---

## Advertisements

### `GET /ads/active`

Public. Returns active ads for a placement.

**Query parameters:** `placement` — `dashboard_top` | `dashboard_inline` | `dashboard_bottom`

**Response:**

```json
[
  {
    "id": "uuid",
    "placement": "dashboard_top",
    "title": "Summer promo",
    "imageUrl": "/static/ads/summer.png",
    "targetUrl": "https://example.com"
  }
]
```

### `POST /ads/{id}/impression`

Fire-and-forget impression counter. No auth. Rate-limited by IP.

**Response:** `204 No Content`

### Admin endpoints (Firebase custom claim `admin: true` required)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/admin/ads` | Create ad slot |
| `PUT` | `/admin/ads/{id}` | Update ad slot |
| `DELETE` | `/admin/ads/{id}` | Delete ad slot |

---

## Health

Quarkus built-in: `GET /q/health`

Checks: PostgreSQL connectivity, optional upstream API reachability.
