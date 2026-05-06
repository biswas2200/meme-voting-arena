# Design Document: Battle Arena

## Overview

The Battle Arena feature adds two competitive voting modes to the Meme Voting Arena application: **Quick Battle** and **Tournament Mode**.

**Quick Battle** is a lightweight, stateless head-to-head vote. The backend selects two random memes (reusing the existing `MemeService.getTwoRandomMemes()`), assigns a unique `BattlePair` identifier, and the frontend auto-advances to the next pair 1.5 seconds after the user votes. Vote results are broadcast in real time over the existing STOMP WebSocket.

**Tournament Mode** is a structured bracket competition. Any authenticated user creates a tournament by selecting 8 or 16 memes. An admin approves it, which starts the first round timer. A `@Scheduled` component advances rounds automatically when timers expire. The bracket is publicly viewable; voting requires authentication.

Both modes are built on top of the existing JWT auth, `MemeRepository`, `MemeService`, and STOMP broker at `/ws`. No new notification infrastructure is introduced — rejection status is surfaced through the tournament list endpoint.

---

## Architecture

```mermaid
graph TD
    subgraph Frontend
        BA[BattleArena Landing Page]
        QB[QuickBattle Component]
        TL[TournamentList Page]
        TC[TournamentCreate Page]
        TBR[TournamentBracket Component]
        TR[TournamentResults Page]
    end

    subgraph Backend REST
        QBC[QuickBattleController\n/api/battle/quick]
        TC2[TournamentController\n/api/battle/tournaments]
        BVC[BattleVoteController\n/api/battle/vote]
    end

    subgraph Backend Services
        QBS[QuickBattleService]
        TS[TournamentService]
        BVS[BattleVoteService]
        RAS[RoundAdvancementScheduler]
        MS[MemeService - existing]
    end

    subgraph WebSocket
        WS[STOMP /ws]
        QT[/topic/battle/quick]
        TT[/topic/battle/tournament/{id}]
    end

    subgraph Database
        BP[battle_pairs]
        BV[battle_votes]
        TN[tournaments]
        TM[tournament_matchups]
    end

    BA --> QB
    BA --> TL
    TL --> TC
    TL --> TBR
    TBR --> TR

    QB --> QBC
    QB --> WS
    TBR --> TC2
    TBR --> WS

    QBC --> QBS
    TC2 --> TS
    BVC --> BVS

    QBS --> MS
    QBS --> BP
    BVS --> BV
    BVS --> QT
    BVS --> TT
    TS --> TN
    TS --> TM
    RAS --> TS
    RAS --> TT
```

### Key Design Decisions

1. **Reuse `MemeService.getTwoRandomMemes()`** — Quick Battle delegates pair selection to the existing service method rather than duplicating the shuffle logic. `QuickBattleService` wraps it and persists the resulting `BattlePair`.

2. **Separate `BattleVote` entity from `Vote`** — The existing `Vote` entity has a unique constraint on `(user_id, meme_id)` and is tied to the general voting system. Battle votes need a different uniqueness scope (`(user_id, battle_pair_id)` or `(user_id, matchup_id)`), so a dedicated `BattleVote` table is cleaner and avoids polluting the existing vote counts.

3. **`@Scheduled` for round advancement** — Spring's `@EnableScheduling` + `@Scheduled(fixedDelay = 30000)` polls for rounds whose `endsAt` timestamp has passed. This is simpler than a distributed timer and sufficient given the requirement of "within 60 seconds of expiry."

4. **Tiebreaker by lower meme ID** — Deterministic, requires no extra data, and is easy to test as a property.

5. **Tournament bracket generation at creation time** — All `TournamentMatchup` rows for round 1 are created immediately on tournament creation. Subsequent rounds' matchups are created by the scheduler when it advances rounds. This keeps the bracket structure in the DB and makes the bracket UI a simple read.

6. **No new notification system** — Rejection is stored as `REJECTED` status on the `Tournament` entity. The creator's tournament list endpoint returns this status, satisfying the requirement without new infrastructure.

