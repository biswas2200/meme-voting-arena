# 🔥 Meme Arena

A full-stack meme battle platform where users upload memes, vote on them in real time, and compete in bracket-style tournaments. Built with **React** frontend and **Spring Boot** backend, containerized with **Docker**.

> **Author:** [Amitosh Biswas](https://github.com/Biswas2200)  
> **GitHub:** https://github.com/biswas2200/meme-voting-arena

---

## 📋 Table of Contents

- [Introduction](#-introduction)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [How the System Works](#-how-the-system-works)
- [Project Structure](#-project-structure)
- [API Endpoints](#-api-endpoints)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [Testing](#-testing)
- [Security](#-security)
- [Troubleshooting](#-troubleshooting)

---

## 📖 Introduction

Meme Arena is a full-stack web application that brings competitive fun to internet culture by letting users upload, vote on, and battle memes in real time. The platform combines a modern React frontend with a robust Spring Boot backend, packaged as Docker containers for consistent local development and deployment anywhere Docker runs.

Users can register an account, upload memes either by URL or file, and participate in two battle formats — **Quick Battle**, where two random memes go head-to-head, and **Tournament Mode**, where 8 or 16 memes compete in a bracket-style elimination contest. Votes are reflected in real time using WebSocket connections, and a leaderboard tracks the most popular memes across the platform.

The application follows a clean separation of concerns with a RESTful API backend, JWT-based authentication, and role-based access control distinguishing regular users from administrators. Admins can approve or reject tournament submissions before they go live. Uploaded meme images are stored on disk under a Docker volume, so they persist across container restarts.

---

## 🌟 Features

- **User Authentication** — JWT-based registration and login with auto-login after signup
- **Meme Upload** — Upload by image URL or file (stored on a persistent Docker volume)
- **Voting System** — Upvote/downvote with real-time WebSocket updates
- **Leaderboard** — Top memes ranked by vote count
- **Quick Battle** — Head-to-head voting between two random memes
- **Tournament Mode** — Bracket-style tournaments (8 or 16 memes) with admin approval
- **Real-time Updates** — WebSocket/STOMP for live vote counts and bracket updates
- **Role-based Access** — Admin and user roles with protected endpoints
- **User Profiles** — Stats, avatar upload, edit profile
- **Responsive Design** — Works on desktop, tablet, and mobile

---

## 🛠️ Tech Stack

### Frontend
| Technology | Purpose |
|---|---|
| React 19 + Vite | UI framework and build tool |
| Framer Motion | Animations and transitions |
| Axios | HTTP API client (with JWT interceptor) |
| @stomp/stompjs + SockJS | WebSocket client for real-time updates |
| React Router v6 | Client-side routing |
| Vitest + Testing Library | Unit and component tests |

### Backend
| Technology | Purpose |
|---|---|
| Java 21 + Spring Boot 3.5 | Application framework |
| Spring Security + JWT | Authentication and authorization |
| Spring Data JPA + Hibernate | ORM and database access |
| Spring WebSocket + STOMP | Real-time bidirectional communication |
| Spring Validation | Request validation |
| Maven | Build and dependency management |

### Database
| Technology | Purpose |
|---|---|
| PostgreSQL 16 | Production database |
| H2 (in-memory) | Local development database |

### Infrastructure
| Service | Purpose |
|---|---|
| Docker | Containerization for backend, frontend, and database |
| Docker Compose | Local multi-container orchestration (dev and prod-profile smoke tests) |
| nginx | Static file serving + reverse proxy for the frontend container |

---

## ⚙️ How the System Works

### Request Flow

```
User Browser
    │
    ▼
nginx (frontend container, port 3000 → 80)
    │
    ├── /* (React app — static build)
    │       404/403 → index.html (SPA routing)
    │
    ├── /api/* (REST API calls)
    │       └──▶ Backend container (Spring Boot, port 8080)
    │               └──▶ PostgreSQL container (port 5432)
    │
    └── /uploads/* (meme images served by backend)
            └──▶ Backend container → local disk (Docker volume)
```

### Authentication Flow

```
1. User submits registration/login form
2. Frontend POST /api/auth/signup or /api/auth/signin
3. Backend validates credentials, generates JWT (HS512, 24h expiry)
4. JWT returned in response body
5. Frontend stores JWT in localStorage
6. All subsequent API requests include: Authorization: Bearer <token>
7. AuthTokenFilter validates JWT on every request
8. SecurityContext populated with UserPrincipal
```

### Meme Upload Flow

```
1. User selects file or enters image URL
2. Frontend POST /api/memes/upload (multipart) or POST /api/memes (JSON)
3. Backend validates file (type: image/*, size: ≤1MB)
4. Saves to /uploads/ on the backend container's disk (persisted via a Docker volume)
5. Meme record saved to PostgreSQL with imageUrl
6. MemeResponse returned to frontend
```

### Real-time Voting Flow

```
1. User clicks vote button
2. Frontend PUT /api/memes/{id}/vote with JWT
3. Backend updates vote count in PostgreSQL
4. SimpMessagingTemplate broadcasts to /topic/votes (WebSocket)
5. All connected clients receive updated vote count instantly
6. Frontend updates UI without page refresh
```

### Tournament Flow

```
1. User creates tournament (selects 8 or 16 memes, sets round duration)
2. Tournament saved with status PENDING_APPROVAL
3. Admin reviews and approves → status changes to ACTIVE
4. Round 1 matchups generated automatically
5. Users vote on matchups during round duration
6. RoundAdvancementScheduler (runs every minute) checks if round time expired
7. Winners advance, next round matchups generated
8. Final round winner becomes Tournament Champion
9. WebSocket broadcasts bracket updates in real time
```

**Database Tables:**
```
users               — id, username, email, password, role, avatar_url
memes               — id, title, image_url, uploaded_by, vote_count, upload_date
votes               — id, user_id, meme_id, vote_type
battle_pairs        — id, meme_a_id, meme_b_id, created_at
battle_votes        — id, user_id, battle_pair_id, chosen_meme_id
tournaments         — id, name, status, creator_id, round_duration_hours
tournament_matchups — id, tournament_id, meme_a_id, meme_b_id, round_number, winner_id
```

---

## 📁 Project Structure

```
meme-voting-arena/
├── gdg/                              # Spring Boot backend
│   ├── src/main/java/com/meme/gdg/
│   │   ├── config/                   # SecurityConfig, WebSocketConfig, WebConfig
│   │   ├── controller/               # REST endpoints (Auth, Meme, Battle, Tournament)
│   │   ├── dto/                      # Request/response DTOs
│   │   ├── exception/                # GlobalExceptionHandler, custom exceptions
│   │   ├── model/                    # JPA entities (User, Meme, Vote, Tournament...)
│   │   ├── repository/               # Spring Data JPA repositories
│   │   ├── scheduler/                # RoundAdvancementScheduler
│   │   ├── security/                 # JwtUtils, AuthTokenFilter, UserPrincipal
│   │   └── service/                  # Business logic
│   ├── src/main/resources/
│   │   ├── application-dev.properties       # H2 in-memory (local)
│   │   ├── application-docker-dev.properties # PostgreSQL in Docker
│   │   └── application-prod.properties      # PostgreSQL (production)
│   ├── Dockerfile                    # Multi-stage build
│   └── Dockerfile.prebuilt           # Pre-built JAR alternative
│
├── meme-arena-frontend/              # React + Vite frontend
│   ├── src/
│   │   ├── components/               # Battle, Profile, Common components
│   │   ├── contexts/                 # AuthContext, NotificationContext
│   │   ├── pages/                    # All page components
│   │   └── services/api.js           # Axios instance with JWT interceptor
│   ├── .env.production               # VITE_API_URL=<your backend domain>
│   └── .env.development              # VITE_API_URL=http://localhost:8080
│
├── docker-compose.yml                # Local dev (Postgres + Backend + Frontend)
├── docker-compose.prod.yml           # Production-profile reference (local smoke test)
├── .env.dev.example                  # Dev env template
└── .env.prod.example                 # Prod env template
```

---

## 🔌 API Endpoints

### Authentication (public)
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/signup` | Register — returns JWT + user |
| POST | `/api/auth/signin` | Login — returns JWT + user |

### Profile (requires JWT)
| Method | Path | Description |
|---|---|---|
| GET | `/api/auth/profile` | Get own profile + stats |
| PUT | `/api/auth/profile` | Update username / email / password |
| POST | `/api/auth/profile/avatar` | Upload avatar image |

### Memes
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/memes` | Public | Paginated meme list |
| GET | `/api/memes/leaderboard` | Public | Top memes by votes |
| GET | `/api/memes/battle` | Public | Two random memes |
| POST | `/api/memes` | User | Create meme (image URL) |
| POST | `/api/memes/upload` | User | Upload meme (file → local disk) |
| PUT | `/api/memes/{id}/vote` | User | Upvote / downvote |
| DELETE | `/api/memes/{id}` | User/Admin | Delete meme |

### Battle & Tournaments
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/battle/quick/pair` | User | Get a new battle pair |
| POST | `/api/battle/vote/quick` | User | Vote on a battle pair |
| POST | `/api/battle/vote/tournament` | User | Vote on a tournament matchup |
| GET | `/api/battle/tournaments` | Public | List all tournaments |
| GET | `/api/battle/tournaments/{id}` | Public | Tournament bracket details |
| POST | `/api/battle/tournaments` | User | Create tournament |
| GET | `/api/battle/tournaments/my` | User | My tournaments |
| GET | `/api/battle/tournaments/pending` | Admin | Pending approval queue |
| POST | `/api/battle/tournaments/{id}/approve` | Admin | Approve tournament |
| POST | `/api/battle/tournaments/{id}/reject` | Admin | Reject tournament |

### WebSocket Topics
| Topic | Description |
|---|---|
| `/topic/votes` | Real-time vote count updates |
| `/topic/battle/tournament/{id}` | Live bracket updates for a tournament |

---

## 🚀 Getting Started

### Prerequisites
- Java 21+, Maven 3.6+
- Node.js 20+, npm
- Docker + Docker Compose (recommended)

### Run with Docker (recommended)

```bash
# 1. Copy env template
cp .env.dev.example .env.dev

# 2. Start all services (PostgreSQL + Backend + Frontend)
docker compose --env-file .env.dev up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| H2 Console | http://localhost:8080/h2-console (dev profile only) |

### Run without Docker

**Backend (H2 in-memory):**
```bash
cd gdg
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd meme-arena-frontend
npm install
npm run dev
```

### Default Seed Users (dev profile only)

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `memeLord` | `password123` | USER |

---

## 🔧 Configuration

All secrets are injected via environment variables — nothing hardcoded in source.

| Variable | Profile | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | All | `dev`, `docker-dev`, or `prod` |
| `DATABASE_URL` | prod | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | prod | DB username |
| `DATABASE_PASSWORD` | prod | DB password |
| `JWT_SECRET` | prod | Min 256-bit random string |
| `JWT_EXPIRATION_MS` | Optional | Default: 86400000 (24h) |
| `CORS_ORIGINS` | prod | Frontend domain(s) |
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

# Backend — unit tests only
./mvnw test -Dtest="MemeServiceUnitTest,BattleVoteServiceUnitTest,TournamentServiceUnitTest"

# Frontend — all tests
cd meme-arena-frontend && npm test -- --run
```

| Suite | Count | Framework |
|---|---|---|
| Backend unit tests | 70+ | JUnit 5 + Mockito + AssertJ |
| Backend integration tests | 18+ | Spring Boot Test |
| Frontend component tests | 114 | Vitest + Testing Library |

---

## 🔒 Security

- All write endpoints require JWT authentication (`@PreAuthorize("isAuthenticated()")`)
- Admin-only endpoints enforce `ROLE_ADMIN`
- Passwords hashed with BCrypt
- JWT signed with HS512, secret injected via env var
- CORS restricted to configured origins
- File uploads limited to 1 MB, images only
- Database not publicly accessible in production — only reachable from the backend container/network
- No secrets committed to source control

---

## 🐛 Troubleshooting

| Issue | Fix |
|---|---|
| 502/504 from the frontend | Backend container not running or failing its health check — check `docker compose logs backend` |
| Image not found | Confirm the `uploads_data` volume is mounted and hasn't been removed |
| Login/Register 404 on refresh | nginx SPA fallback misconfigured — confirm `nginx.conf` has `try_files $uri $uri/ /index.html` |
| Upload fails | Check the backend container has write access to `/app/uploads` |
| CORS errors | Check `CORS_ORIGINS` matches the frontend origin exactly |
| JWT errors | Verify `JWT_SECRET` is consistent across backend restarts |

---

## 📝 License

MIT License

---

**Built by [Amitosh Biswas](https://github.com/Biswas2200)**
