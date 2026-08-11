# gdg Backend — System Diagrams

Reverse-engineered from the current `gdg` codebase (Spring Boot 3.5, Java 21) — domain model, data flow at three levels of decomposition, and functional scope by actor. Drawn against the code as it stands after the test-hardening pass (176 passing tests), so entity fields, endpoints, and access rules below match what's actually implemented.

## Contents

1. [Class Diagram](#1-class-diagram)
2. [DFD — Level 0 (Context)](#2-dfd--level-0-context)
3. [DFD — Level 1 (Overview)](#3-dfd--level-1-overview)
4. [DFD — Level 2: Quick Battle (3.0)](#4-dfd--level-2-quick-battle-30)
5. [DFD — Level 2: Tournament Management (4.0)](#5-dfd--level-2-tournament-management-40)
6. [Use Case Diagram](#6-use-case-diagram)

---

## 1. Class Diagram

The seven JPA entities and their relationships. A `BattleVote` is deliberately polymorphic: exactly one of `battlePair` / `matchup` is set, distinguishing a Quick Battle vote from a tournament matchup vote — enforced at the database level by two separate unique constraints on `(user_id, battle_pair_id)` and `(user_id, matchup_id)`.

```mermaid
classDiagram
    class User {
        -Long id
        -String username
        -String email
        -String password
        -Role role
        -String avatarUrl
        -LocalDateTime createdAt
    }
    class Meme {
        -Long id
        -String title
        -String imageUrl
        -LocalDateTime uploadDate
        -Integer voteCount
        +updateVoteCount()
    }
    class Vote {
        -Long id
        -VoteType voteType
        -LocalDateTime createdAt
    }
    class Tournament {
        -Long id
        -String name
        -TournamentStatus status
        -int roundDurationHours
        -int memeCount
        -Integer currentRound
        -LocalDateTime currentRoundEndsAt
        -LocalDateTime createdAt
        -LocalDateTime completedAt
    }
    class TournamentMatchup {
        -Long id
        -int roundNumber
        -int bracketPosition
        -int votesA
        -int votesB
    }
    class BattlePair {
        -Long id
        -LocalDateTime createdAt
    }
    class BattleVote {
        -Long id
        -LocalDateTime createdAt
    }
    class Role {
        <<enumeration>>
        USER
        ADMIN
    }
    class TournamentStatus {
        <<enumeration>>
        PENDING_APPROVAL
        ACTIVE
        COMPLETED
        REJECTED
    }
    class VoteType {
        <<enumeration>>
        UPVOTE
        DOWNVOTE
    }

    User "1" --> "*" Meme : uploadedBy
    User "1" --> "*" Vote : casts
    User "1" --> "0..1" Role
    Meme "1" --> "*" Vote : receives
    Vote "1" --> "1" VoteType
    User "1" --> "*" Tournament : creates
    Tournament "1" --> "*" TournamentMatchup : matchups
    Tournament "1" --> "0..1" Meme : champion
    Tournament "1" --> "1" TournamentStatus
    TournamentMatchup "1" --> "1" Meme : memeA
    TournamentMatchup "1" --> "1" Meme : memeB
    TournamentMatchup "1" --> "0..1" Meme : winner
    TournamentMatchup "1" --> "*" BattleVote : votes
    BattlePair "1" --> "1" Meme : memeA
    BattlePair "1" --> "1" Meme : memeB
    BattlePair "1" --> "*" BattleVote : votes
    BattleVote "*" --> "1" User : castBy
    BattleVote "*" --> "1" Meme : chosenMeme
```

> **Not modeled as an entity:** `UserPrincipal` is a runtime Spring Security wrapper around `User`, not a persisted class — omitted here as an implementation detail of the security layer, not the domain.

---

## 2. DFD — Level 0 (Context)

The system as a single process, showing every actor that crosses its boundary. The Reddit scraper is drawn as a dotted flow — it's a planned integration (`redit-scraper`, a sibling project) with no wiring into this backend yet.

```mermaid
flowchart LR
    Guest["Guest / Visitor<br/>(Browser)"]
    RegUser["Registered User<br/>(Browser)"]
    Admin["Admin<br/>(Browser)"]
    Scraper["Reddit Scraper Service<br/>(planned integration)"]
    Sys(("0.0<br/>Meme Voting<br/>Arena API"))

    Guest -- "browse memes, sign up, sign in" --> Sys
    RegUser -- "upload meme, vote, create tournament" --> Sys
    Admin -- "approve / reject tournament" --> Sys
    Sys -- "meme feed, leaderboard,<br/>brackets, JWT" --> Guest
    Sys -- "profile stats, tournament state,<br/>live vote updates" --> RegUser
    Sys -- "pending tournament queue" --> Admin
    Scraper -. "scraped meme submissions (future)" .-> Sys
```

---

## 3. DFD — Level 1 (Overview)

The five major processes and six data stores behind the single Level 0 bubble. Every write path that touches vote counts (2.0, 3.0, 4.0) also triggers Process 5.0, which pushes the update to connected clients over the `/topic/votes` STOMP broker — the real-time layer behind the live vote counters in the UI.

```mermaid
flowchart TB
    U["User (Browser)"]
    A["Admin (Browser)"]
    T["Round Timer<br/>(scheduler, 60s tick)"]

    P1(("1.0<br/>Authenticate &<br/>Manage Profile"))
    P2(("2.0<br/>Browse & Manage<br/>Memes"))
    P3(("3.0<br/>Quick Battle"))
    P4(("4.0<br/>Tournament<br/>Management"))
    P5(("5.0<br/>Broadcast Live<br/>Updates"))

    D1[("D1 Users")]
    D2[("D2 Memes")]
    D3[("D3 Votes")]
    D4[("D4 Tournaments &<br/>Matchups")]
    D5[("D5 Battle Pairs &<br/>Battle Votes")]
    D6[("D6 Image Storage<br/>S3 / GCS")]

    U -- "credentials, profile edits" --> P1
    P1 <--> D1
    P1 -- "JWT, profile & stats" --> U

    U -- "upload / vote / browse" --> P2
    P2 <--> D2
    P2 <--> D3
    P2 <--> D6
    P2 -- "meme feed, leaderboard" --> U
    P2 --> P5

    U -- "request pair / cast vote" --> P3
    P3 <--> D2
    P3 <--> D5
    P3 --> P5

    U -- "create tournament / vote matchup" --> P4
    A -- "approve / reject" --> P4
    T -- "round-expiry trigger" --> P4
    P4 <--> D4
    P4 <--> D2
    P4 --> P5

    P5 -- "WebSocket push (/topic/votes)" --> U
    P5 -- "pending-queue refresh" --> A
```

---

## 4. DFD — Level 2: Quick Battle (3.0)

A pair is ephemeral — two random memes, no repeats enforced — and one vote per pair per user is guarded at the database layer, not just in application logic.

```mermaid
flowchart TB
    U["Registered User"]
    D2[("D2 Memes")]
    D5[("D5 Battle Pairs &<br/>Battle Votes")]
    P5(("to 5.0<br/>Broadcast"))

    P31(("3.1<br/>Select Random<br/>Meme Pair"))
    P32(("3.2<br/>Record Battle<br/>Vote"))
    P33(("3.3<br/>Enforce One-Vote<br/>-Per-Pair"))
    P34(("3.4<br/>Tally Pair<br/>Votes"))

    U -- "GET /battle/quick/pair" --> P31
    P31 <--> D2
    P31 -- "create BattlePair" --> D5
    P31 -- "pair (2 meme snapshots)" --> U

    U -- "POST vote<br/>{pairId, chosenMemeId}" --> P32
    P32 --> P33
    P33 <--> D5
    P33 -- "409 on duplicate" --> U
    P32 -- "write BattleVote" --> D5
    P32 --> P34
    P34 <--> D5
    P34 -- "vote result (A/B counts)" --> U
    P34 --> P5
```

---

## 5. DFD — Level 2: Tournament Management (4.0)

The only automated actor in the system: a scheduler ticks every 60 seconds, checks for tournaments whose round has expired, and advances them — resolving ties, seeding the next round, or crowning a champion on the final round.

```mermaid
flowchart TB
    U["Registered User"]
    A["Admin"]
    T["Round Timer<br/>(scheduler)"]
    D2[("D2 Memes")]
    D4[("D4 Tournaments &<br/>Matchups")]
    D5[("D5 Battle Votes")]
    P5(("to 5.0<br/>Broadcast"))

    P41(("4.1<br/>Create Tournament<br/>& Seed Bracket"))
    P42(("4.2<br/>Approve / Reject"))
    P43(("4.3<br/>Vote on<br/>Matchup"))
    P44(("4.4<br/>Advance<br/>Round"))
    P45(("4.5<br/>Crown<br/>Champion"))

    U -- "name, 8/16 memes,<br/>round duration" --> P41
    P41 <--> D2
    P41 -- "status = PENDING_APPROVAL" --> D4

    A -- "approve / reject" --> P42
    P42 <--> D4
    P42 -- "status = ACTIVE,<br/>round 1 pairs" --> D4

    U -- "matchup vote" --> P43
    P43 <--> D5
    P43 -- "votesA / votesB" --> D4
    P43 --> P5

    T -- "currentRoundEndsAt passed" --> P44
    P44 <--> D4
    P44 -- "resolve ties, seed<br/>next round" --> D4
    P44 --> P5
    P44 -- "final round resolved" --> P45
    P45 -- "status = COMPLETED,<br/>set champion" --> D4
    P45 --> P5
```

---

## 6. Use Case Diagram

Four actors: an unauthenticated **Guest**, a **Registered User**, an **Admin** (every Registered User capability, plus tournament moderation — `ADMIN` is a `User.role` value, not a separate account type), and the **Round Timer**, the system's one non-human actor. Boundaries here match `SecurityConfig`'s production filter chain exactly — e.g. matchup voting requires authentication, but viewing a bracket does not.

```mermaid
flowchart LR
    Guest["Guest"]
    RegUser["Registered<br/>User"]
    AdminA["Admin"]
    Scheduler["Round Timer<br/>(system)"]

    subgraph SYS["Meme Voting Arena — use cases"]
      direction TB
      UC1(["Register Account"])
      UC2(["Sign In"])
      UC3(["Browse Meme Gallery"])
      UC4(["View Leaderboard"])
      UC12(["View Tournament<br/>Bracket / Results"])
      UC5(["Upload Meme"])
      UC6(["Vote on Meme"])
      UC7(["Play Quick Battle"])
      UC8(["View / Update Profile"])
      UC9(["Upload Avatar"])
      UC10(["Create Tournament"])
      UC11(["Vote on Tournament<br/>Matchup"])
      UC13(["View My Tournaments"])
      UC14(["View Pending<br/>Tournaments"])
      UC15(["Approve Tournament"])
      UC16(["Reject Tournament"])
      UC17(["Advance Tournament<br/>Round"])
    end

    Guest --- UC1
    Guest --- UC2
    Guest --- UC3
    Guest --- UC4
    Guest --- UC12

    RegUser --- UC3
    RegUser --- UC4
    RegUser --- UC12
    RegUser --- UC5
    RegUser --- UC6
    RegUser --- UC7
    RegUser --- UC8
    RegUser --- UC9
    RegUser --- UC10
    RegUser --- UC11
    RegUser --- UC13

    AdminA -. inherits .-> RegUser
    AdminA --- UC14
    AdminA --- UC15
    AdminA --- UC16

    Scheduler --- UC17
```

> **Read vs. write split:** Guest already covers every public GET — gallery, leaderboard, and tournament brackets are open by design (`SecurityConfig` permits them unauthenticated) so results stay shareable without an account.

---

*Diagrams generated from the codebase as of the current test-hardening pass. Source: `gdg/src/main/java/com/meme/gdg`.*
