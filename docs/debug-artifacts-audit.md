# Debug & Dev-Only Artifacts Audit

A full sweep of the codebase (backend `gdg/`, frontend `meme-arena-frontend/`) for debugging-style
code left behind from development — persistent loggers, verbose console output, relaxed security
gates, seeded test data, disabled code, and similar. Compiled 2026-08-09.

Nothing here is flagged as automatically wrong — a lot of it is genuinely useful for local dev.
The point of this doc is a single place to check **before a demo, before opening the repo to
someone else, or before pointing a build at production** — so nothing dev-only slips through
unnoticed.

---

## Frontend (`meme-arena-frontend/src`)

### 1. Persistent auth debug logger — `contexts/AuthContext.jsx`, `services/api.js`
Every auth event (login attempt, token save, session clear, 401s) is written to
`localStorage['_authLog']` as a rolling 50-entry log, in addition to `console.log('[AUTH]', ...)`.
Readable any time from the browser console via:
```js
JSON.parse(localStorage.getItem('_authLog') || '[]')
```
- `AuthContext.jsx:8-19` — the `authLog()` helper
- `AuthContext.jsx:69,73,90,95` — logs on every login step, **including a 20-char token preview**
- `api.js:41-53` — mirrors 401 interceptor events into the same log

**Why it's there:** clearly built to debug an auth/session bug (see item 3 below — the disabled
`useEffect` mentions a real "session clearing race condition"). Useful for reproducing auth issues
after the fact without reopening devtools mid-session.
**Watch out for:** it persists across page loads and never expires — on a shared/public machine
this leaves auth flow details (not the full token, but a prefix) sitting in `localStorage`
indefinitely.

### 2. Heavy `DEBUG`-prefixed console logging — `pages/MemeGallery.jsx`
23 `console.log`/`console.error` calls, all literally prefixed `DEBUG:` with emoji markers
(🚀 📡 ✅ ❌ 🎨), covering the entire meme-fetch lifecycle: request start, response status/headers,
raw payload, per-meme iteration, and every render (`MemeGallery.jsx:186-191` logs on **every
render**, not just on data change).
- Lines: 23, 29, 32, 35-36, 40, 44-45, 49, 59, 61-62, 66-67, 76, 186-191, 194, 395, 399

**Why it's there:** this is almost certainly the trail from debugging the exact frontend/backend
API-URL mismatch found earlier in this project (`MemeGallery.jsx` falls back to a relative URL
when `VITE_API_URL` is unset, which breaks under the docker-compose nginx setup) — the log
sequence traces exactly that request path.
**Watch out for:** this is by far the noisiest file in the app; logging on every render is a minor
perf cost too. Safe to strip once that underlying API-URL issue is confirmed fixed.

### 3. Disabled `useEffect` with an incident note — `contexts/AuthContext.jsx:53-56`
```js
/* ── Background token validation on mount ── */
/* DISABLED — causes session clearing race condition.
   Token validity is checked on every API call via the 401 interceptor.
useEffect(() => { ... }, []); */
```
**Why it's there:** a real prior bug — validating the token in the background on mount raced with
the 401 interceptor's own session-clearing logic, presumably logging users out incorrectly. Left
commented instead of deleted, with the reasoning intact.
**Worth knowing:** this is the most valuable comment in the audit — it documents a real fixed bug
and warns against reintroducing that pattern. Don't delete this one; it's institutional memory.

### 4. Scattered `console.error` on API failures (no central error reporting)
`console.error(...)` on catch blocks in `Leaderboard.jsx:70`, `TournamentList.jsx:146`,
`TournamentCreate.jsx:86`, `UploadMeme.jsx:193`, `MemeGallery.jsx:111`. All silent to the user
beyond whatever UI state each page sets — errors only surface if someone has devtools open.
**Not urgent**, but if this ever needs real production error visibility (Sentry, etc.), these are
the call sites to wire up.

---

## Backend (`gdg/src/main/java/com/meme/gdg`)

### 5. `devFilterChain` — every endpoint open, no auth enforced — `config/SecurityConfig.java:73-89`
```java
@Bean
@Profile({"dev", "docker-dev"})
public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
    ...
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/**").permitAll()
        .anyRequest().permitAll()
    );
```
In `dev` and **`docker-dev`** profiles — i.e. exactly the profile your local `docker-compose up`
uses — every single endpoint is `permitAll()`. The JWT filter still runs and populates
`Authentication` when a token is present, but nothing requires it. This is explicitly commented as
intentional ("All endpoints open so development is frictionless"), and `prodFilterChain`
(`SecurityConfig.java:97-140`) is properly locked down with per-endpoint rules and `hasRole("ADMIN")`
checks.
**Watch out for:** if you're demoing the app locally via docker-compose and want to show off the
auth/authorization behavior, it won't reflect production — everything just works regardless of
login state in that profile.

### 6. Hardcoded wildcard CORS on 5 controllers — bypasses the configured origin list
```java
@CrossOrigin(origins = "*", maxAge = 3600)
```
Present on `AuthController`, `MemeController`, `BattleVoteController`, `TournamentController`,
`QuickBattleController` — and `WebSocketConfig.java:22` sets
`.setAllowedOriginPatterns("*")` for the WebSocket endpoint too.

