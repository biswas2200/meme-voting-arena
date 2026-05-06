# Requirements Document

## Introduction

The Battle Arena is a competitive voting feature for the Meme Voting Arena application. It introduces two modes of head-to-head meme competition: Quick Battle and Tournament Mode.

In Quick Battle, the system pairs two random memes and authenticated users vote for their favorite in real time. Votes are broadcast via the existing WebSocket infrastructure and the next pair loads automatically after each vote.

In Tournament Mode, any authenticated user can create a bracket tournament using 8 or 16 memes selected from the gallery. An admin must approve the tournament before it goes live. Rounds advance automatically on a time-based schedule set by the creator. The bracket progresses until a champion is crowned, and a results page preserves the full bracket history.

Both modes build on the existing JWT authentication, `MemeService`, `VoteRepository`, `MemeRepository`, and STOMP WebSocket infrastructure.

---

## Glossary

- **Battle_Arena**: The feature encompassing Quick Battle and Tournament Mode.
- **Quick_Battle**: A mode where the system selects two random memes for a single head-to-head vote.
- **Battle_Pair**: A set of exactly two memes presented side-by-side in a Quick Battle.
- **Battle_Vote**: A vote cast by an authenticated user for one meme within a specific Battle_Pair or Tournament_Matchup.
- **Battle_Vote_Service**: The backend service responsible for recording and validating Battle_Votes.
- **Quick_Battle_Service**: The backend service responsible for selecting Battle_Pairs and managing Quick Battle state.
- **Tournament**: A bracket competition containing either 8 or 16 memes across multiple timed rounds.
- **Tournament_Creator**: The authenticated user who creates a Tournament.
- **Tournament_Bracket**: The full tree structure of rounds and matchups within a Tournament.
- **Tournament_Matchup**: A single head-to-head pairing of two memes within a Tournament round.
- **Tournament_Round**: A set of all simultaneous Tournament_Matchups at the same bracket depth.
- **Round_Duration**: The time limit for a Tournament_Round, set by the Tournament_Creator (1 hour, 6 hours, or 24 hours).
- **Round_Advancement_Scheduler**: The backend component that monitors round timers and advances rounds when they expire.
- **Tournament_Service**: The backend service responsible for creating, approving, and managing Tournaments.
- **Admin**: A user with `Role.ADMIN` in the existing `User` model.
- **Bracket_UI**: The frontend component that renders the Tournament_Bracket, vote counts, and time remaining.
- **Champion**: The meme that wins the final round of a Tournament.
- **WebSocket_Broker**: The existing STOMP broker configured at `/ws` broadcasting to `/topic`.

---

## Requirements

### Requirement 1: Quick Battle — Pair Selection

**User Story:** As an authenticated user, I want the system to automatically pick two random memes for me to compare, so that I can start voting immediately without choosing memes myself.

#### Acceptance Criteria

1. WHEN an authenticated user requests a Quick Battle, THE Quick_Battle_Service SHALL select exactly 2 distinct memes at random from the meme gallery.
2. IF the meme gallery contains fewer than 2 memes, THEN THE Quick_Battle_Service SHALL return an error response indicating that insufficient memes are available.
3. THE Quick_Battle_Service SHALL assign a unique Battle_Pair identifier to each selected pair before returning it to the client.
4. WHEN a Quick Battle pair is returned, THE Quick_Battle_Service SHALL include the meme id, title, image URL, and current vote count for each meme in the pair.

---

### Requirement 2: Quick Battle — Voting

**User Story:** As an authenticated user, I want to click one meme in a battle pair to cast my vote, so that my preference is recorded immediately.

#### Acceptance Criteria

1. WHEN an authenticated user submits a Battle_Vote for a Battle_Pair, THE Battle_Vote_Service SHALL record the vote and associate it with the user's id and the Battle_Pair identifier.
2. IF an authenticated user attempts to submit a second Battle_Vote for the same Battle_Pair, THEN THE Battle_Vote_Service SHALL reject the request with an HTTP 409 Conflict response.
3. WHEN a Battle_Vote is successfully recorded, THE Battle_Vote_Service SHALL update the vote count for the voted meme and broadcast the updated counts for both memes in the Battle_Pair to `/topic/battle/quick`.
4. WHEN a Battle_Vote is successfully recorded, THE Battle_Vote_Service SHALL return the updated vote counts for both memes in the Battle_Pair to the voting user.
5. THE Battle_Vote_Service SHALL enforce that only authenticated users (valid JWT) may submit Battle_Votes; unauthenticated requests SHALL receive an HTTP 401 response.

---

### Requirement 3: Quick Battle — Real-Time Vote Display

