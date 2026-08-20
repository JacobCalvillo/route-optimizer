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
| AI | `com.anthropic:anthropic-java`, model `claude-sonnet-5`, structured outputs |
| Geocoding | OpenStreetMap Nominatim, with a database-backed cache |
| Distances | OSRM road routing (default), or an offline Haversine approximation |
| Frontend | Angular 22 (standalone components, signals), Leaflet |

---

## Running it

### Prerequisites

- JDK 21 or newer (built and tested on JDK 25)
- Node 20 or newer
- An Anthropic API key, if you want free-form parsing. Everything else works without one.

### Configure

Each side has a `.env` (gitignored) alongside a tracked `.env.example` documenting every key:

```powershell
cd backend;  cp .env.example .env    # then paste your ANTHROPIC_API_KEY
cd ..\frontend; cp .env.example .env # BACKEND_URL, only if you moved the backend off 8080
```

`backend/.env` is read by Spring via `spring.config.import` and holds both the API key and any
`app.*` / `server.*` override you want locally. `frontend/.env` is read by `proxy.conf.js` and only
affects the dev server — **never put a secret in it**, since anything the frontend can read is
visible in devtools.

Real environment variables take precedence over `.env`, so CI and containers override anything
without editing files:

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."   # wins over backend/.env
```

### Backend

```powershell
cd backend
.\mvnw spring-boot:run                   # http://localhost:8080
```

The H2 database is a file under `data/`, **relative to the directory you launched from** — running
from the repository root and from `backend/` produces two different databases. `/api/health` reports
the resolved path so this is never a mystery. Delete that directory to start clean.

> Two notes on resetting it. `AUTO_SERVER` is deliberately **off**: with it on, a still-running
> instance keeps the database open in server mode, so deleting the files appears to work and
> changes nothing. And stopping an instance is not instant — it rewrites its lock file on the way
> out, so a delete issued immediately after can lose the race. Wait a few seconds.

> `ddl-auto: update` is prototype-grade in a way worth knowing: it **cannot add a `NOT NULL` column
> to an existing table**, and it only logs a warning when the ALTER fails. The application starts
> normally and then fails on the first query with "column not found". New columns here are nullable
> for that reason. Production would use Flyway.

### Frontend

```powershell
cd frontend
npm install
npm start                                # http://localhost:4200
```

`proxy.conf.js` forwards `/api` to the backend (reading `BACKEND_URL` from `frontend/.env`), so the
dev server needs no CORS handling.

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
| `POST` | `/api/routes/optimize` | Optimize; `depot` takes an address or coordinates, plus `departureTime` and optional `orderIds` |

```powershell
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" `
  -d '{\"text\":\"entregar en Av. Insurgentes Sur 1602, urgente; Masaryk 111 antes de las 13:00\"}'

curl -X POST http://localhost:8080/api/routes/optimize -H "Content-Type: application/json" `
  -d '{\"depot\":{\"address\":\"Zocalo, Ciudad de Mexico\"},\"departureTime\":\"08:00\"}'
