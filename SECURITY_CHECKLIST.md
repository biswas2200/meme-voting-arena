# Pre-Deployment Security Checklist

Use this checklist before every production deployment. Check off each item manually.

---

## 1. Authentication & Authorization

- [ ] `@PreAuthorize("isAuthenticated()")` is active on `createMeme`, `uploadMeme`, `voteMeme`, `deleteMeme` in `MemeController`
- [ ] `@PreAuthorize` annotations are active on all write endpoints in `TournamentController` and `BattleVoteController`
- [ ] No fallback "no-auth" branches exist in service layer — all write operations throw `RuntimeException("Authentication required")` when `authentication` is null
- [ ] JWT secret is a strong, randomly generated 256-bit value (not the dev placeholder)
- [ ] JWT expiration is set to an appropriate value (e.g., 24h = `86400000` ms)
- [ ] `AuthTokenFilter` correctly parses and validates the `Authorization: Bearer <token>` header
- [ ] `UserDetailsServiceImpl` loads users by username or email correctly
- [ ] CORS `allowed-origins` is set to the exact frontend domain(s) — not `*` in production
- [ ] Admin-only endpoints (`/api/battle/tournaments/pending`, `approve`, `reject`) require `ROLE_ADMIN`

---

## 2. Input Validation

- [ ] All DTOs with user input have `@Valid` on controller method parameters
- [ ] `MemeRequest`: `@NotBlank` + `@Size` on `title` and `imageUrl`
- [ ] `SignupRequest`: `@NotBlank`, `@Size`, `@Email` on all fields
- [ ] `LoginRequest`: `@NotBlank` on `username` and `password`
- [ ] `TournamentCreateRequest`: `@NotBlank @Size(min=3, max=100)` on `name`; `@NotNull @Size(min=8, max=16)` on `memeIds`; `@NotNull` on `roundDurationHours`
- [ ] `VoteRequest`: `@NotNull` on `voteType`
- [ ] File upload validation: content type must be `image/*`, size must be ≤ 1 MB
- [ ] `GlobalExceptionHandler` returns structured error responses for `MethodArgumentNotValidException`

---

## 3. Secrets Management

- [ ] `JWT_SECRET` is set via environment variable — never hardcoded in `application-prod.properties`
- [ ] `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` are set via environment variables
- [ ] `CORS_ORIGINS` is set via environment variable
- [ ] `.env.prod` (if used locally) is listed in `.gitignore` and never committed
- [ ] No secrets appear in application logs (SQL logging is off: `spring.jpa.show-sql=false`)
- [ ] Rotate JWT secret and DB credentials if they were ever accidentally committed

---

## 4. Production Configuration

- [ ] Active Spring profile is `prod` (`SPRING_PROFILES_ACTIVE=prod`)
- [ ] H2 console is disabled: `spring.h2.console.enabled=false`
- [ ] SQL logging is off: `spring.jpa.show-sql=false`
- [ ] Hibernate DDL is set to `update` (not `create-drop`) — or use a migration tool (Flyway/Liquibase)
- [ ] `DataInitializer` component is annotated `@Profile({"dev", "docker-dev"})` — does NOT run in prod
- [ ] Logging levels are appropriate: `INFO` for app, `WARN` for security and Hibernate

---

## 5. API Security

- [ ] File upload size limits are set: `spring.servlet.multipart.max-file-size=1MB`, `max-request-size=2MB`
- [ ] Actuator exposes only `health`: `management.endpoints.web.exposure.include=health`
- [ ] Actuator health details are hidden: `management.endpoint.health.show-details=never`
- [ ] `/actuator/health` is the only actuator endpoint accessible without authentication
- [ ] CSRF is disabled (stateless JWT API — correct for this architecture)
- [ ] Session management is `STATELESS`
- [ ] WebSocket endpoint `/ws/**` is permitted (required for STOMP connections)
- [ ] Rate limiting is configured at the load balancer / API gateway level (AWS WAF, ALB rules, or similar)

---

## 6. Frontend Security

- [ ] JWT token is stored in `localStorage` or `sessionStorage` — document the tradeoff (XSS risk vs. CSRF risk)
- [ ] Frontend handles `401 Unauthorized` responses by redirecting to login and clearing the stored token
- [ ] Frontend handles `403 Forbidden` responses gracefully (show error, do not expose internal details)
- [ ] All API calls use HTTPS in production — no mixed content
- [ ] CORS is restricted to the exact frontend origin (not `*`)
- [ ] Sensitive data (token, user info) is cleared from storage on logout

---

## 7. Pre-Deployment Smoke Tests

Run these manually or via CI after deploying to a staging environment:

- [ ] `GET /actuator/health` → `200 OK` with `{"status":"UP"}`
- [ ] `GET /api/memes` → `200 OK` (public, no token)
- [ ] `GET /api/memes/leaderboard` → `200 OK` (public, no token)
- [ ] `GET /api/battle/tournaments` → `200 OK` (public, no token)
- [ ] `POST /api/memes` with no token → `401 Unauthorized`
- [ ] `POST /api/memes/upload` with no token → `401 Unauthorized`
- [ ] `PUT /api/memes/{id}/vote` with no token → `401 Unauthorized`
- [ ] `DELETE /api/memes/{id}` with no token → `401 Unauthorized`
- [ ] `POST /api/battle/vote/quick` with no token → `401 Unauthorized`
- [ ] `POST /api/battle/tournaments` with no token → `401 Unauthorized`
- [ ] `GET /api/battle/tournaments/pending` with USER token → `403 Forbidden`
- [ ] `POST /api/auth/signup` with valid data → `200 OK` + JWT token in response
- [ ] `POST /api/auth/signin` with valid credentials → `200 OK` + JWT token in response
- [ ] `GET /api/auth/profile` with valid token → `200 OK`
- [ ] `POST /api/memes` with valid token → `200 OK` (meme created)
- [ ] WebSocket connection to `/ws` → handshake succeeds for authenticated client
- [ ] Upload a file > 1 MB → `400 Bad Request` or `413 Payload Too Large`
