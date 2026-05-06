# Implementation Plan: Battle Arena

## Overview

This implementation plan builds the Battle Arena feature in a structured, incremental manner. The approach follows a backend-first strategy: data layer → services → controllers → WebSocket integration → frontend components. Each task builds on previous work, with checkpoints to validate progress.

The Battle Arena introduces two competitive voting modes:
- **Quick Battle**: Lightweight, stateless head-to-head voting with auto-advance
- **Tournament Mode**: Structured bracket competitions with admin approval and scheduled round advancement

Both modes leverage the existing JWT authentication, `MemeService`, `MemeRepository`, and STOMP WebSocket infrastructure.

---

## Tasks

### 1. Backend Data Layer — Entities and Repositories

- [x] 1.1 Create `BattlePair` entity and repository
  - Create `BattlePair` entity with `id`, `memeA`, `memeB`, `createdAt` fields
  - Add `@Entity`, `@Table(name = "battle_pairs")` annotations
  - Use `@ManyToOne` for meme relationships with `FetchType.LAZY`
  - Create `BattlePairRepository` interface extending `JpaRepository<BattlePair, Long>`
  - _Requirements: 1.3_

- [x] 1.2 Create `BattleVote` entity and repository
  - Create `BattleVote` entity with `id`, `user`, `battlePair`, `matchup`, `chosenMeme`, `createdAt` fields
  - Add unique constraints: `@UniqueConstraint(columnNames = {"user_id", "battle_pair_id"})` and `@UniqueConstraint(columnNames = {"user_id", "matchup_id"})`
  - Ensure exactly one of `battlePair` or `matchup` is non-null (nullable columns)
  - Create `BattleVoteRepository` with methods: `findByUserIdAndBattlePairId`, `findByUserIdAndMatchupId`, `countByMatchupIdAndChosenMemeId`
  - _Requirements: 2.1, 8.1_

- [x] 1.3 Create `Tournament` entity and repository
  - Create `Tournament` entity with `id`, `name`, `creator`, `status`, `roundDurationHours`, `memeCount`, `currentRound`, `currentRoundEndsAt`, `champion`, `createdAt`, `completedAt` fields
  - Add `TournamentStatus` enum: `PENDING_APPROVAL`, `ACTIVE`, `COMPLETED`, `REJECTED`
  - Add `@OneToMany` relationship to `TournamentMatchup` with `cascade = CascadeType.ALL`
  - Create `TournamentRepository` with methods: `findByStatus`, `findByStatusAndCurrentRoundEndsAtBefore`, `findByCreatorId`
  - _Requirements: 5.6, 6.1, 6.2, 7.4_

- [x] 1.4 Create `TournamentMatchup` entity and repository
  - Create `TournamentMatchup` entity with `id`, `tournament`, `roundNumber`, `bracketPosition`, `memeA`, `memeB`, `winner`, `votesA`, `votesB` fields
  - Add `@ManyToOne` relationship to `Tournament`
  - Add `@OneToMany` relationship to `BattleVote` votes
  - Create `TournamentMatchupRepository` with method: `findByTournamentIdAndRoundNumber`
  - _Requirements: 5.7, 5.8, 7.1_

---

### 2. Backend DTOs and Custom Exceptions

- [x] 2.1 Create Battle Arena DTOs
  - Create `BattlePairResponse` with `pairId`, `memeA`, `memeB` (using `MemeSnapshot` nested DTO)
  - Create `MemeSnapshot` with `id`, `title`, `imageUrl`, `voteCount`
  - Create `BattleVoteResult` with `pairId`/`matchupId`, `memeAVotes`, `memeBVotes`, `chosenMemeId`
  - Create `TournamentCreateRequest` with `name`, `memeIds` (List<Long>), `roundDurationHours`
  - Create `TournamentResponse` with full tournament details including matchups list
  - Create `TournamentSummaryResponse` with `id`, `name`, `creator`, `status`, `memeCount`, `createdAt`
  - Create `MatchupResponse` with `id`, `roundNumber`, `bracketPosition`, `memeA`, `memeB`, `votesA`, `votesB`, `winner`
  - _Requirements: 1.4, 2.4, 5.1_

