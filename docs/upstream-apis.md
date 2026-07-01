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
| Light Rail (LRT) | MTR | `https://rt.data.gov.hk/v1/transport/mtr/lrt` | Yes |
| MTR Bus & feeder | MTR | `https://rt.data.gov.hk/v1/transport/mtr/bus` | Yes |
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
| `LrtClient` | GET | `/getSchedule?station_id={ID}&with_special={0\|1}` → filter `route_no` |
| `MtrBusClient` | POST | `/getSchedule` body: `language=en&routeName=K65` → filter `busStop` by `busStopId` |

### Example URLs

```
# KMB — route 720 at a stop
https://data.etabus.gov.hk/v1/transport/kmb/eta/{stopId}/720/1

# Citybus — route 720 at stop 001313
https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001313/720

# GMB — route at stop
https://data.etagmb.gov.hk/eta/route-stop/{routeId}/{stopId}

# MTR heavy rail — TKL at Tseung Kwan O
https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=TKL&sta=TKO&lang=EN

# Light Rail — Yuen Long station (station_id 600)
https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule?station_id=600&with_special=1

# MTR Bus — route K65 (returns all stops; filter by busStopId)
curl -X POST https://rt.data.gov.hk/v1/transport/mtr/bus/getSchedule \
  -d "language=en&routeName=K65"
```

## Discovery / static data endpoints (cached 24h)

Used by search and the add-favorite flow.