---

## Components and Interfaces

### Backend Components

#### `QuickBattleController` — `/api/battle/quick`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/battle/quick/pair` | Required | Returns a new random `BattlePair` |

#### `BattleVoteController` — `/api/battle/vote`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/battle/vote/quick` | Required | Cast a vote on a Quick Battle pair |
| POST | `/api/battle/vote/tournament` | Required | Cast a vote on a tournament matchup |

#### `TournamentController` — `/api/battle/tournaments`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/battle/tournaments` | Required | Create a new tournament |
| GET | `/api/battle/tournaments` | Public | List all tournaments (paginated) |
| GET | `/api/battle/tournaments/{id}` | Public | Get full bracket for a tournament |
| GET | `/api/battle/tournaments/my` | Required | Get creator's own tournaments |
| GET | `/api/battle/tournaments/pending` | ADMIN | List `PENDING_APPROVAL` tournaments |
| POST | `/api/battle/tournaments/{id}/approve` | ADMIN | Approve a tournament |
| POST | `/api/battle/tournaments/{id}/reject` | ADMIN | Reject a tournament |

#### `QuickBattleService`

```java
public interface QuickBattleService {
    BattlePairResponse getNewPair();
}
```

Delegates to `MemeService.getTwoRandomMemes()`, creates and persists a `BattlePair`, and returns the DTO.

#### `BattleVoteService`

```java
public interface BattleVoteService {
    BattleVoteResult voteOnPair(Long userId, Long battlePairId, Long chosenMemeId);
    BattleVoteResult voteOnMatchup(Long userId, Long matchupId, Long chosenMemeId);
}
```

Validates uniqueness, persists `BattleVote`, broadcasts via `SimpMessagingTemplate`, and returns updated counts.

#### `TournamentService`

```java
public interface TournamentService {
    TournamentResponse createTournament(Long creatorId, TournamentCreateRequest request);
    TournamentResponse getTournament(Long id);
    Page<TournamentSummaryResponse> listTournaments(Pageable pageable);
    List<TournamentSummaryResponse> getMyTournaments(Long userId);
    List<TournamentSummaryResponse> getPendingTournaments();
    TournamentResponse approveTournament(Long id);
    TournamentResponse rejectTournament(Long id);
}
```

#### `RoundAdvancementScheduler`

```java
@Component
@EnableScheduling
public class RoundAdvancementScheduler {
    @Scheduled(fixedDelay = 30_000)
    public void advanceExpiredRounds() { ... }
}
```

Queries for active tournaments with `currentRoundEndsAt < now()`, determines winners (higher vote count; lower ID on tie), creates next-round matchups or marks tournament `COMPLETED`.

### Frontend Components

#### `BattleArena` (landing page — `/battle`)
Replaces the existing placeholder. Shows two cards: "Quick Battle" and "Tournament Mode". Unauthenticated users see both options but are prompted to log in before voting or creating.

#### `QuickBattle` component
- Fetches a pair from `GET /api/battle/quick/pair`
- Subscribes to `/topic/battle/quick` via STOMP
- On vote: calls `POST /api/battle/vote/quick`, shows 1.5 s countdown animation, then fetches next pair
- Disables voting controls after the user has voted on the current pair

#### `TournamentList` page (`/battle/tournaments`)
- Lists all tournaments with status badges
- "Create Tournament" button (auth-gated)
- Admin users see a "Pending Approval" tab

#### `TournamentCreate` page (`/battle/tournaments/new`)
- Meme picker (grid from gallery, multi-select, 8 or 16 required)
- Tournament name input
- Round duration selector (1h / 6h / 24h)

#### `TournamentBracket` component (`/battle/tournaments/:id`)
- Renders bracket tree using CSS grid / flexbox
- Polls `GET /api/battle/tournaments/{id}` every 30 s
- Subscribes to `/topic/battle/tournament/{id}` for live vote count updates
- Shows countdown timer for active round
- Voting controls on active matchups for authenticated users

