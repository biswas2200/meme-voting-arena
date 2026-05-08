# 🔥 Meme Arena

A full-stack meme battle platform where users upload memes, vote on them in real time, and compete in bracket-style tournaments. Built with **React** frontend and **Spring Boot** backend, deployed on **AWS**.

> **Author:** [Amitosh Biswas](https://github.com/Biswas2200)  
> **Live:** https://d1i3pilqzxap5e.cloudfront.net  
> **GitHub:** https://github.com/biswas2200/meme-voting-arena

---

## 📋 Table of Contents

- [Introduction](#-introduction)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [How the System Works](#-how-the-system-works)
- [AWS Infrastructure](#-aws-infrastructure)
- [Infrastructure Components](#-infrastructure-components)
- [Project Structure](#-project-structure)
- [API Endpoints](#-api-endpoints)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Testing](#-testing)
- [Security](#-security)
- [Troubleshooting](#-troubleshooting)

---

## 📖 Introduction

Meme Arena is a full-stack web application that brings competitive fun to internet culture by letting users upload, vote on, and battle memes in real time. The platform combines a modern React frontend with a robust Spring Boot backend, deployed on AWS infrastructure, to deliver a seamless and engaging experience for meme enthusiasts.

Users can register an account, upload memes either by URL or file, and participate in two battle formats — **Quick Battle**, where two random memes go head-to-head, and **Tournament Mode**, where 8 or 16 memes compete in a bracket-style elimination contest. Votes are reflected in real time using WebSocket connections, and a leaderboard tracks the most popular memes across the platform.

The application follows a clean separation of concerns with a RESTful API backend, JWT-based authentication, and role-based access control distinguishing regular users from administrators. Admins can approve or reject tournament submissions before they go live. Uploaded meme images are stored permanently in Amazon S3, ensuring they survive container restarts and deployments.

---

## 🌟 Features

- **User Authentication** — JWT-based registration and login with auto-login after signup
- **Meme Upload** — Upload by image URL or file (stored permanently in S3)
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
| AWS SDK v2 (S3) | Meme image file storage |
| Maven | Build and dependency management |

### Database
| Technology | Purpose |
|---|---|
| PostgreSQL 16 (AWS RDS) | Production database |
| H2 (in-memory) | Local development database |

### Infrastructure
| Service | Purpose |
|---|---|
| AWS ECS Fargate | Serverless container hosting (backend) |
| AWS S3 | Frontend static hosting + meme image storage |
| AWS CloudFront | CDN, HTTPS, API routing |
| AWS ALB | Load balancer for backend |
| AWS RDS | Managed PostgreSQL |
| AWS ECR | Docker image registry |
| AWS IAM | Roles and permissions |
| AWS CloudWatch | Logs and monitoring |
| Docker | Containerization |
| GitHub Actions | CI/CD pipeline |

---

## ⚙️ How the System Works

### Request Flow (Production)

```
User Browser
    │
    │  HTTPS (all traffic)
    ▼
Amazon CloudFront  (d1i3pilqzxap5e.cloudfront.net)
    │
    ├── /* (React app)
    │       └──▶ S3 Bucket (meme-arena-frontend)
    │               React static files (index.html, JS, CSS)
    │               404/403 → index.html (SPA routing)
    │
    ├── /api/* (REST API calls)
    │       └──▶ ALB (meme-arena-alb, port 80)
    │               └──▶ ECS Fargate Task (Spring Boot, port 8080)
    │                       └──▶ RDS PostgreSQL (port 5432)
    │
    └── /uploads/* (meme images served by backend)
            └──▶ ALB → ECS Fargate Task
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
4. If S3 configured:
     └── S3UploadService.upload() → PutObject to meme-arena-uploads bucket
     └── Returns permanent public URL: https://meme-arena-uploads-*.s3.*.amazonaws.com/uploads/uuid.ext
5. If S3 not configured (local dev):
     └── Saves to /uploads/ on container disk
6. Meme record saved to PostgreSQL with imageUrl
7. MemeResponse returned to frontend
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

### CI/CD Deployment Flow

```
git push origin main
    │
    ▼
GitHub Actions
    ├── Backend Job:
    │   ├── Build JAR (Maven, skip tests)
    │   ├── Build Docker image
    │   ├── Push to ECR
    │   ├── Deploy to ECS (force new deployment)
    │   ├── Wait 90s for new task to start
    │   └── Re-register new task IP with ALB target group
    │
    └── Frontend Job:
        ├── npm ci + npm run build (VITE_API_URL=CloudFront domain)
        ├── aws s3 sync build/ → S3 frontend bucket
        └── CloudFront cache invalidation /*
```

---

## ☁️ AWS Infrastructure

### Infrastructure Diagram

<img width="3714" height="2802" alt="infrastructure-composer-template yaml (5)" src="https://github.com/user-attachments/assets/b3f21deb-f11a-4cca-9563-21ea7c41a023" />

---

## 🏗️ Infrastructure Components

### Networking
| Component | Details |
|---|---|
| **VPC** | `vpc-02134a0c30903a40c` — isolated network for all resources |
| **Public Subnets** | 3 subnets across AZs — ALB and ECS tasks |
| **Private Subnets** | 2 subnets — RDS (not publicly accessible) |
| **Internet Gateway** | Outbound internet access for public subnets |
| **Route Tables** | Public route: 0.0.0.0/0 → Internet Gateway |

### Security Groups
| Group | Inbound Rules | Purpose |
|---|---|---|
| `meme-arena-alb-sg` | 80, 443 from 0.0.0.0/0 | ALB accepts public traffic |
| `meme-arena-ecs-sg` | 8080 from ALB SG only | ECS only reachable via ALB |
| `meme-arena-rds-sg` | 5432 from ECS SG only | RDS only reachable from ECS |

### Compute — ECS Fargate
| Property | Value |
|---|---|
| **Cluster** | `meme-cluster` |
| **Service** | `meme-arena-service` (desired count: 1) |
| **Task Definition** | `meme-arena-backend:3` |
| **CPU / Memory** | 0.5 vCPU / 1 GB RAM |
| **Image** | `562273658670.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend:latest` |
| **Port** | 8080 |
| **Health Check** | `GET /actuator/health` every 30s |
| **Logs** | CloudWatch `/ecs/meme-arena-backend` |

### Load Balancer — ALB
| Property | Value |
|---|---|
| **Name** | `meme-arena-alb` |
| **DNS** | `meme-arena-alb-2075408473.ap-south-1.elb.amazonaws.com` |
| **Scheme** | Internet-facing |
| **Listener** | HTTP port 80 → Target Group |
| **Target Group** | `meme-arena-tg` — IP type, port 8080 |
| **Health Check** | `GET /actuator/health` — 2 healthy / 2 unhealthy threshold |

### CDN — CloudFront
| Property | Value |
|---|---|
| **Distribution ID** | `E331210BH0NF20` |
| **Domain** | `d1i3pilqzxap5e.cloudfront.net` |
| **Default Origin** | S3 frontend bucket (React app) |
| **`/api/*` behavior** | → ALB (no cache, all HTTP methods) |
| **`/uploads/*` behavior** | → ALB (no cache, GET only) |
| **Error handling** | 403 + 404 → `/index.html` (SPA routing) |
| **Protocol** | HTTPS only (redirect HTTP) |

### Database — RDS PostgreSQL
| Property | Value |
|---|---|
| **Identifier** | `meme-arena-db` |
| **Endpoint** | `meme-arena-db.cv6gcmoks21t.ap-south-1.rds.amazonaws.com` |
| **Engine** | PostgreSQL 16 |
| **Instance** | `db.t3.micro` |
| **Storage** | 20 GB gp2 |
| **Database** | `meme_arena` |
| **Access** | Private only (via ECS security group) |

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

### Storage — S3
| Bucket | Purpose | Access |
|---|---|---|
| `meme-arena-frontend-562273658670` | React static build files | Public read via CloudFront |
| `meme-arena-uploads-562273658670` | Meme image files | Public read (direct URL) |

### Container Registry — ECR
| Property | Value |
|---|---|
| **Repository** | `562273658670.dkr.ecr.ap-south-1.amazonaws.com/meme-arena-backend` |
| **Tags** | `latest` + commit SHA on each deploy |

### IAM Roles
| Role | Permissions | Used By |
|---|---|---|
| `ecsTaskExecutionRole` | Pull from ECR, write CloudWatch logs | ECS task startup |
| `meme-arena-ecs-task-role` | `s3:PutObject`, `s3:GetObject` on uploads bucket | Spring Boot app |

### Observability
| Service | Details |
|---|---|
| **CloudWatch Logs** | `/ecs/meme-arena-backend` — 7 day retention |
| **ALB Access Logs** | Target health, request counts |
| **ECS Health Checks** | Container-level via `/actuator/health` |

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
│   │   └── service/                  # Business logic + S3UploadService
│   ├── src/main/resources/
│   │   ├── application-dev.properties       # H2 in-memory (local)
│   │   ├── application-docker-dev.properties # PostgreSQL in Docker
│   │   └── application-prod.properties      # AWS RDS (production)
│   ├── Dockerfile                    # Multi-stage build
│   └── Dockerfile.prebuilt           # Pre-built JAR (used in CI/CD)
│
├── meme-arena-frontend/              # React + Vite frontend
│   ├── src/
│   │   ├── components/               # Battle, Profile, Common components
│   │   ├── contexts/                 # AuthContext, NotificationContext
│   │   ├── pages/                    # All page components
│   │   └── services/api.js           # Axios instance with JWT interceptor
│   ├── .env.production               # VITE_API_URL=https://cloudfront-domain
│   └── .env.development              # VITE_API_URL=http://localhost:8080
│
├── .github/workflows/deploy.yml      # GitHub Actions CI/CD
├── infrastructure.yaml               # CloudFormation template (full AWS stack)
├── docker-compose.yml                # Local dev (Postgres + Backend + Frontend)
├── docker-compose.prod.yml           # Production reference
├── .env.dev.example                  # Dev env template
├── .env.prod.example                 # Prod env template
└── DEPLOYMENT.md                     # Step-by-step AWS deployment guide
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
| POST | `/api/memes/upload` | User | Upload meme (file → S3) |
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
| `S3_BUCKET` | prod | S3 bucket name for image uploads |
| `AWS_REGION` | prod | AWS region (default: ap-south-1) |
| `VITE_API_URL` | Frontend | Backend API base URL |

Generate a secure JWT secret:
```bash
openssl rand -hex 32
```

---

## 🔄 CI/CD Pipeline

Every push to `main` triggers the GitHub Actions workflow (`.github/workflows/deploy.yml`):

```
Push to main
    │
    ├── Backend Job
    │   ├── Checkout + Configure AWS credentials
    │   ├── Set up JDK 21
    │   ├── ./mvnw clean package -DskipTests
    │   ├── docker build -f Dockerfile.prebuilt
    │   ├── docker push → ECR
    │   ├── aws ecs update-service --force-new-deployment
    │   ├── sleep 90 (wait for new task to start)
    │   └── Re-register new task IP with ALB target group
    │
    └── Frontend Job
        ├── npm ci
        ├── npm run build (VITE_API_URL=CloudFront domain)
        ├── aws s3 sync build/ → S3
        └── CloudFront invalidation /*
```

**Required GitHub Secrets:**
| Secret | Description |
|---|---|
| `AWS_ACCESS_KEY_ID` | IAM user access key |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret key |

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
- RDS not publicly accessible — only reachable from ECS security group
- No secrets committed to source control

---

## 🐛 Troubleshooting

| Issue | Fix |
|---|---|
| 502 Bad Gateway | ECS task IP changed — re-register with ALB target group |
| 504 Gateway Timeout | Backend not running or health check failing — check ECS logs |
| Image not found | Old meme uploaded before S3 fix — delete and re-upload |
| Login/Register 404 | CloudFront 404 error rule not set — add 404→index.html custom error |
| Upload fails | Check `S3_BUCKET` env var is set on ECS task definition |
| CORS errors | Check `CORS_ORIGINS` matches frontend domain exactly |
| JWT errors | Verify `JWT_SECRET` is consistent across deployments |

---

## 📝 License

MIT License

---

**Built by [Amitosh Biswas](https://github.com/Biswas2200)**