| Client | Endpoint | Purpose |
|--------|----------|---------|
| KMB | `/stop`, `/route`, `/route-stop/{route}/{dir}/{serviceType}` | Stop and route search |
| Citybus | `/stop`, `/route`, `/route-stop/{route}/{direction}` | Stop and route search |
| GMB | `/stop`, `/route`, `/stop-route/{stopId}` | Routes at stop (for route picker) |
| MTR | Static metadata in app | Heavy rail line/station picker |
| LRT | `getSchedule` + station ID table | LRT station picker; routes from `platform_list` |
| MTR Bus | Routes/stops CSV + `getSchedule` | Route variants and stop list from [MTR routes & fares](https://data.gov.hk/en-data/dataset/mtr-data-routes-fares-barrier-free-facilities) |

### Routes-at-stop / routes-at-station

Critical for the mandatory route picker step:

- **GMB:** `GET /stop-route/{stopId}`
- **KMB:** derive from `/route-stop` data or stop-level route list
- **Citybus:** `GET /route-stop/{route}/{direction}` combined with stop lookup
- **LRT:** `GET /getSchedule?station_id={id}` — extract unique `route_no` values from `platform_list[].route_list`
- **MTR Bus:** `POST /getSchedule` with `routeName` — `busStop[]` lists stops on route; stops for picker; ETAs in `busStop[].bus[]`

## MTR heavy rail — lines and stations

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

## Light Rail (LRT)

| Item | Detail |
|------|--------|
| Dataset | [Real-time Light Rail train information (data.gov.hk)](https://data.gov.hk/en-data/dataset/mtr-lrnt_data-light-rail-nexttrain-data) |
| Spec | [LR Next Train API Spec v1.1](https://opendata.mtr.com.hk/doc/LR_Next_Train_API_Spec_v1.1.pdf) |
| Update frequency | ~10 seconds |
| Rate limit | HTTP 429 on overload |

### Parameters

| Param | Required | Description |
|-------|----------|-------------|
| `station_id` | Yes | Numeric LRT station ID (e.g. `600` = Yuen Long, `1` = Tuen Mun Ferry Pier) |
| `with_special` | No | `0` = regular routes only (default); `1` = include special routes |

### Response shape

```json
{
  "platform_list": [
    {
      "platform_id": 1,
      "route_list": [
        {
          "route_no": "614",
          "dest_en": "Tuen Mun Ferry Pier",
          "dest_ch": "屯門碼頭",
          "time_en": "3",
          "time_ch": "3",
          "platform_id": 1,
          "train_length": 2,
          "arrival_departure": "A",
          "stop": 0,
          "special": 0
        }
      ]
    }
  ]
}
```

- `time_en` / `time_ch` — minutes until arrival, or `"-"` / arriving-departing text for imminent trains
- `special=1` — use `additionalInfo1` for route number instead of `route_no`
- `stop=1` — route suspended at this station

### Favorite model

LRT favorites require **station + route** (same rule as bus/GMB):

- Fetch schedule for `station_id`
- Filter `route_list` entries matching saved `routeNo`
- Optional `platformId` filter; optional `withSpecial` for special services

Station IDs and names are cached from the API data dictionary appendix (~68 stations in Tuen Mun / Yuen Long / Tin Shui Wai area).

## MTR Bus & feeder bus

| Item | Detail |
|------|--------|
| Dataset | [港鐵巴士及接駁巴士實時到站時間 (data.gov.hk)](https://data.gov.hk/tc-data/dataset/mtr-mtr_bus-mtr-bus-eta-data) |
| Spec | [MTR Bus API Spec v1.13+](https://opendata.mtr.com.hk/doc/MTR_BUS_API_Spec_v1.13.pdf) |
| Data dictionary | [MTR_BUS_DataDictionary v1.19](https://opendata.mtr.com.hk/doc/MTR_BUS_DataDictionary_v1.19.pdf) |
| Update frequency | ~10 seconds |
| Rate limit | HTTP 429 on overload |

### Request

```
POST https://rt.data.gov.hk/v1/transport/mtr/bus/getSchedule
Content-Type: application/x-www-form-urlencoded

language=en&routeName=K65
```

| Param | Required | Description |
|-------|----------|-------------|
| `language` | Yes | `en` or `zh` |
| `routeName` | Yes | Route identifier — may include direction variant (e.g. `K65`, `K66 Tai Tong Wong Nai Tun Tsuen to On Hong Road`, `506 Glorious Garden to Tuen Mun Station`) |

### Response shape

Returns route-level schedule with per-stop bus arrivals:

```json
{
  "routeName": "K65",
  "appRefreshTimeInSecond": "10",
  "busStop": [
    {
      "busStopId": "K65-U010",
      "bus": [
        {
          "busId": "K65-001",
          "arrivalTimeInSecond": "180",
          "arrivalTimeText": "3 minutes",
          "departureTimeInSecond": "120",
          "departureTimeText": "2 minutes",
          "isScheduled": "0",
          "lineRef": "K65-YLS"
        }
      ]
    }
  ]
}
```

- `arrivalTimeText` — `"N minutes"` / `"N 分鐘"`, `"Arriving"`, `"Departed"`, or empty at route origin
- `isScheduled` — `"1"` = timetable-based estimate; `"0"` = real-time
- Static routes/stops from [MTR Bus & Feeder Bus Routes/Stops CSV](https://data.gov.hk/en-data/dataset/mtr-data-routes-fares-barrier-free-facilities)

### Favorite model

MTR Bus favorites require **stop + route** (same rule as franchised bus):

- `routeName` — full route variant string (direction is encoded in the name when multiple variants exist)
- `busStopId` — stop on that route (from `busStop[].busStopId`)
- Fetch via `POST getSchedule`, locate matching `busStop`, return `bus[]` ETAs (up to all active buses at that stop)

## Refresh rates and rate limits

| API | Data refresh | Documented rate limit |
|-----|--------------|----------------------|
| KMB | ~60s | None published |
| Citybus | ~60s | None published |
| NLB | Real-time | None published |
| GMB | ~60s | None published |
| MTR | ~10s | HTTP 429 on overload |
| LRT | ~10s | HTTP 429 on overload |
| MTR Bus | ~10s | HTTP 429 on overload |
| Batch (CTB/NLB) | ~60s | HTTP 429 on overload |

**Our policy:** cache bus/GMB at 60s; MTR, LRT, and MTR Bus at 10s. Never poll faster than upstream refresh rates.

## Response normalization

Each operator returns different field names. `EtaNormalizer` services map them to a common `EtaEntry`:

| Normalized field | KMB | Citybus | GMB | MTR | LRT | MTR_BUS |
|------------------|-----|---------|-----|-----|-----|---------|
| `etaMinutes` | from `eta` | from `eta` | `diff` | `ttnt` | parse `time_en` | `arrivalTimeInSecond` / 60 |
| `etaTime` | `eta` | `eta` | `timestamp` | `time` | computed | computed from seconds |
| `destination` | `dest_*` | `dest_*` | `dest_*` | from `dest` | `dest_en` | from `lineRef` / route |
| `platform` | — | — | — | `plat` | `platform_id` | — |
| `remarks` | `rmk_*` | `rmk_*` | `remarks_*` | — | special route | `isScheduled`, `busStopRemark` |

## REST client interfaces

Located in `src/main/java/com/faworkshop/ptdashboard/service/client/`:

- `KmbClient`
- `CitybusClient`
- `NlbClient`
- `GmbClient`
- `MtrClient`
- `LrtClient`
- `MtrBusClient`

All methods return `Uni<T>` via REST Client Reactive.

## References

- [KMB/LWB ETA (data.gov.hk)](https://data.gov.hk/en-data/dataset/hk-td-tis_21-etakmb)
- [Citybus ETA (data.gov.hk)](https://data.gov.hk/en-data/dataset/ctb-eta-transport-realtime-eta)
- [GMB ETA (data.gov.hk)](https://data.gov.hk/en-data/dataset/hk-td-sm_7-real-time-arrival-data-of-gmb)
- [MTR Next Train (data.gov.hk)](https://data.gov.hk/en-data/dataset/mtr-data2-nexttrain-data)
- [Light Rail Next Train (data.gov.hk)](https://data.gov.hk/en-data/dataset/mtr-lrnt_data-light-rail-nexttrain-data)
- [MTR Bus & Feeder Bus ETA (data.gov.hk)](https://data.gov.hk/tc-data/dataset/mtr-mtr_bus-mtr-bus-eta-data)