Meanwhile `SecurityConfig.java` has a proper, profile-aware CORS bean
(`corsConfigurationSource()`, driven by `app.cors.allowed-origins`) that's meant to be the single
source of truth. Class-level `@CrossOrigin` **takes precedence over the global CORS bean for those
specific controllers**, so this isn't dead code — it actively opens those 5 controllers (i.e. most
of the API surface) to any origin, **in every profile including prod**, regardless of what
`CORS_ORIGINS` is set to.
**This is the one item in this audit worth treating as a real inconsistency, not just dev
leftover** — it looks like `@CrossOrigin(origins = "*")` was added early (probably to unblock
local frontend dev quickly) and never removed once the real CORS bean was built. Worth deciding
deliberately whether to keep the wildcard or delete these annotations and let the CORS bean govern.

### 7. Seeded dummy accounts on every startup — `component/DataInitializer.java`
`@Profile({"dev", "docker-dev"})` (confirmed excluded from `prod`) — reseeds on every boot:
- `admin` / `admin123` (ADMIN role)
- 6 users (`memeLord`, `funnyGuy`, `jokeQueen`, `laughMaster`, `giggleGirl`, `chuckleChamp`) all on
  password `password123`
- 20 sample memes (Unsplash-hosted images, programmer-humor titles) with randomized votes

**Fine as-is** — profile-gated correctly, this is what makes local dev/demo usable without manual
signup. Just don't be surprised by "yes I remember the password, it's `password123`" muscle memory
carrying over anywhere real.

### 8. Verbose logging enabled in dev/docker-dev — `application-dev.properties`, `application-docker-dev.properties`
```
logging.level.com.meme.gdg=DEBUG
logging.level.org.springframework.security=DEBUG   # docker-dev only (INFO in plain dev)
logging.level.org.hibernate.SQL=DEBUG
```
Plus `spring.h2.console.enabled=true` and `management.endpoint.health.show-details=always` in
`dev`. All three are correctly tightened in `application-prod.properties`
(`INFO`/`WARN`/`WARN`, H2 console absent, `show-details=never`). No action needed — flagged here
just so the contrast is documented in one place.

### 9. Raw exception messages returned to API clients — `exception/GlobalExceptionHandler.java:76-90`
```java
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<MessageResponse> handleRuntimeException(RuntimeException ex, WebRequest request) {
    log.error("Runtime exception: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse(ex.getMessage()));
}
```
Every `RuntimeException` thrown anywhere in the service layer (e.g. `"User not found: 42"`,
`"Battle pair not found: 7"` — see `BattleVoteServiceImpl`) gets its **raw `.getMessage()`** sent
straight to the API response body. Only the generic top-level `Exception` handler
(`GlobalExceptionHandler.java:84-90`) returns a scrubbed `"An unexpected error occurred"`.
**Convenient for debugging from the frontend/Postman**, since you see exactly what failed without
checking server logs — but it's the same instinct as the frontend's verbose logging, just
server-side: informative during development, a bit chatty for a hardened API (internal identifiers
and phrasing leak to any caller, authenticated or not).

---

## Quick-reference table

| # | What | Where | Scope | Action before prod/demo? |
|---|------|-------|-------|---------------------------|
| 1 | Persistent `_authLog` in localStorage | `AuthContext.jsx`, `api.js` | frontend, all envs | Low priority — consider gating behind `import.meta.env.DEV` |
| 2 | 23 `DEBUG:` console logs | `MemeGallery.jsx` | frontend, all envs | Strip once API-URL bug is confirmed fixed |
| 3 | Disabled `useEffect` (documented bug) | `AuthContext.jsx:53-56` | frontend | **Keep** — don't delete, it's a bug-avoidance note |
| 4 | Scattered `console.error` | 5 pages | frontend, all envs | Fine as-is; wire to real logging if it ever matters |
| 5 | All endpoints `permitAll()` | `SecurityConfig.java` (dev/docker-dev) | backend, dev only | Fine — but don't demo auth behavior from docker-compose |
| 6 | `@CrossOrigin(origins="*")` on 5 controllers | 5 controllers + `WebSocketConfig` | **backend, ALL profiles incl. prod** | **Decide deliberately** — likely leftover, currently overrides prod CORS config |
| 7 | Seeded dummy accounts/memes | `DataInitializer.java` | backend, dev only | Fine — profile-gated correctly |
| 8 | Verbose SQL/security logging, H2 console | `application-dev*.properties` | backend, dev only | Fine — already tightened in prod |
| 9 | Raw exception messages in API responses | `GlobalExceptionHandler.java` | backend, ALL profiles | Medium priority — consider generic messages for `RuntimeException` too |

**The two worth actually acting on, not just knowing about:** #6 (wildcard CORS silently active in
prod) and #9 (internal error detail leaking to every caller). Everything else is either
intentionally dev-only and already gated correctly, or low-stakes frontend logging.