**User Story:** As a viewer of a Quick Battle, I want to see vote counts update in real time, so that I can follow the live results without refreshing the page.

#### Acceptance Criteria

1. WHEN a Battle_Vote is broadcast to `/topic/battle/quick`, THE Battle_Arena frontend SHALL update the displayed vote count for the affected meme without a full page reload.
2. WHILE a Quick Battle pair is displayed, THE Battle_Arena frontend SHALL show a live vote counter for each meme reflecting the most recently received WebSocket message.
3. WHEN a user has already voted on the current Battle_Pair, THE Battle_Arena frontend SHALL display the vote counts for both memes and disable the voting controls for that pair.

---

### Requirement 4: Quick Battle — Auto-Advance

**User Story:** As an authenticated user, I want the next battle pair to load automatically after I vote, so that I can keep voting without manual navigation.

#### Acceptance Criteria

1. WHEN an authenticated user's Battle_Vote is successfully recorded, THE Battle_Arena frontend SHALL wait 1500 milliseconds and then automatically request a new Battle_Pair from the Quick_Battle_Service.
2. WHILE the 1500-millisecond delay is active, THE Battle_Arena frontend SHALL display a visual indicator showing that the next pair is loading.
3. WHEN the new Battle_Pair is received, THE Battle_Arena frontend SHALL replace the current pair display with the new pair and reset the voting controls.

---

### Requirement 5: Tournament — Creation

**User Story:** As an authenticated user, I want to create a tournament by selecting memes from the gallery, so that I can organize a structured bracket competition.

#### Acceptance Criteria

1. WHEN an authenticated user submits a tournament creation request, THE Tournament_Service SHALL accept a list of either 8 or 16 distinct meme ids, a tournament name, and a Round_Duration value of 1 hour, 6 hours, or 24 hours.
2. IF the submitted meme id list contains fewer than 8 or more than 16 entries, THEN THE Tournament_Service SHALL reject the request with an HTTP 400 response and a descriptive error message.
3. IF the submitted meme id list contains a count other than 8 or 16, THEN THE Tournament_Service SHALL reject the request with an HTTP 400 response indicating that only 8 or 16 memes are permitted.
4. IF the submitted meme id list contains a duplicate meme id, THEN THE Tournament_Service SHALL reject the request with an HTTP 400 response.
5. IF any submitted meme id does not exist in the meme gallery, THEN THE Tournament_Service SHALL reject the request with an HTTP 404 response.
6. WHEN a valid tournament creation request is accepted, THE Tournament_Service SHALL create the Tournament with status `PENDING_APPROVAL`, record the Tournament_Creator's user id, and return the new tournament id to the creator.
7. WHEN a Tournament is created with 8 memes, THE Tournament_Service SHALL generate a Tournament_Bracket with 3 rounds and 4 first-round Tournament_Matchups.
8. WHEN a Tournament is created with 16 memes, THE Tournament_Service SHALL generate a Tournament_Bracket with 4 rounds and 8 first-round Tournament_Matchups.
9. THE Tournament_Service SHALL assign the first-round Tournament_Matchups by randomly pairing the submitted memes at creation time.

---

### Requirement 6: Tournament — Admin Approval

**User Story:** As an admin, I want to review and approve or reject pending tournaments, so that I can ensure only appropriate content goes live.

#### Acceptance Criteria

1. WHEN an Admin submits an approval decision for a Tournament in `PENDING_APPROVAL` status, THE Tournament_Service SHALL update the Tournament status to `ACTIVE` and start the timer for the first Tournament_Round.
2. WHEN an Admin submits a rejection decision for a Tournament in `PENDING_APPROVAL` status, THE Tournament_Service SHALL update the Tournament status to `REJECTED` and notify the Tournament_Creator via the existing notification mechanism.
3. IF a non-Admin user attempts to approve or reject a Tournament, THEN THE Tournament_Service SHALL return an HTTP 403 Forbidden response.
4. THE Tournament_Service SHALL expose an endpoint that returns all Tournaments with `PENDING_APPROVAL` status, accessible only to Admin users.
5. IF an Admin attempts to approve or reject a Tournament that is not in `PENDING_APPROVAL` status, THEN THE Tournament_Service SHALL return an HTTP 409 Conflict response.

---

### Requirement 7: Tournament — Round Timing and Advancement

**User Story:** As a tournament participant, I want rounds to advance automatically when the timer expires, so that the bracket progresses without manual intervention.

#### Acceptance Criteria