- [x] 2.2 Create custom exceptions for Battle Arena
  - Create `DuplicateVoteException` extending `RuntimeException`
  - Create `TournamentStateException` extending `RuntimeException`
  - Create `InsufficientMemesException` extending `RuntimeException`
  - Add exception mappings to `GlobalExceptionHandler`: `DuplicateVoteException` → 409, `TournamentStateException` → 409, `InsufficientMemesException` → 400
  - _Requirements: 1.2, 2.2, 8.2_

---

### 3. Backend Services — Quick Battle

- [x] 3.1 Implement `QuickBattleService`
  - Create `QuickBattleService` interface with `getNewPair()` method
  - Implement service: delegate to `MemeService.getTwoRandomMemes()`
  - Create and persist `BattlePair` entity with the two memes
  - Return `BattlePairResponse` DTO with pair ID and meme snapshots
  - Throw `InsufficientMemesException` if fewer than 2 memes exist
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 3.2 Write property test for `QuickBattleService.getNewPair()`
  - **Property 1: Quick Battle pair contains exactly two distinct memes**
  - **Validates: Requirements 1.1, 1.3**
  - Generate random lists of ≥ 2 memes
  - Assert `memeA.id != memeB.id` in returned pair
  - Run 100 iterations with jqwik

---

### 4. Backend Services — Battle Voting

- [x] 4.1 Implement `BattleVoteService` for Quick Battle voting
  - Create `BattleVoteService` interface with `voteOnPair(userId, battlePairId, chosenMemeId)` method
  - Check for existing vote using `BattleVoteRepository.findByUserIdAndBattlePairId`
  - Throw `DuplicateVoteException` if vote already exists
  - Persist `BattleVote` entity
  - Count votes for both memes in the pair
  - Broadcast vote update to `/topic/battle/quick` using `SimpMessagingTemplate`
  - Return `BattleVoteResult` with updated counts
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 4.2 Write property test for one-vote-per-pair enforcement
  - **Property 2: One-vote-per-pair enforcement**
  - **Validates: Requirements 2.2**
  - Generate random `(userId, battlePairId)` pairs
  - First vote succeeds, second vote throws `DuplicateVoteException`
  - Assert vote counts unchanged after duplicate attempt
  - Run 100 iterations with jqwik

- [x] 4.3 Write property test for vote count consistency
  - **Property 4: Vote count consistency after a Quick Battle vote**
  - **Validates: Requirements 2.3, 2.4**
  - Generate random sequence of votes from distinct users on same pair
  - Assert `votesA + votesB == total BattleVote records` for that pair
  - Run 100 iterations with jqwik

---

### 5. Backend Services — Tournament Creation and Management

- [x] 5.1 Implement `TournamentService.createTournament()`
  - Validate meme count is exactly 8 or 16 (throw `TournamentStateException` otherwise)
  - Validate no duplicate meme IDs (throw `TournamentStateException` if duplicates found)
  - Validate all meme IDs exist in `MemeRepository` (throw `RuntimeException` with 404 message if not found)
  - Validate `roundDurationHours` is 1, 6, or 24
  - Create `Tournament` entity with status `PENDING_APPROVAL`
  - Generate first-round `TournamentMatchup` entities by randomly pairing memes
  - For 8 memes: create 4 matchups (3 total rounds)
  - For 16 memes: create 8 matchups (4 total rounds)
  - Persist tournament and matchups
  - Return `TournamentResponse` DTO
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

- [x] 5.2 Write property test for tournament bracket size invariant
  - **Property 6: Tournament bracket size invariant**
  - **Validates: Requirements 5.7, 5.8**
  - Generate tournament creation requests with `n ∈ {8, 16}`
  - Assert `firstRoundMatchups.size() == n/2`
  - Assert `totalRounds == log₂(n)` (3 for 8 memes, 4 for 16 memes)
  - Run 100 iterations with jqwik