```

### The depot

`depot` accepts three shapes, resolved in this order:

```jsonc
{ "lat": 19.4326, "lon": -99.1332 }   // explicit coordinates
{ "address": "19.4326, -99.1332" }    // a pasted coordinate pair
{ "address": "Zocalo, Ciudad de Mexico" }  // geocoded like any delivery
```

The middle case is why the UI needs only one text field: a bare numeric pair short-circuits to
coordinates, and anything else goes through the same geocoder and cache the deliveries use.
`Reforma 222` is not mistaken for a pair. When no label is given, the depot is named after where it
actually resolved, so the map tooltip shows the geocoder's answer rather than what was typed.

---

## Configuration

Defaults live in `backend/src/main/resources/application.yml`; override any of them in
`backend/.env` or as an environment variable.

> **`.env` is parsed as a Java properties file, not by a shell.** So: no `export`, no `$VAR`
> expansion, and **do not quote values** — quotes become part of the value. Use dotted keys
> (`app.routing.matrix=osrm`), not `APP_ROUTING_MATRIX`; the uppercase form only works as a real
> environment variable.

| Property | Default | What it does |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | API key; blank disables free-form parsing only |
| `app.ai.model` | `claude-sonnet-5` | Model used for extraction; swap freely, it is configuration |
| `app.ai.max-tokens` | `8000` | Covers thinking **and** the JSON answer — don't drop below ~4000 |
| `app.geocoding.user-agent` | — | **Change this.** Nominatim's policy requires an identifying value |
| `app.geocoding.min-interval-millis` | `1100` | Enforces Nominatim's one-request-per-second limit |
| `app.routing.matrix` | `osrm` | `haversine` for an offline approximation with no network |
| `app.routing.osrm.overview` | `full` | Detail of the drawn route line; `simplified` for a lighter payload |
| `app.routing.max-stop-distance-km` | `150` | Warns when a stop is this far from the depot (probable geocoding error) |
| `app.routing.detour-factor` | `1.3` | Scales straight-line distance toward road distance |
| `app.routing.average-speed-kmh` | `30` | Used for Haversine durations only; OSRM supplies its own |
| `app.routing.late-penalty-per-minute` | `500` | Metres of cost per minute past a deadline |
| `app.routing.priority-penalty-per-position` | `200` | Metres of cost per weighted position |

### Road routing and the drawn line

By default the backend asks OSRM for two different things: `/table` for the distance matrix the
optimizer needs *before* it knows the order, and `/route` for the geometry of the tour it chose.
That second call is what makes the line on the map follow streets — without it the map would
contradict its own summary, showing a straight line next to a road distance.

The response carries `legs` — **one polyline per leg** rather than a single line — plus
`geometrySource`. Splitting per leg is what lets the map style each segment independently. When no
road data is available both are null, and the map draws **dashed** straight segments and says so,
rather than passing an approximation off as a real route.

### Why the legs are one hue and not a rainbow

Legs are an *ordinal* variable: they have an order and a direction. So the map encodes them as a
single blue hue running light to dark, which makes the direction of travel readable at a glance.
A set of unrelated colours would read as "unrelated categories" and destroy exactly that.

The ramp holds **five** visually distinct steps and no more. That is measured, not guessed: on the
OpenStreetMap tile background, nine evenly spaced steps of the same hue sit 0.047 apart in
lightness, well under the 0.06 needed to tell neighbours apart, and the lightest step of the source
ramp only reaches 1.84:1 contrast against the tiles — below the 2:1 floor. The shipped ramp
(`#6da7ec → #0d366b`) clears every check.

Past five legs the ramp is interpolated, so neighbouring legs stop being reliably distinguishable
and colour carries **progression** rather than identity. Identity lives on two other channels that
do not degrade: the numbered markers, and a hover tooltip naming each leg with its distance and
arrival time. Colour is never the only way to tell legs apart.

The return-to-depot leg is deliberately outside the ramp — **black and dashed**, because it has no
position in the sequence to encode: it is the drive home, not a delivery. It is drawn at full
opacity, since black at the tint the other legs use composites to a mid grey against the tiles.

To run fully offline:

```yaml
app:
  routing:
    matrix: haversine
```

`/api/health` reports which provider is live. If OSRM is unreachable the matrix falls back to
Haversine and says so in the response's `matrixProvider` field, and the geometry is simply omitted
— the request never fails because of it.

### When a real address will not geocode

A dispatcher writes `Fracc. Felipe Tena Ramirez 101, Praderas de Huinala, 66642 Loma La paz, N.L.`
and Nominatim returns nothing — yet the street *is* in OpenStreetMap, and
`Felipe Tena Ramirez 101, Praderas de Huinala` finds it immediately.

What makes that example instructive is that no single transformation fixes it: removing the
`Fracc.` prefix alone still fails, and trimming the noisy tail alone still fails. Only doing both
works. So `AddressQueries` builds a **ladder** — the address as written, then progressively cleaner
and shorter forms — and the geocoder walks it until something matches, capped at five attempts.

Cleaning drops five-digit postal codes (as often wrong as helpful — that one is), strips
place-type prefixes (`Fracc.`, `Col.`, `Priv.`), and expands state abbreviations (`N.L.` →
`Nuevo León`). Note `Col.` is treated as *colonia*, not Colima.

The trade-off is precision, and it is reported rather than hidden. Only the first rung counts as an
exact match; anything later found the street or the neighbourhood, not the number, so the order
gets `geocodeStatus: APPROXIMATE`. Such orders **do** route — a hundred metres rarely changes the
sequence — but the UI outlines them in amber and says *street-level match — check it*.

### A stop in the wrong state

An ambiguous address resolves to a real place that happens to be somewhere else entirely. In
testing, `Paseo de la Reforma 222` with no city attached geocoded to **Playa del Carmen**, turning a
70 km city round trip into a 3,200 km one. The route was arithmetically perfect and useless.

`app.routing.max-stop-distance-km` catches this: any stop further than that from the depot produces
a warning naming it and the distance. It is a warning and never an exclusion, because the threshold
is a heuristic and long routes are legitimate — the operator decides, but only if told.

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
