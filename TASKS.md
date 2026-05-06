# Meme Voting Arena — Production Readiness Tasks

> **Deployment Target:** AWS (ECS/EC2 backend · RDS PostgreSQL · S3+CloudFront frontend)
> **Status legend:** ✅ Done · 🔄 In Progress · ⬜ Pending

---

## Phase 0 — Cleanup & Dependency Updates ✅

| # | Task | Status |
|---|------|--------|
| 0.1 | Remove Supabase integration (`application-supabase.properties` deleted, credentials purged) | ✅ |
| 0.2 | Rewrite `application.properties` — default profile `dev`, all secrets via env vars | ✅ |
| 0.3 | Rewrite `application-dev.properties` — clean H2 config, no hardcoded secrets | ✅ |
| 0.4 | Rewrite `application-prod.properties` — AWS RDS PostgreSQL, all values from env vars | ✅ |
| 0.5 | Update Spring Boot `3.5.5` → `3.5.9` (latest stable 3.x) | ✅ |
| 0.6 | Update jjwt `0.12.3` → `0.12.6` | ✅ |
| 0.7 | Fix `MemeRepository` — replace MySQL `RAND()` with PostgreSQL `RANDOM()` | ✅ |
| 0.8 | Update frontend deps: `framer-motion` 10→12, `lucide-react` 0.294→0.511, `axios` 1.6→1.9, `socket.io-client` 4.7→4.8, `react-router-dom` 6.20→6.30, `web-vitals` 2→4, `user-event` 13→14, `clsx` 2.0→2.1, `react-hot-toast` 2.4→2.5 | ✅ |

---

## Phase 1 — Security Hardening ⬜

| # | Task | Status |
|---|------|--------|
| 1.1 | Re-enable `@PreAuthorize` on all `MemeController` endpoints | ⬜ |
| 1.2 | Remove all debug endpoints from `AuthController` (`/debug/users`, `/debug/validate-token`, `/debug/generate-token`) | ⬜ |
| 1.3 | Restore real JWT auth in `AuthContext.jsx` — remove mock user, re-enable `Authorization` header | ⬜ |
| 1.4 | Restore proper voting auth in `MemeService.voteMeme` — enforce one-vote-per-user via `VoteRepository` | ⬜ |
| 1.5 | Restrict `SecurityConfig` — only public endpoints (`/api/auth/**`, `GET /api/memes`, `GET /api/memes/leaderboard`) should be permitAll | ⬜ |
| 1.6 | Add rate limiting (Spring Boot `bucket4j` or AWS WAF) on auth endpoints | ⬜ |
| 1.7 | Enforce HTTPS-only CORS origins in prod (no `http://`) | ⬜ |
| 1.8 | Add `@Valid` + input sanitisation on all controller endpoints | ⬜ |
| 1.9 | Rotate default admin password — remove hardcoded `admin123` from `DataInitializer` | ⬜ |

---

## Phase 2 — Backend Feature Completion ⬜

| # | Task | Status |
|---|------|--------|
| 2.1 | Implement full Battle Arena logic — `GET /api/memes/battle` returns 2 random memes, `POST /api/memes/battle/{id}/vote` records battle vote | ⬜ |
| 2.2 | Implement User Profile endpoint — `GET /api/auth/profile` returns uploaded memes, vote history, keyword | ⬜ |
| 2.3 | Implement meme search endpoint — `GET /api/memes/search?q=` | ⬜ |
| 2.4 | Replace local `uploads/` file storage with **AWS S3** (presigned URL upload flow) | ⬜ |
| 2.5 | Add `GET /api/stats` endpoint for real homepage stats (total memes, users, votes) | ⬜ |
| 2.6 | Wire WebSocket real-time vote updates end-to-end (backend already configured, frontend needs `useWebSocket` hook) | ⬜ |
| 2.7 | Add pagination metadata to leaderboard (`GET /api/memes/leaderboard?page=&size=`) | ⬜ |
| 2.8 | Add `spring-boot-starter-actuator` health endpoint for AWS ALB health checks | ⬜ |

---

## Phase 3 — Frontend Feature Completion ⬜