- [x] 5.3 Implement `TournamentService` query methods
  - Implement `getTournament(id)` returning full `TournamentResponse` with all matchups
  - Implement `listTournaments(pageable)` returning `Page<TournamentSummaryResponse>`
  - Implement `getMyTournaments(userId)` returning creator's tournaments
  - Implement `getPendingTournaments()` returning tournaments with `PENDING_APPROVAL` status
  - _Requirements: 6.4, 9.1_

- [x] 5.4 Implement `TournamentService` admin approval methods
  - Implement `approveTournament(id)` checking status is `PENDING_APPROVAL`
  - Throw `TournamentStateException` if status is not `PENDING_APPROVAL`
  - Update status to `ACTIVE`, set `currentRound = 1`, set `currentRoundEndsAt = now + roundDurationHours`
  - Return updated `TournamentResponse`
  - Implement `rejectTournament(id)` checking status is `PENDING_APPROVAL`
  - Update status to `REJECTED`
  - Return updated `TournamentResponse`
  - _Requirements: 6.1, 6.2, 6.5_

- [x] 5.5 Write property test for tournament status transition validity
  - **Property 8: Tournament status transition validity**
  - **Validates: Requirements 6.1, 6.2, 6.5**
  - Generate random sequences of approve/reject operations
  - Assert only valid transitions occur: `PENDING_APPROVAL → ACTIVE`, `PENDING_APPROVAL → REJECTED`, `ACTIVE → COMPLETED`
  - Assert invalid transitions throw `TournamentStateException`
  - Run 100 iterations with jqwik

---

### 6. Backend Services — Tournament Voting

- [x] 6.1 Implement `BattleVoteService.voteOnMatchup()`
  - Validate tournament status is `ACTIVE` (throw `TournamentStateException` otherwise)
  - Validate matchup belongs to current active round (throw `TournamentStateException` otherwise)
  - Check for existing vote using `BattleVoteRepository.findByUserIdAndMatchupId`
  - Throw `DuplicateVoteException` if vote already exists
  - Persist `BattleVote` entity with matchup reference
  - Update `TournamentMatchup.votesA` or `votesB` based on chosen meme
  - Broadcast vote update to `/topic/battle/tournament/{tournamentId}` using `SimpMessagingTemplate`
  - Return `BattleVoteResult` with updated counts
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 6.2 Write property test for one-vote-per-matchup enforcement
  - **Property 3: One-vote-per-matchup enforcement**
  - **Validates: Requirements 8.2**
  - Generate random `(userId, matchupId)` pairs
  - First vote succeeds, second vote throws `DuplicateVoteException`
  - Assert vote counts unchanged after duplicate attempt
  - Run 100 iterations with jqwik

---

### 7. Backend Services — Round Advancement Scheduler

- [x] 7.1 Implement `RoundAdvancementScheduler`
  - Create `@Component` class with `@EnableScheduling`
  - Add `@Scheduled(fixedDelay = 30000)` method `advanceExpiredRounds()`
  - Query `TournamentRepository.findByStatusAndCurrentRoundEndsAtBefore(ACTIVE, now())`
  - For each expired tournament:
    - Fetch current round matchups using `TournamentMatchupRepository.findByTournamentIdAndRoundNumber`
    - Determine winner for each matchup: higher vote count wins; if tied, lower meme ID wins
    - Set `TournamentMatchup.winner` field
    - If not final round: create next round matchups by pairing winners in bracket order
    - If final round: set tournament status to `COMPLETED`, set `champion` field, set `completedAt` timestamp
    - Broadcast round advancement to `/topic/battle/tournament/{tournamentId}`
  - Wrap each tournament in try-catch to prevent one failure from blocking others
  - Log errors at ERROR level with tournament ID
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

- [x] 7.2 Write property test for tiebreaker logic
  - **Property 5: Tiebreaker selects lower meme ID**
  - **Validates: Requirements 7.2**
  - Generate random pairs of memes with equal vote counts
  - Assert winner ID is `min(memeA.id, memeB.id)`
  - Run 100 iterations with jqwik

- [x] 7.3 Write property test for round advancement winner count
  - **Property 7: Round advancement preserves winner count**
  - **Validates: Requirements 7.1, 7.3**
  - Generate random sets of matchups with random vote counts
  - Assert `winners.size() == matchups.size()`
  - Assert each winner is one of the two memes in its matchup
  - Run 100 iterations with jqwik