1. WHEN a Tournament_Round timer expires, THE Round_Advancement_Scheduler SHALL determine the winner of each Tournament_Matchup in that round as the meme with the higher Battle_Vote count.
2. WHEN two memes in a Tournament_Matchup have equal Battle_Vote counts at round expiry, THE Round_Advancement_Scheduler SHALL select the winner by the lower meme id as a deterministic tiebreaker.
3. WHEN all Tournament_Matchup winners in a round are determined, THE Round_Advancement_Scheduler SHALL create the next Tournament_Round's matchups by pairing winners in bracket order and start the next round's timer.
4. WHEN the final Tournament_Round completes, THE Round_Advancement_Scheduler SHALL set the Tournament status to `COMPLETED` and designate the winning meme as the Champion.
5. WHILE a Tournament_Round is active, THE Round_Advancement_Scheduler SHALL make all Tournament_Matchups in that round open for voting simultaneously.
6. THE Round_Advancement_Scheduler SHALL process round advancement within 60 seconds of the scheduled round expiry time.

---

### Requirement 8: Tournament — Voting

**User Story:** As an authenticated user, I want to vote on any open tournament matchup, so that I can influence the bracket outcome.

#### Acceptance Criteria

1. WHEN an authenticated user submits a Battle_Vote for a Tournament_Matchup, THE Battle_Vote_Service SHALL record the vote associated with the user's id, the Tournament_Matchup id, and the chosen meme id.
2. IF an authenticated user attempts to submit a second Battle_Vote for the same Tournament_Matchup, THEN THE Battle_Vote_Service SHALL reject the request with an HTTP 409 Conflict response.
3. IF a user attempts to vote on a Tournament_Matchup that belongs to a round that is not the current active round, THEN THE Battle_Vote_Service SHALL reject the request with an HTTP 400 response.
4. IF a user attempts to vote on a Tournament_Matchup in a Tournament with status other than `ACTIVE`, THEN THE Battle_Vote_Service SHALL reject the request with an HTTP 400 response.
5. WHEN a Tournament Battle_Vote is successfully recorded, THE Battle_Vote_Service SHALL broadcast the updated vote counts for the affected Tournament_Matchup to `/topic/battle/tournament/{tournamentId}`.

---

### Requirement 9: Tournament — Bracket UI

**User Story:** As a user, I want to view the full tournament bracket with vote counts and time remaining, so that I can track the competition's progress.

#### Acceptance Criteria

1. THE Bracket_UI SHALL display all Tournament_Rounds and their Tournament_Matchups in a visual bracket layout.
2. WHILE a Tournament_Round is active, THE Bracket_UI SHALL display the time remaining for the current round, updated at least every 30 seconds.
3. THE Bracket_UI SHALL poll the Tournament_Service every 30 seconds to refresh the full bracket state, including vote counts and round status.
4. WHEN a Tournament_Matchup is in an active round and the current user has not yet voted on it, THE Bracket_UI SHALL display voting controls for that matchup.
5. WHEN a Tournament_Matchup round has ended, THE Bracket_UI SHALL display the winner of that matchup and the final vote counts for both memes.
6. WHEN a Tournament status is `COMPLETED`, THE Bracket_UI SHALL display the Champion meme prominently at the top of the bracket.
7. WHEN a WebSocket message is received on `/topic/battle/tournament/{tournamentId}`, THE Bracket_UI SHALL update the vote counts for the affected Tournament_Matchup without a full page reload.

---

### Requirement 10: Tournament — Results Page

**User Story:** As a user, I want to view the complete bracket history of a finished tournament, so that I can review how the champion was determined.

#### Acceptance Criteria

1. WHEN a user navigates to the results page for a `COMPLETED` Tournament, THE Battle_Arena frontend SHALL display the full Tournament_Bracket including all rounds, all Tournament_Matchups, all final vote counts, and the Champion.
2. THE Battle_Arena frontend SHALL display the Tournament name, the Tournament_Creator's username, the Round_Duration used, and the date the Tournament completed on the results page.
3. THE Battle_Arena frontend SHALL make the results page accessible to both authenticated and unauthenticated users.

---

### Requirement 11: Battle Arena Navigation

**User Story:** As a user, I want to access the Battle Arena from the main navigation, so that I can find Quick Battle and Tournament Mode easily.

#### Acceptance Criteria

1. THE Battle_Arena frontend SHALL add a "Battle Arena" entry to the existing Navbar that navigates to the Battle Arena landing page.
2. THE Battle_Arena landing page SHALL present clearly labeled options to enter Quick Battle mode or to view and create Tournaments.
3. WHILE a user is not authenticated, THE Battle_Arena frontend SHALL display Quick Battle and Tournament voting as read-only and prompt the user to log in before voting or creating a Tournament.