| # | Task | Status |
|---|------|--------|
| 3.1 | Replace hardcoded `http://localhost:8080` with `REACT_APP_API_URL` env variable across all pages | ⬜ |
| 3.2 | Create `src/services/api.js` — centralised Axios instance with base URL + auth interceptor | ⬜ |
| 3.3 | Implement Battle Arena page — fetch 2 memes, vote UI, next battle button | ⬜ |
| 3.4 | Implement Profile page — user stats, uploaded memes grid, vote history | ⬜ |
| 3.5 | Wire real homepage stats from `GET /api/stats` (remove hardcoded numbers) | ⬜ |
| 3.6 | Add `useWebSocket` hook for real-time vote count updates in Gallery and Leaderboard | ⬜ |
| 3.7 | Add `REACT_APP_API_URL` to `.env.development` and `.env.production` | ⬜ |
| 3.8 | Add error boundary component for graceful crash handling | ⬜ |
| 3.9 | Update `framer-motion` imports from `framer-motion` → `motion/react` (v12 recommended path) | ⬜ |

---

## Phase 4 — AWS Infrastructure Setup ⬜

| # | Task | Status |
|---|------|--------|
| 4.1 | Provision **AWS RDS** PostgreSQL instance (db.t3.micro, Multi-AZ for prod) | ⬜ |
| 4.2 | Create **S3 bucket** for meme image uploads (private, CORS policy for frontend domain) | ⬜ |
| 4.3 | Create **CloudFront** distribution in front of S3 for frontend CDN delivery | ⬜ |
| 4.4 | Set up **ECR** repository and push backend Docker image | ⬜ |
| 4.5 | Create **ECS Fargate** cluster + task definition + service for backend | ⬜ |
| 4.6 | Configure **Application Load Balancer** (ALB) with HTTPS listener + ACM certificate | ⬜ |
| 4.7 | Set up **VPC** with public/private subnets — backend in private subnet, RDS in isolated subnet | ⬜ |
| 4.8 | Store all secrets in **AWS Secrets Manager** or **Parameter Store** (JWT_SECRET, DB creds) | ⬜ |
| 4.9 | Configure **Security Groups** — ALB → ECS (8080), ECS → RDS (5432) only | ⬜ |
| 4.10 | Deploy frontend to **S3 + CloudFront** (or AWS Amplify) | ⬜ |
| 4.11 | Set up **Route 53** DNS records pointing to ALB and CloudFront | ⬜ |
| 4.12 | Enable **CloudWatch** log groups for ECS task logs | ⬜ |

---

## Phase 5 — CI/CD Pipeline ⬜

| # | Task | Status |
|---|------|--------|
| 5.1 | Create `Dockerfile` for Spring Boot backend (multi-stage build, non-root user) | ⬜ |
| 5.2 | Create `Dockerfile` for React frontend (nginx-based, production build) | ⬜ |
| 5.3 | Create `docker-compose.yml` for local full-stack development | ⬜ |
| 5.4 | Set up **GitHub Actions** workflow — test → build → push to ECR → deploy to ECS | ⬜ |
| 5.5 | Add frontend build + S3 sync step to CI/CD pipeline | ⬜ |
| 5.6 | Add `mvn test` gate — block deploy on test failure | ⬜ |
| 5.7 | Configure environment-specific GitHub Secrets (prod vs staging) | ⬜ |

---

## Phase 6 — Testing ⬜

| # | Task | Status |
|---|------|--------|
| 6.1 | Write unit tests for `MemeService` (vote logic, create, delete) | ⬜ |
| 6.2 | Write unit tests for `AuthController` (signup, signin, duplicate user) | ⬜ |
| 6.3 | Write integration tests with `@SpringBootTest` + H2 for full request/response cycle | ⬜ |
| 6.4 | Write frontend component tests for `MemeGallery`, `LoginPage`, `Navbar` | ⬜ |
| 6.5 | Add `DataInitializer` guard — skip seeding in prod profile | ⬜ |

---

## Phase 7 — Observability & Performance ⬜

| # | Task | Status |
|---|------|--------|
| 7.1 | Add structured JSON logging (Logback + `logstash-logback-encoder`) | ⬜ |
| 7.2 | Integrate **AWS CloudWatch** metrics via Micrometer | ⬜ |
| 7.3 | Add database query caching for leaderboard (`@Cacheable` + Redis or in-memory) | ⬜ |
| 7.4 | Enable gzip compression on backend responses | ⬜ |
| 7.5 | Add image lazy loading and skeleton loaders in frontend | ⬜ |
| 7.6 | Set up **AWS CloudWatch Alarms** for error rate and latency thresholds | ⬜ |

---

## Notes

- **Never commit** `.env`, `application-prod.properties` with real values, or any AWS credentials.
- All production secrets must live in **AWS Secrets Manager** and be injected as environment variables into ECS task definitions.
- The `DataInitializer` component seeds sample data only when the `users` table is empty — add a profile guard (`@Profile("!prod")`) before going live.
- `framer-motion` v12 recommends importing from `motion/react` instead of `framer-motion` — update imports when upgrading.
