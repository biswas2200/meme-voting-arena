# 🔥 Meme Voting Arena

A full-stack meme voting platform where users upload memes, vote on them, and compete in bracket-style tournaments. Built with **Spring Boot** backend and **React** frontend.

> **Author:** [Amitosh Biswas](https://github.com/Biswas2200)

---

## 🌟 Features

- **User Authentication** — JWT-based registration and login
- **Meme Upload & Management** — Upload memes with titles and image URLs or file upload
- **Voting System** — Upvote/downvote memes with real-time WebSocket updates
- **Leaderboard** — Top 5 memes ranked by vote count
- **Quick Battle** — Head-to-head voting between two random memes
- **Tournament Mode** — Bracket-style tournaments with admin approval workflow
- **Real-time Updates** — WebSocket/STOMP for live vote counts
- **Role-based Access** — Admin and user roles
- **Responsive Design** — Works on desktop, tablet, and mobile

---

## 🛠️ Tech Stack

### Backend
- **Java 21** + **Spring Boot 3.5**
- **Spring Security** — JWT authentication, role-based authorization
- **Spring Data JPA** — ORM
- **H2** — In-memory database for development
- **PostgreSQL** — Production database (AWS RDS)
- **WebSocket / STOMP** — Real-time communication
- **Maven** — Build tool

### Frontend
- **React 19** + **Vite**
- **Framer Motion** — Animations
- **Axios** — HTTP client
- **@stomp/stompjs** — WebSocket client
- **Vitest** + **@testing-library/react** — Unit and component tests

---

## 🚀 Getting Started

### Prerequisites
- Java 21+, Maven 3.6+
- Node.js 18+, npm
- Docker + Docker Compose (optional but recommended)

### Run with Docker (recommended)

```bash
cp .env.dev.example .env.dev
docker compose --env-file .env.dev up --build
```

- Backend: http://localhost:8080
- Frontend: http://localhost:3000

### Run locally without Docker

**Backend:**
```bash
cd gdg
./mvnw spring-boot:run
# Uses H2 in-memory DB by default (dev profile)
```

**Frontend:**
```bash
cd meme-arena-frontend
npm install
npm run start
```

### Default Seed Users (dev profile only)

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `memeLord` | `password123` | USER |

---

## 🏗️ Project Structure

```
meme-voting-arena/
├── gdg/                          # Spring Boot backend
│   ├── src/main/java/com/meme/gdg/
│   │   ├── config/               # Security, WebSocket, CORS config
│   │   ├── controller/           # REST API endpoints
│   │   ├── dto/                  # Request/response DTOs
│   │   ├── model/                # JPA entities
│   │   ├── repository/           # Data access layer
│   │   ├── scheduler/            # Tournament round advancement
│   │   ├── security/             # JWT filter, UserPrincipal
│   │   └── service/              # Business logic
│   └── src/test/                 # Unit + integration tests (88 tests)
├── meme-arena-frontend/          # React + Vite frontend
│   └── src/
│       ├── components/           # Reusable components
│       ├── contexts/             # Auth, Notification contexts
│       ├── pages/                # Page components
│       └── services/             # Axios API client
├── .env.dev.example              # Dev env template → copy to .env.dev
├── .env.prod.example             # Prod env template
├── docker-compose.yml            # Local dev stack
├── docker-compose.prod.yml       # Production reference
└── SECURITY_CHECKLIST.md         # Pre-deployment security checklist
```

---

## 🔌 API Endpoints

### Authentication (public)
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/signin` | Login — returns JWT |
| POST | `/api/auth/signup` | Register — returns JWT |

### Auth (requires JWT)
| Method | Path | Description |
|---|---|---|
| GET | `/api/auth/profile` | Get own profile + stats |
| PUT | `/api/auth/profile` | Update username / email / password |
| POST | `/api/auth/profile/avatar` | Upload avatar |

### Memes
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/memes` | Public | Paginated meme list |
| GET | `/api/memes/leaderboard` | Public | Top 5 memes |
| GET | `/api/memes/battle` | Public | Two random memes |
| POST | `/api/memes` | User | Create meme (URL) |
| POST | `/api/memes/upload` | User | Upload meme (file) |
| PUT | `/api/memes/{id}/vote` | User | Upvote / downvote |
| DELETE | `/api/memes/{id}` | User/Admin | Delete (owner or admin) |

### Battle & Tournaments
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/battle/quick/pair` | User | Get a new battle pair |
| POST | `/api/battle/vote/quick` | User | Vote on a battle pair |
| POST | `/api/battle/vote/tournament` | User | Vote on a matchup |
| GET | `/api/battle/tournaments` | Public | List tournaments |
| GET | `/api/battle/tournaments/{id}` | Public | Tournament details |
| POST | `/api/battle/tournaments` | User | Create tournament |
| GET | `/api/battle/tournaments/my` | User | My tournaments |
| GET | `/api/battle/tournaments/pending` | Admin | Pending approval |
| POST | `/api/battle/tournaments/{id}/approve` | Admin | Approve |
| POST | `/api/battle/tournaments/{id}/reject` | Admin | Reject |

---

## 🔧 Configuration

All secrets are supplied via environment variables — nothing hardcoded.

| Variable | Required | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `dev`, `docker-dev`, or `prod` |
| `DATABASE_URL` | Prod only | JDBC URL for PostgreSQL |
| `DATABASE_USERNAME` | Prod only | DB username |
| `DATABASE_PASSWORD` | Prod only | DB password |
| `JWT_SECRET` | Prod only | Min 256-bit random string |
| `JWT_EXPIRATION_MS` | Optional | Default: 86400000 (24h) |
| `CORS_ORIGINS` | Prod only | Frontend domain(s) |
| `VITE_API_URL` | Frontend | Backend API base URL |

Generate a secure JWT secret:
```bash
openssl rand -hex 32
```

---

## 🧪 Testing

```bash
# Backend — all tests
cd gdg && ./mvnw test

# Frontend — all tests
cd meme-arena-frontend && npm test
```

**Test coverage:**
- **88 backend tests** — JUnit 5 + Mockito + AssertJ (unit + integration)
- **114 frontend tests** — Vitest + @testing-library/react

---

## 🔒 Security

See [SECURITY_CHECKLIST.md](SECURITY_CHECKLIST.md) for the full pre-deployment checklist.

- All write endpoints require JWT authentication
- Admin-only endpoints enforce `ROLE_ADMIN`
- Passwords hashed with BCrypt
- JWT secret injected via `JWT_SECRET` env var in production
- CORS restricted to configured origins
- File uploads limited to 1 MB, images only
- `target/` and `node_modules/` excluded from git (no compiled secrets)

---

## 🐛 Troubleshooting

| Issue | Fix |
|---|---|
| Port 8080 in use | Set `PORT` env var or change `server.port` |
| CORS errors | Check `CORS_ORIGINS` / `app.cors.allowed-origins` |
| JWT errors | Verify `JWT_SECRET` is set and consistent |
| DB connection | Check `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` |
| File upload fails | Check `spring.servlet.multipart.max-file-size` |

---

## 📝 License

MIT License

---

**Built by [Amitosh Biswas](https://github.com/Biswas2200)**