#### `TournamentResults` page (`/battle/tournaments/:id/results`)
- Publicly accessible
- Full bracket with all final vote counts and champion highlighted

---

## Data Models

### `BattlePair` entity

```java
@Entity
@Table(name = "battle_pairs")
public class BattlePair {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_a_id", nullable = false)
    private Meme memeA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_b_id", nullable = false)
    private Meme memeB;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### `BattleVote` entity

```java
@Entity
@Table(name = "battle_votes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "battle_pair_id"}),
    @UniqueConstraint(columnNames = {"user_id", "matchup_id"})
})
public class BattleVote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Exactly one of battle_pair_id or matchup_id is non-null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_pair_id")
    private BattlePair battlePair;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchup_id")
    private TournamentMatchup matchup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chosen_meme_id", nullable = false)
    private Meme chosenMeme;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

The DB-level unique constraints enforce one-vote-per-pair and one-vote-per-matchup. The application layer checks for duplicates first and returns HTTP 409 before the constraint fires.

### `Tournament` entity

```java
@Entity
@Table(name = "tournaments")
public class Tournament {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentStatus status; // PENDING_APPROVAL, ACTIVE, COMPLETED, REJECTED

    @Column(name = "round_duration_hours", nullable = false)
    private int roundDurationHours; // 1, 6, or 24

    @Column(name = "meme_count", nullable = false)
    private int memeCount; // 8 or 16

    @Column(name = "current_round")
    private Integer currentRound; // null until ACTIVE; 1-indexed

    @Column(name = "current_round_ends_at")
    private LocalDateTime currentRoundEndsAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "champion_id")
    private Meme champion; // set when COMPLETED

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TournamentMatchup> matchups = new ArrayList<>();
}
```

### `TournamentMatchup` entity

```java
@Entity
@Table(name = "tournament_matchups")
public class TournamentMatchup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "round_number", nullable = false)
    private int roundNumber; // 1-indexed

    @Column(name = "bracket_position", nullable = false)
    private int bracketPosition; // position within the round, 1-indexed

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_a_id", nullable = false)
    private Meme memeA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_b_id", nullable = false)
    private Meme memeB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Meme winner; // null until round ends

    @Column(name = "votes_a")
    private int votesA = 0;

    @Column(name = "votes_b")
    private int votesB = 0;

    @OneToMany(mappedBy = "matchup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BattleVote> votes = new ArrayList<>();
}
```

### DTOs

```
BattlePairResponse   { pairId, memeA: MemeSnapshot, memeB: MemeSnapshot }
MemeSnapshot         { id, title, imageUrl, voteCount }
BattleVoteResult     { pairId/matchupId, memeAVotes, memeBVotes, chosenMemeId }
TournamentCreateRequest { name, memeIds: List<Long>, roundDurationHours }
TournamentResponse   { id, name, creator, status, roundDurationHours, currentRound,
                       currentRoundEndsAt, champion, matchups: List<MatchupResponse>,
                       createdAt, completedAt }
TournamentSummaryResponse { id, name, creator, status, memeCount, createdAt }
MatchupResponse      { id, roundNumber, bracketPosition, memeA, memeB,
                       votesA, votesB, winner }
```

### WebSocket Message Payloads

**`/topic/battle/quick`**
```json
{ "pairId": 42, "memeAId": 7, "memeBId": 13, "votesA": 5, "votesB": 3 }
```

**`/topic/battle/tournament/{tournamentId}`**
```json
{ "matchupId": 99, "votesA": 12, "votesB": 8, "winnerId": null }
```

### New Repository Interfaces

