# Requirements Document

## Introduction

The Automated DB Seeding feature provides an on-demand mechanism to populate the production PostgreSQL database (AWS RDS) with realistic mock data: 100 users and up to 5,000 programming/coding memes sourced from external APIs (meme-api.com and Reddit's JSON API). The seeding process is triggered manually via a secured admin-only REST endpoint — it never runs automatically on container startup in the `prod` profile. The feature must be idempotent (safe to call multiple times), handle deduplication, respect external API rate limits, and produce a realistic vote distribution across seeded memes.

---

## Glossary

- **Seeder**: The Spring Boot service component responsible for orchestrating the full seeding workflow.
- **Admin_Endpoint**: The secured REST controller endpoint (`POST /api/admin/seed`) that triggers the Seeder.
- **Meme_Fetcher**: The component responsible for calling external meme APIs and returning normalized meme data.
- **User_Generator**: The component responsible for generating realistic mock user records.
- **Vote_Distributor**: The component responsible for creating realistic vote records across seeded users and memes.
- **External_Meme_API**: The meme-api.com API (`GET /gimme/ProgrammerHumor/50`) and/or Reddit JSON API (`GET /r/ProgrammerHumor/top.json`), used as sources for meme titles and image URLs.
- **Seed_Result**: A response object summarizing the outcome of a seeding operation (users created, memes fetched, votes created, skipped duplicates, errors).
- **Idempotency_Guard**: The deduplication check that prevents re-inserting records that already exist in the database.
- **Rate_Limiter**: The mechanism that enforces a minimum delay between successive calls to the External_Meme_API.
- **Seeded_User**: A mock user created by the User_Generator, distinct from real registered users.
- **Seeded_Meme**: A meme record whose `imageUrl` stores only the remote image URL (no file download or local storage).

---

## Requirements

### Requirement 1: On-Demand Admin Trigger

**User Story:** As an admin, I want to trigger database seeding via a secured API endpoint, so that I can populate the production database with realistic data without redeploying the application or restarting containers.

#### Acceptance Criteria

1. THE Admin_Endpoint SHALL expose a `POST /api/admin/seed` route that initiates the seeding workflow.
2. WHEN a request is received at `POST /api/admin/seed`, THE Admin_Endpoint SHALL require a valid JWT token belonging to a user with the `ADMIN` role.
3. IF a request to `POST /api/admin/seed` is made without a valid `ADMIN`-role JWT, THEN THE Admin_Endpoint SHALL return HTTP 403.
4. WHEN the seeding workflow completes successfully, THE Admin_Endpoint SHALL return HTTP 200 with a Seed_Result payload.
5. WHEN the seeding workflow is already in progress (concurrent invocation), THE Admin_Endpoint SHALL return HTTP 409 with a message indicating seeding is already running.
6. THE Admin_Endpoint SHALL NOT be active on the `dev` or `docker-dev` profiles (those profiles use the existing `DataInitializer` on startup).

---

### Requirement 2: Idempotency and Deduplication

**User Story:** As an admin, I want the seeding operation to be safe to run multiple times, so that I can re-trigger it without creating duplicate data or corrupting existing records.

#### Acceptance Criteria

1. BEFORE inserting a user, THE Idempotency_Guard SHALL check whether a user with the same `username` or `email` already exists in the database.
2. IF a user with the same `username` or `email` already exists, THEN THE Idempotency_Guard SHALL skip that user and increment the skipped-users counter in the Seed_Result.
3. BEFORE inserting a meme, THE Idempotency_Guard SHALL check whether a meme with the same `imageUrl` already exists in the database.
4. IF a meme with the same `imageUrl` already exists, THEN THE Idempotency_Guard SHALL skip that meme and increment the skipped-memes counter in the Seed_Result.
5. THE Seeder SHALL complete without error even when all generated users and fetched memes are duplicates of existing records.
6. THE Seed_Result SHALL include separate counts for `usersCreated`, `usersSkipped`, `memesCreated`, `memesSkipped`, and `votesCreated`.

---

### Requirement 3: Mock User Generation

**User Story:** As an admin, I want the seeder to create 100 realistic mock users, so that the application has a believable user base for testing and demonstration.

#### Acceptance Criteria

1. WHEN seeding is triggered, THE User_Generator SHALL attempt to create 100 Seeded_Users.
2. THE User_Generator SHALL assign each Seeded_User a unique `username` composed of a programming-themed adjective, a noun, and a random numeric suffix (e.g., `lazyCoder42`, `asyncNinja7`).
3. THE User_Generator SHALL assign each Seeded_User a unique `email` derived from the generated `username` (e.g., `lazycoder42@devmail.io`).
4. THE User_Generator SHALL assign each Seeded_User a BCrypt-hashed password using the application's configured `PasswordEncoder`.
5. THE User_Generator SHALL assign all Seeded_Users the `USER` role.
6. THE User_Generator SHALL assign each Seeded_User a `avatarUrl` pointing to a deterministic placeholder avatar service URL (e.g., `https://api.dicebear.com/7.x/pixel-art/svg?seed=<username>`).
7. WHERE the target count of 100 users already exists in the database (all skipped by the Idempotency_Guard), THE User_Generator SHALL log a warning and proceed to meme fetching without error.

---

### Requirement 4: External Meme Fetching

**User Story:** As an admin, I want the seeder to fetch up to 5,000 real programming memes from external APIs, so that the application has authentic meme content for voting and battle features.

#### Acceptance Criteria

1. WHEN seeding is triggered, THE Meme_Fetcher SHALL call the External_Meme_API to retrieve meme data (title and image URL).
2. THE Meme_Fetcher SHALL use the meme-api.com endpoint `GET https://meme-api.com/gimme/ProgrammerHumor/50` as the primary source, returning up to 50 memes per call.
3. WHERE the meme-api.com source returns fewer memes than needed, THE Meme_Fetcher SHALL also call the Reddit JSON API `GET https://www.reddit.com/r/ProgrammerHumor/top.json` as a secondary source.
4. THE Meme_Fetcher SHALL make repeated paginated calls to accumulate up to 5,000 unique meme URLs.
5. THE Meme_Fetcher SHALL store only the remote image URL in the `imageUrl` field — it SHALL NOT download or store image file bytes.
6. THE Meme_Fetcher SHALL filter out any meme entries where the image URL is null, empty, or does not end with a recognized image extension (`.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`).
7. WHEN a call to the External_Meme_API fails with a network error or non-2xx HTTP response, THE Meme_Fetcher SHALL log the error, skip that batch, and continue fetching from the next available call.
8. THE Rate_Limiter SHALL enforce a minimum delay of 500 milliseconds between successive calls to the same External_Meme_API host.
9. IF the External_Meme_API returns HTTP 429 (Too Many Requests), THEN THE Rate_Limiter SHALL wait 5 seconds before retrying the same request, up to a maximum of 3 retries.
10. WHEN the total number of unique meme URLs fetched reaches 5,000 or no further pages are available, THE Meme_Fetcher SHALL stop making additional API calls.

---

### Requirement 5: Meme Record Persistence

**User Story:** As an admin, I want fetched memes to be saved to the database with realistic metadata, so that they appear as authentic content uploaded by the seeded users.

#### Acceptance Criteria

1. WHEN a fetched meme passes the Idempotency_Guard check, THE Seeder SHALL persist a `Meme` record with the fetched `title` and `imageUrl`.
2. THE Seeder SHALL assign each Seeded_Meme a random Seeded_User as the `uploadedBy` owner, selected uniformly from the set of successfully created or pre-existing Seeded_Users.
3. THE Seeder SHALL set the `voteCount` field to 0 at insertion time; the Vote_Distributor will update it after votes are created.
4. THE Seeder SHALL persist memes in batches of 100 records per database transaction to limit memory usage and transaction size.

---

### Requirement 6: Realistic Vote Distribution

**User Story:** As an admin, I want the seeder to create a realistic distribution of votes across memes and users, so that the leaderboard and battle features have meaningful data to display.

#### Acceptance Criteria

1. WHEN meme persistence is complete, THE Vote_Distributor SHALL create vote records linking Seeded_Users to Seeded_Memes.
2. THE Vote_Distributor SHALL ensure each Seeded_User votes on between 40% and 80% of the total Seeded_Memes, selected at random.
3. THE Vote_Distributor SHALL assign vote types such that approximately 70% of votes are `UPVOTE` and 30% are `DOWNVOTE`, with per-vote randomness.
4. THE Vote_Distributor SHALL respect the unique constraint on `(user_id, meme_id)` — it SHALL NOT attempt to insert a duplicate vote for a user-meme pair that already exists.
5. AFTER all votes are inserted, THE Vote_Distributor SHALL recalculate and update the `voteCount` field on each affected `Meme` record using the formula `upvotes - downvotes`.
6. THE Vote_Distributor SHALL persist votes in batches of 500 records per database transaction to limit memory usage and transaction size.

---

### Requirement 7: Seed Result Reporting

**User Story:** As an admin, I want the seeding endpoint to return a detailed summary of what was created, so that I can verify the operation completed as expected.

#### Acceptance Criteria

1. WHEN the seeding workflow completes (successfully or partially), THE Admin_Endpoint SHALL return a Seed_Result JSON object.
2. THE Seed_Result SHALL include the following fields: `usersCreated` (integer), `usersSkipped` (integer), `memesCreated` (integer), `memesSkipped` (integer), `votesCreated` (integer), `durationMs` (long), `status` (string: `"COMPLETED"` or `"PARTIAL"`).
3. WHEN at least one meme or user was successfully created, THE Seed_Result SHALL set `status` to `"COMPLETED"`.
4. WHEN no new records were created (all skipped due to deduplication), THE Seed_Result SHALL set `status` to `"PARTIAL"` and include a human-readable `message` field explaining the outcome.
5. IF an unrecoverable error occurs during seeding, THEN THE Admin_Endpoint SHALL return HTTP 500 with a Seed_Result containing `status` set to `"FAILED"` and an `errorMessage` field describing the failure.

---

### Requirement 8: Profile Isolation

**User Story:** As a developer, I want the production seeding feature to be completely isolated from the dev/docker-dev startup seeder, so that the two mechanisms never interfere with each other.

#### Acceptance Criteria

1. THE Seeder SHALL be annotated with `@Profile("prod")` so that it is only instantiated in the production Spring context.
2. THE Admin_Endpoint SHALL be annotated with `@Profile("prod")` so that it is only registered in the production Spring context.
3. WHILE running on the `dev` or `docker-dev` profile, THE existing `DataInitializer` SHALL continue to run on startup without modification.
4. THE Seeder SHALL NOT implement `CommandLineRunner` or any other startup lifecycle interface — it SHALL only execute when explicitly invoked via the Admin_Endpoint.

---

### Requirement 9: Observability and Logging

**User Story:** As an operator, I want the seeding process to emit structured log messages at key stages, so that I can monitor progress and diagnose failures in AWS CloudWatch.

#### Acceptance Criteria

1. WHEN the seeding workflow starts, THE Seeder SHALL log an INFO-level message including the timestamp and the username of the admin who triggered it.
2. WHEN each batch of memes is fetched from the External_Meme_API, THE Meme_Fetcher SHALL log an INFO-level message including the source URL, the number of memes returned, and the running total.
3. WHEN each batch of records is persisted to the database, THE Seeder SHALL log an INFO-level message including the batch number and record count.
4. WHEN the seeding workflow completes, THE Seeder SHALL log an INFO-level message including the final Seed_Result counts and total duration in milliseconds.
5. IF any External_Meme_API call fails, THEN THE Meme_Fetcher SHALL log a WARN-level message including the failed URL and the HTTP status code or exception message.
