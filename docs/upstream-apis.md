# Upstream APIs

PT Dashboard proxies Hong Kong government open-data ETA APIs. All are **free and unauthenticated**. None support user favorites — bookmarks are stored in our PostgreSQL database.

## Coverage summary

| Mode | Operators | Base URL | In scope |
|------|-----------|----------|----------|
| Franchised bus | KMB/LWB | `https://data.etabus.gov.hk/v1/transport/kmb` | Yes |
| Franchised bus | Citybus (CTB) | `https://rt.data.gov.hk/v2/transport/citybus` | Yes |
| Franchised bus | NLB | `https://rt.data.gov.hk/v1/transport/nlb` | Yes |
| Green minibus | GMB | `https://data.etagmb.gov.hk` | Yes |
| MTR heavy rail | MTR | `https://rt.data.gov.hk/v1/transport/mtr` | Yes |
| Red minibus | — | — | **No** (no open API) |

## Per-route ETA endpoints (used by this app)

We fetch **per-route** ETAs, not all routes at a stop.

| Client | Method | Endpoint |
|--------|--------|----------|
| `KmbClient` | GET | `/eta/{stopId}/{route}/{serviceType}` |
| `CitybusClient` | GET | `/eta/CTB/{stopId}/{route}` |
| `NlbClient` | POST | `/stop.php?action=estimatedArrivals` body: `{ routeId, stopId }` |
| `GmbClient` | GET | `/eta/route-stop/{routeId}/{stopId}` |
| `MtrClient` | GET | `/getSchedule.php?line={LINE}&sta={STA}&lang=EN` |

### Example URLs

```
# KMB — route 720 at a stop
https://data.etabus.gov.hk/v1/transport/kmb/eta/{stopId}/720/1

# Citybus — route 720 at stop 001313
https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001313/720

# GMB — route at stop
https://data.etagmb.gov.hk/eta/route-stop/{routeId}/{stopId}

# MTR — TKL at Tseung Kwan O
https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=TKL&sta=TKO&lang=EN
```

## Discovery / static data endpoints (cached 24h)

Used by search and the add-favorite flow.

| Client | Endpoint | Purpose |
|--------|----------|---------|
| KMB | `/stop`, `/route`, `/route-stop/{route}/{dir}/{serviceType}` | Stop and route search |
| Citybus | `/stop`, `/route`, `/route-stop/{route}/{direction}` | Stop and route search |
| GMB | `/stop`, `/route`, `/stop-route/{stopId}` | Routes at stop (for route picker) |
| MTR | Static metadata in app | Line/station picker |

### Routes-at-stop

Critical for the mandatory route picker step:

- **GMB:** `GET /stop-route/{stopId}`
- **KMB:** derive from `/route-stop` data or stop-level route list
- **Citybus:** `GET /route-stop/{route}/{direction}` combined with stop lookup

## MTR lines and stations

| Line code | Line name |
|-----------|-----------|
| `AEL` | Airport Express |
| `TCL` | Tung Chung Line |
| `TML` | Tuen Ma Line |
| `TKL` | Tseung Kwan O Line |
| `EAL` | East Rail Line |
| `SIL` | South Island Line |
| `TWL` | Tsuen Wan Line |
| `ISL` | Island Line |
| `KTL` | Kwun Tong Line |
| `DRL` | Disneyland Resort Line |

Station codes are 3 letters (e.g. `CEN`, `ADM`, `TKO`).

**Response shape:** `{ UP: [...], DOWN: [...] }` — each train has `ttnt` (minutes), `plat` (platform), `dest`, `time`.

## Refresh rates and rate limits

| API | Data refresh | Documented rate limit |
|-----|--------------|----------------------|
| KMB | ~60s | None published |
| Citybus | ~60s | None published |
| NLB | Real-time | None published |
| GMB | ~60s | None published |
| MTR | ~10s | HTTP 429 on overload |
| Batch (CTB/NLB) | ~60s | HTTP 429 on overload |

**Our policy:** cache bus/GMB at 60s, MTR at 10s. Never poll faster than upstream refresh rates.

## Response normalization

Each operator returns different field names. `EtaNormalizer` services map them to a common `EtaEntry`:

| Normalized field | KMB | Citybus | GMB | MTR |
|------------------|-----|---------|-----|-----|
| `etaMinutes` | computed from `eta` | computed from `eta` | `diff` | `ttnt` |
| `etaTime` | `eta` (ISO 8601) | `eta` | `timestamp` | `time` |
| `destination` | `dest_*` | `dest_*` | `dest_*` | station name from `dest` |
| `platform` | — | — | — | `plat` |
| `remarks` | `rmk_*` | `rmk_*` | `remarks_*` | — |

## REST client interfaces

Located in `src/main/java/com/faworkshop/ptdashboard/service/client/`:

- `KmbClient`
- `CitybusClient`
- `NlbClient`
- `GmbClient`
- `MtrClient`

All methods return `Uni<T>` via REST Client Reactive.

## References

- [KMB/LWB ETA (data.gov.hk)](https://data.gov.hk/en-data/dataset/hk-td-tis_21-etakmb)
- [Citybus ETA (data.gov.hk)](https://data.gov.hk/en-data/dataset/ctb-eta-transport-realtime-eta)
- [GMB ETA (data.gov.hk)](https://data.gov.hk/en-data/dataset/hk-td-sm_7-real-time-arrival-data-of-gmb)
- [MTR Next Train (data.gov.hk)](https://data.gov.hk/en-data/dataset/mtr-data2-nexttrain-data)