---

### 8. Backend Controllers and Security Configuration

- [x] 8.1 Create `QuickBattleController`
  - Create `@RestController` with `@RequestMapping("/api/battle/quick")`
  - Add `GET /api/battle/quick/pair` endpoint calling `QuickBattleService.getNewPair()`
  - Require authentication using `@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")`
  - Return `BattlePairResponse`
  - _Requirements: 1.1_

- [x] 8.2 Create `BattleVoteController`
  - Create `@RestController` with `@RequestMapping("/api/battle/vote")`
  - Add `POST /api/battle/vote/quick` endpoint with `@RequestBody` containing `battlePairId` and `chosenMemeId`
  - Extract user ID from `Authentication` principal
  - Call `BattleVoteService.voteOnPair()`
  - Require authentication using `@PreAuthorize("hasRole('USER') or hasRole('ADMIN')")`
  - Add `POST /api/battle/vote/tournament` endpoint with `@RequestBody` containing `matchupId` and `chosenMemeId`
  - Call `BattleVoteService.voteOnMatchup()`
  - Return `BattleVoteResult`
  - _Requirements: 2.1, 8.1_

- [x] 8.3 Create `TournamentController`
  - Create `@RestController` with `@RequestMapping("/api/battle/tournaments")`
  - Add `POST /api/battle/tournaments` endpoint accepting `TournamentCreateRequest`, require authentication
  - Add `GET /api/battle/tournaments` endpoint with pagination, public access
  - Add `GET /api/battle/tournaments/{id}` endpoint returning full bracket, public access
  - Add `GET /api/battle/tournaments/my` endpoint returning creator's tournaments, require authentication
  - Add `GET /api/battle/tournaments/pending` endpoint, require `@PreAuthorize("hasRole('ADMIN')")`
  - Add `POST /api/battle/tournaments/{id}/approve` endpoint, require `@PreAuthorize("hasRole('ADMIN')")`
  - Add `POST /api/battle/tournaments/{id}/reject` endpoint, require `@PreAuthorize("hasRole('ADMIN')")`
  - _Requirements: 5.1, 6.1, 6.2, 6.3, 6.4, 9.1, 10.1_

- [x] 8.4 Update `SecurityConfig` for Battle Arena endpoints
  - Add `/api/battle/tournaments` (GET) to public endpoints in `prodFilterChain`
  - Add `/api/battle/tournaments/{id}` (GET) to public endpoints in `prodFilterChain`
  - All other `/api/battle/**` endpoints require authentication (default `.anyRequest().authenticated()` rule)
  - _Requirements: 10.3_

---

### 9. Checkpoint — Backend Complete

- [x] 9.1 Verify all backend tests pass
  - Run `mvn test` to execute all unit tests and property-based tests
  - Ensure all tests pass, ask the user if questions arise

---

### 10. Frontend — Battle Arena Landing Page and Routing

- [x] 10.1 Replace `BattleArena.jsx` placeholder with landing page
  - Remove "coming soon" content from existing `BattleArena.jsx`
  - Add two main cards: "Quick Battle" and "Tournament Mode"
  - Quick Battle card: shows description, "Start Quick Battle" button
  - Tournament Mode card: shows description, "View Tournaments" and "Create Tournament" buttons
  - Use existing `CommonPages.css` styles for layout
  - Show login prompt for unauthenticated users when clicking voting/creation buttons
  - _Requirements: 11.1, 11.2, 11.3_

- [x] 10.2 Add Battle Arena routes to `App.jsx`
  - Add route `/battle` for `BattleArena` landing page (existing)
  - Add route `/battle/quick` for `QuickBattle` component (auth required)
  - Add route `/battle/tournaments` for `TournamentList` page (public)
  - Add route `/battle/tournaments/new` for `TournamentCreate` page (auth required)
  - Add route `/battle/tournaments/:id` for `TournamentBracket` component (public)
  - Add route `/battle/tournaments/:id/results` for `TournamentResults` page (public)
  - Wrap auth-required routes with `user ? <Component /> : <Navigate to="/login" replace />`
  - _Requirements: 11.1_

