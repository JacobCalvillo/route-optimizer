# Delivery Route Optimizer

An AI-assisted delivery router with a strict split of responsibilities:

- **Claude reads the orders.** Free-form dispatcher notes in Spanish — *"entregar en Av. Insurgentes
  Sur 1602, urgente; paquete para Juan en Reforma 222, sin prisa"* — are turned into structured
  `{address, priority, time window}` records via the Anthropic Java SDK's structured outputs.
- **Deterministic code does everything else.** Geocoding, the distance matrix, and the stop
  sequence are ordinary algorithms. Given the same orders and configuration, the route is always
  identical, and every number it reports can be traced to a formula rather than a model.

The model is called exactly once per batch of text, and never again.

---

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Data JPA, H2 (file-backed) |
| AI | `com.anthropic:anthropic-java`, model `claude-opus-5`, structured outputs |
| Geocoding | OpenStreetMap Nominatim, with a database-backed cache |
| Distances | Haversine (default) or OSRM road routing, selected by configuration |
| Frontend | Angular 22 (standalone components, signals), Leaflet |

---

## Running it

### Prerequisites

- JDK 21 or newer (built and tested on JDK 25)
- Node 20 or newer
- An Anthropic API key, if you want free-form parsing. Everything else works without one.

### Backend

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."   # optional; omit to use manual order entry only
cd backend
.\mvnw spring-boot:run                   # http://localhost:8080
```

The H2 database is a file under `backend/data/`, so orders and the geocoding cache survive
restarts. Delete that directory to start clean.

### Frontend

```powershell
cd frontend
npm install
npm start                                # http://localhost:4200
```

`proxy.conf.json` forwards `/api` to the backend, so the dev server needs no CORS handling.

---

## How the optimizer works

The interesting part is not that it sorts stops — it is *what* it minimizes.

A textbook TSP minimizes distance. That would quietly ignore both of this system's actual
requirements: urgent orders should go early, and delivery windows should be met. So the local
search minimizes a **penalized cost**, with all three terms expressed in metres so they are
directly comparable:

```
cost = totalDistance
     + latePenaltyPerMinute       * Σ lateMinutes(stop)
     + priorityPenaltyPerPosition * Σ priorityWeight(stop) * position(stop)
```

An `URGENT` stop (weight 3.0) in position 8 costs six times what it costs in position 4; a `LOW`
stop (weight 0.3) barely cares where it lands. Missing a window is never fatal — the route is
scored worse, not rejected, so you always get a plan plus an explicit list of the promises it
breaks.

Two phases, both in `com.routeopt.routing`:

1. **`NearestNeighborSolver`** — greedy construction from the depot, choosing the stop that
   minimizes `distance / priorityWeight`, so urgency is baked into the starting tour. O(n²).
2. **`TwoOptImprover`** — 2-opt local search: reverse a segment, re-score, keep the improvement,
   repeat until a full pass finds nothing.

> **A note for whoever optimizes this later.** `TwoOptImprover` re-evaluates the whole tour for
> every candidate move instead of using the classic four-edge O(1) delta. That is deliberate. The
> delta shortcut is only valid when the objective is a sum over edges of a symmetric matrix; ours
> is not — lateness depends on the arrival clock, which every earlier stop shifts, and the priority
> term depends on position. Rewriting it into the four-edge form would be faster and wrong.

The API returns both the greedy tour's distance and the final one, so the improvement is visible
rather than merely claimed (typically 15–25%).

---

## API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/health` | Status, whether the AI parser has credentials, active matrix provider |
| `POST` | `/api/orders/parse` | Extract orders from `{"text": "..."}` — no geocoding, nothing stored |
| `POST` | `/api/orders` | Extract, geocode, and persist |
| `POST` | `/api/orders/manual` | Add one order from structured fields, bypassing the model |
| `GET` | `/api/orders` | List orders |
| `PATCH` | `/api/orders/{id}` | Correct an order; re-geocodes when the address changes |
| `DELETE` | `/api/orders/{id}` | Delete an order |
| `POST` | `/api/orders/geocode-retry` | Retry orders still `PENDING` or `FAILED` |
| `POST` | `/api/routes/optimize` | Optimize; body takes `depot`, `departureTime`, optional `orderIds` |

```powershell
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" `
  -d '{\"text\":\"entregar en Av. Insurgentes Sur 1602, urgente; Masaryk 111 antes de las 13:00\"}'

curl -X POST http://localhost:8080/api/routes/optimize -H "Content-Type: application/json" `
  -d '{\"depot\":{\"lat\":19.4326,\"lon\":-99.1332,\"label\":\"Central Warehouse\"},\"departureTime\":\"08:00\"}'
```

---

## Configuration

Everything lives under `app.*` in `backend/src/main/resources/application.yml`.

| Property | Default | What it does |
|---|---|---|
| `app.ai.model` | `claude-opus-5` | Model used for extraction |
| `app.ai.max-tokens` | `8000` | Covers thinking **and** the JSON answer — don't drop below ~4000 |
| `app.geocoding.user-agent` | — | **Change this.** Nominatim's policy requires an identifying value |
| `app.geocoding.min-interval-millis` | `1100` | Enforces Nominatim's one-request-per-second limit |
| `app.routing.matrix` | `haversine` | Switch to `osrm` for real road distances |
| `app.routing.detour-factor` | `1.3` | Scales straight-line distance toward road distance |
| `app.routing.average-speed-kmh` | `30` | Used for Haversine durations only; OSRM supplies its own |
| `app.routing.late-penalty-per-minute` | `500` | Metres of cost per minute past a deadline |
| `app.routing.priority-penalty-per-position` | `200` | Metres of cost per weighted position |

Switching to real road routing is one line:

```yaml
app:
  routing:
    matrix: osrm
```

`/api/health` reports which provider is live. If the OSRM server is unreachable the provider falls
back to Haversine and says so in the response's `matrixProvider` field, rather than failing the
request.

---

## Tests

```powershell
cd backend
.\mvnw test
```

The suite targets the parts where a bug would be invisible from the outside:

- `HaversineDistanceMatrixProviderTest` — a known real-world distance, matrix symmetry, the detour
  factor, and speed-derived durations.
- `RouteEvaluatorTest` — arriving early waits rather than counting as a violation; arriving late
  accumulates penalty; the tour closes back at the depot.
- `TwoOptImproverTest` — untangles a deliberately crossed tour, preserves every stop exactly once,
  and **never returns a worse tour than it was given** (checked across 25 randomized instances).
- `RouteOptimizerTest` — the same stop marked `URGENT` is served earlier than when marked `NORMAL`;
  a tight deadline pulls a stop forward; an impossible window produces a warning rather than a
  failure.

---

## Known limits

- **One vehicle.** This is a TSP with side constraints, not a fleet VRP — no capacities, no
  splitting work across drivers.
- **Routes must fit inside one day.** The scheduler tracks minutes since midnight and wraps past
  24:00 rather than rolling over to a real next-day timestamp.
- **Nominatim is rate-limited to one request per second**, so a first batch of twenty new addresses
  takes about twenty seconds. The cache makes every later run instant.
- **The public OSRM demo server** is fine for trying things out and unsuitable for production; point
  `app.routing.osrm.base-url` at your own instance.
