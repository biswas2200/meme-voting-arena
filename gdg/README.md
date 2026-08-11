# Meme Voting Arena — Backend

Spring Boot backend for the Meme Voting Arena platform.

## Tech Stack

- **Java 21** + **Spring Boot 3.5**
- **Spring Security** — JWT authentication, role-based authorization
- **Spring Data JPA** — ORM
- **H2** — In-memory DB for development
- **PostgreSQL** — Production DB
- **WebSocket / STOMP** — Real-time vote broadcasts
- **Maven** — Build tool

## Running Locally

```bash
# Uses H2 in-memory DB (dev profile)
./mvnw spring-boot:run
```

Backend available at `http://localhost:8080`.
H2 console (dev only): `http://localhost:8080/h2-console`

## Running with Docker

```bash
# From the project root
docker compose --env-file .env.dev up --build
```

## Configuration

All secrets are injected via environment variables. See `.env.dev.example` and `.env.prod.example` at the project root.

| Variable | Profile | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | All | `dev`, `docker-dev`, or `prod` |
| `DATABASE_URL` | prod | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | prod | DB username |
| `DATABASE_PASSWORD` | prod | DB password |
| `JWT_SECRET` | prod | Min 256-bit random string |
| `CORS_ORIGINS` | prod | Allowed frontend origin(s) |

## Testing

```bash
# All tests
./mvnw test

# Unit tests only
./mvnw test -Dtest="MemeServiceUnitTest,BattleVoteServiceUnitTest,TournamentServiceUnitTest,QuickBattleServiceUnitTest,UserProfileServiceUnitTest,RoundAdvancementSchedulerUnitTest"

# Integration tests
./mvnw test -Dtest="QuickBattleIntegrationTest,TournamentLifecycleIntegrationTest,SecurityConfigIntegrationTest"
```

**88 tests total — 0 failures.**

## Project Structure

```
src/main/java/com/meme/gdg/
├── config/         # SecurityConfig, WebSocketConfig, WebConfig
├── controller/     # REST endpoints
├── dto/            # Request/response DTOs (all validated with @Valid)
├── exception/      # GlobalExceptionHandler, custom exceptions
├── model/          # JPA entities
├── repository/     # Spring Data repositories
├── scheduler/      # RoundAdvancementScheduler
├── security/       # JwtUtils, AuthTokenFilter, UserPrincipal
└── service/        # Business logic
```