---

### 11. Frontend — Quick Battle Component

- [x] 11.1 Create `QuickBattle.jsx` component
  - Create component at `meme-arena-frontend/src/components/battle/QuickBattle.jsx`
  - Fetch initial pair from `GET /api/battle/quick/pair` using `api.js`
  - Display two memes side-by-side with images, titles, and vote counts
  - Add vote buttons for each meme
  - Show loading state while fetching pair
  - Handle `InsufficientMemesException` error (< 2 memes) with user-friendly message
  - _Requirements: 1.1, 1.4, 3.1_

- [x] 11.2 Implement Quick Battle voting logic
  - On vote button click, call `POST /api/battle/vote/quick` with `battlePairId` and `chosenMemeId`
  - Disable voting buttons after vote is cast
  - Display updated vote counts from response
  - Show 1.5 second countdown animation with visual indicator
  - After countdown, automatically fetch next pair from `GET /api/battle/quick/pair`
  - Reset voting controls for new pair
  - _Requirements: 2.1, 2.4, 4.1, 4.2_

- [x] 11.3 Add WebSocket subscription for Quick Battle
  - Install `@stomp/stompjs` and `sockjs-client` if not already present
  - Create STOMP client connecting to `/ws` endpoint
  - Subscribe to `/topic/battle/quick` on component mount
  - Update vote counts in real time when WebSocket message received
  - Clean up subscription on component unmount
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 11.4 Write unit tests for `QuickBattle` component
  - Test: renders pair with two memes
  - Test: disables voting controls after vote
  - Test: shows countdown animation after vote
  - Test: auto-fetches next pair after 1.5 seconds
  - Test: updates vote counts on WebSocket message

---

### 12. Frontend — Tournament List and Create Pages

- [x] 12.1 Create `TournamentList.jsx` page
  - Create page at `meme-arena-frontend/src/pages/TournamentList.jsx`
  - Fetch tournaments from `GET /api/battle/tournaments` with pagination
  - Display tournament cards with name, creator, status badge, meme count, created date
  - Add "Create Tournament" button (auth-gated, navigates to `/battle/tournaments/new`)
  - Add status filter tabs: All, Active, Completed
  - For admin users: add "Pending Approval" tab fetching from `GET /api/battle/tournaments/pending`
  - Each tournament card links to `/battle/tournaments/{id}`
  - _Requirements: 6.4, 11.2_

- [x] 12.2 Create `TournamentCreate.jsx` page
  - Create page at `meme-arena-frontend/src/pages/TournamentCreate.jsx`
  - Fetch meme gallery from `GET /api/memes` for meme picker
  - Display meme grid with multi-select checkboxes
  - Show selected count (must be exactly 8 or 16)
  - Add tournament name input field
  - Add round duration selector: 1 hour, 6 hours, 24 hours (radio buttons)
  - Validate 8 or 16 memes selected before enabling submit button
  - On submit: call `POST /api/battle/tournaments` with `TournamentCreateRequest`
  - Show success message and navigate to tournament list
  - Handle validation errors (duplicate memes, invalid count, missing memes)
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 12.3 Write unit tests for `TournamentCreate` component
  - Test: validates 8 or 16 meme selection
  - Test: disables submit button when invalid count selected
  - Test: submits correct payload to API
  - Test: shows validation errors

---

### 13. Frontend — Tournament Bracket Component

- [x] 13.1 Create `TournamentBracket.jsx` component
  - Create component at `meme-arena-frontend/src/components/battle/TournamentBracket.jsx`
  - Fetch tournament data from `GET /api/battle/tournaments/{id}`
  - Render bracket tree structure using CSS grid/flexbox
  - Display all rounds vertically with matchups in each round
  - Show meme images, titles, and vote counts for each matchup
  - Highlight current active round
  - Show "Winner" badge for completed matchups
  - Display champion prominently at top when tournament is `COMPLETED`
  - _Requirements: 9.1, 9.5, 9.6_