```java
// BattleVoteRepository
Optional<BattleVote> findByUserIdAndBattlePairId(Long userId, Long pairId);
Optional<BattleVote> findByUserIdAndMatchupId(Long userId, Long matchupId);
int countByMatchupIdAndChosenMemeId(Long matchupId, Long memeId);

// TournamentMatchupRepository
List<TournamentMatchup> findByTournamentIdAndRoundNumber(Long tournamentId, int round);

// TournamentRepository
List<Tournament> findByStatus(TournamentStatus status);
List<Tournament> findByStatusAndCurrentRoundEndsAtBefore(TournamentStatus status, LocalDateTime now);
List<Tournament> findByCreatorId(Long creatorId);
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Quick Battle pair contains exactly two distinct memes

*For any* call to `QuickBattleService.getNewPair()` when the gallery contains at least 2 memes, the returned `BattlePairResponse` SHALL contain exactly 2 memes with different IDs.

**Validates: Requirements 1.1, 1.3**

---

### Property 2: One-vote-per-pair enforcement

*For any* authenticated user and any `BattlePair`, if that user has already cast a `BattleVote` for that pair, a second vote attempt SHALL be rejected (HTTP 409 / `DuplicateVoteException`), and the vote counts for the pair SHALL remain unchanged.

**Validates: Requirements 2.2**

---

### Property 3: One-vote-per-matchup enforcement

*For any* authenticated user and any `TournamentMatchup`, if that user has already cast a `BattleVote` for that matchup, a second vote attempt SHALL be rejected (HTTP 409 / `DuplicateVoteException`), and the vote counts for the matchup SHALL remain unchanged.

**Validates: Requirements 8.2**

---

### Property 4: Vote count consistency after a Quick Battle vote

*For any* `BattlePair` and any valid vote cast on it, the sum `votesA + votesB` returned in the `BattleVoteResult` SHALL equal the total number of `BattleVote` records persisted for that pair.

**Validates: Requirements 2.3, 2.4**

---

### Property 5: Tiebreaker selects lower meme ID

*For any* `TournamentMatchup` where `votesA == votesB` at round expiry, the winner determined by `RoundAdvancementScheduler` SHALL be the meme with the lower ID.

**Validates: Requirements 7.2**

---

### Property 6: Tournament bracket size invariant

*For any* tournament created with `n` memes (where `n` is 8 or 16), the number of first-round `TournamentMatchup` records created SHALL equal `n / 2`, and the total number of rounds SHALL equal `log₂(n)`.

**Validates: Requirements 5.7, 5.8**

---

### Property 7: Round advancement preserves winner count

*For any* completed tournament round with `k` matchups, the number of winners advanced to the next round SHALL equal `k`, and each winner SHALL be one of the two memes from its matchup.

**Validates: Requirements 7.1, 7.3**

---

### Property 8: Tournament status transition validity

*For any* tournament, the status SHALL only transition along the valid paths: `PENDING_APPROVAL → ACTIVE`, `PENDING_APPROVAL → REJECTED`, or `ACTIVE → COMPLETED`. No other transitions SHALL be permitted.

**Validates: Requirements 6.1, 6.2, 6.5**

---

## Error Handling

| Scenario | HTTP Status | Response Body |
|----------|-------------|---------------|
| Gallery has fewer than 2 memes | 400 | `{ "message": "Insufficient memes for a battle" }` |
| Duplicate Quick Battle vote | 409 | `{ "message": "You have already voted on this battle pair" }` |
| Duplicate tournament matchup vote | 409 | `{ "message": "You have already voted on this matchup" }` |
| Vote on non-active-round matchup | 400 | `{ "message": "This matchup is not in the current active round" }` |
| Vote on non-ACTIVE tournament | 400 | `{ "message": "Tournament is not currently active" }` |
| Tournament meme count not 8 or 16 | 400 | `{ "message": "Tournament requires exactly 8 or 16 memes" }` |
| Duplicate meme ID in tournament request | 400 | `{ "message": "Duplicate meme IDs are not allowed" }` |
| Meme ID not found | 404 | `{ "message": "Meme not found: {id}" }` |
| Non-admin approves/rejects tournament | 403 | `{ "message": "Admin access required" }` |
| Approve/reject non-PENDING tournament | 409 | `{ "message": "Tournament is not in PENDING_APPROVAL status" }` |
| Unauthenticated vote attempt | 401 | `{ "message": "Authentication required" }` |

All error responses use the existing `MessageResponse` DTO and are handled by the existing `GlobalExceptionHandler`. New custom exceptions (`DuplicateVoteException`, `TournamentStateException`, `InsufficientMemesException`) will be added and mapped there.

The `RoundAdvancementScheduler` catches all exceptions per tournament to prevent one failing tournament from blocking others. Failures are logged at ERROR level with the tournament ID.

---

## Testing Strategy

### Unit Tests

Focus on specific examples, edge cases, and error conditions:

- `QuickBattleServiceTest`: verify pair selection delegates to `MemeService`, verify error when `< 2` memes
- `BattleVoteServiceTest`: verify vote persistence, duplicate rejection, vote count update, WebSocket broadcast call
- `TournamentServiceTest`: verify bracket generation for 8 and 16 memes, status transition guards, admin-only enforcement
- `RoundAdvancementSchedulerTest`: verify winner selection (higher votes wins, lower ID on tie), next-round matchup creation, `COMPLETED` transition on final round

### Property-Based Tests

Using [jqwik](https://jqwik.net/) (Java property-based testing library). Each test runs a minimum of 100 iterations.

**Property 1 — Quick Battle pair distinctness**
Tag: `Feature: battle-arena, Property 1: Quick Battle pair contains exactly two distinct memes`
Generate: random lists of ≥ 2 `Meme` stubs; assert `memeA.id != memeB.id`

**Property 2 — One-vote-per-pair enforcement**
Tag: `Feature: battle-arena, Property 2: One-vote-per-pair enforcement`
Generate: random `(userId, battlePairId)` pairs; first vote succeeds, second vote throws `DuplicateVoteException`; vote counts unchanged

**Property 3 — One-vote-per-matchup enforcement**
Tag: `Feature: battle-arena, Property 3: One-vote-per-matchup enforcement`
Generate: random `(userId, matchupId)` pairs; same pattern as Property 2

**Property 4 — Vote count consistency**
Tag: `Feature: battle-arena, Property 4: Vote count consistency after a Quick Battle vote`
Generate: random sequence of votes from distinct users on the same pair; assert `votesA + votesB == total BattleVote records`

**Property 5 — Tiebreaker**
Tag: `Feature: battle-arena, Property 5: Tiebreaker selects lower meme ID`
Generate: random pairs of memes with equal vote counts; assert winner ID is `min(memeA.id, memeB.id)`

**Property 6 — Bracket size invariant**
Tag: `Feature: battle-arena, Property 6: Tournament bracket size invariant`
Generate: tournament creation requests with `n ∈ {8, 16}`; assert `firstRoundMatchups.size() == n/2` and `totalRounds == log₂(n)`

**Property 7 — Round advancement winner count**
Tag: `Feature: battle-arena, Property 7: Round advancement preserves winner count`
Generate: random sets of matchups with random vote counts; assert `winners.size() == matchups.size()` and each winner is one of the two memes in its matchup

**Property 8 — Status transition validity**
Tag: `Feature: battle-arena, Property 8: Tournament status transition validity`
Generate: random sequences of approve/reject/advance operations; assert no invalid status transitions occur

### Integration Tests

- End-to-end Quick Battle flow: create pair → vote → verify WebSocket broadcast → verify 409 on second vote
- Tournament lifecycle: create → approve → vote → scheduler advance → complete
- Admin authorization: verify 403 for non-admin on approve/reject endpoints
- Public access: verify tournament results accessible without JWT

### Frontend Tests

- `QuickBattle`: renders pair, disables controls after vote, shows countdown, auto-fetches next pair after 1.5 s
- `TournamentBracket`: renders all rounds, shows countdown, updates vote counts on WebSocket message
- `TournamentCreate`: validates 8/16 meme selection, submits correct payload