- [x] 13.2 Implement tournament voting in bracket
  - For matchups in current active round: show vote buttons if user is authenticated and hasn't voted
  - On vote button click: call `POST /api/battle/vote/tournament` with `matchupId` and `chosenMemeId`
  - Disable voting buttons after vote is cast for that matchup
  - Update vote counts from response
  - Show "You voted for [meme title]" indicator after voting
  - _Requirements: 8.1, 8.2, 9.4_

- [x] 13.3 Add countdown timer for active round
  - Display time remaining for current round using `currentRoundEndsAt` timestamp
  - Update countdown every second using `setInterval`
  - Show format: "Round ends in: 5h 23m 15s"
  - When timer reaches zero, show "Round ending soon..." message
  - _Requirements: 9.2_

- [x] 13.4 Add polling and WebSocket for bracket updates
  - Poll `GET /api/battle/tournaments/{id}` every 30 seconds to refresh full bracket state
  - Subscribe to `/topic/battle/tournament/{tournamentId}` via STOMP
  - Update vote counts in real time when WebSocket message received
  - Refresh full bracket when round advancement detected (currentRound changed)
  - Clean up polling interval and WebSocket subscription on component unmount
  - _Requirements: 9.3, 9.7_

- [x] 13.5 Write unit tests for `TournamentBracket` component
  - Test: renders all rounds and matchups
  - Test: shows countdown timer for active round
  - Test: displays voting controls for active matchups
  - Test: updates vote counts on WebSocket message
  - Test: highlights champion when tournament completed

---

### 14. Frontend — Tournament Results Page

- [x] 14.1 Create `TournamentResults.jsx` page
  - Create page at `meme-arena-frontend/src/pages/TournamentResults.jsx`
  - Fetch tournament data from `GET /api/battle/tournaments/{id}`
  - Verify tournament status is `COMPLETED` (show error if not)
  - Display full bracket with all rounds, matchups, and final vote counts
  - Highlight champion meme at top with special styling
  - Show tournament metadata: name, creator username, round duration, completion date
  - Make page publicly accessible (no auth required)
  - Reuse bracket rendering logic from `TournamentBracket` component (no voting controls)
  - _Requirements: 10.1, 10.2, 10.3_

- [x] 14.2 Write unit tests for `TournamentResults` page
  - Test: displays full bracket for completed tournament
  - Test: highlights champion
  - Test: shows tournament metadata
  - Test: accessible without authentication

---

### 15. Frontend — Admin Tournament Approval

- [x] 15.1 Add admin approval UI to `TournamentList` page
  - For admin users viewing "Pending Approval" tab: show "Approve" and "Reject" buttons on each tournament card
  - On "Approve" click: call `POST /api/battle/tournaments/{id}/approve`
  - On "Reject" click: call `POST /api/battle/tournaments/{id}/reject`
  - Show confirmation dialog before approve/reject
  - Refresh tournament list after action
  - Show success/error toast notifications
  - _Requirements: 6.1, 6.2, 6.3_

---

### 16. Final Checkpoint — Integration Testing

- [x] 16.1 End-to-end Quick Battle flow test
  - Test: authenticated user requests pair → votes → receives WebSocket broadcast → attempts second vote (409 error)
  - Test: auto-advance to next pair after 1.5 seconds

- [x] 16.2 End-to-end Tournament lifecycle test
  - Test: user creates tournament → admin approves → users vote on matchups → scheduler advances round → tournament completes → results page displays champion

- [x] 16.3 Admin authorization test
  - Test: non-admin user receives 403 when attempting to approve/reject tournament

- [x] 16.4 Public access test
  - Test: unauthenticated user can view tournament list, bracket, and results pages
  - Test: unauthenticated user cannot vote or create tournaments

- [x] 16.5 Final checkpoint — Ensure all tests pass
  - Run backend tests: `mvn test`
  - Run frontend tests: `npm test --run`
  - Ensure all tests pass, ask the user if questions arise

---

## Notes

- Tasks marked with `*` are optional property-based tests and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using jqwik
- Unit tests validate specific examples and edge cases
- The implementation follows a strict backend-first approach to ensure data integrity before building UI
- WebSocket integration is added after core voting logic is complete
- Frontend components are built incrementally: landing → Quick Battle → Tournament list/create → bracket → results
