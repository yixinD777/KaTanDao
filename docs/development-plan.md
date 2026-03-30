# KaTanDao Development Plan

## 1. Project Goal

Build an online playable base-game Settlers of Catan system with:

- Java backend as the authoritative game server
- Web client for board rendering and player interaction
- Real-time multiplayer support
- A clean separation between game rules, networking, and UI

The first version focuses on the base game only. Expansions, AI, replay, ranking, and advanced social systems are out of scope for v1.

## 2. Recommended Tech Stack

### Backend

- Java 21
- Spring Boot 3.x
- Spring WebSocket
- Gradle multi-module build
- Jackson for serialization
- JUnit 5 for unit and integration tests

### Frontend

- React
- TypeScript
- Vite
- SVG for board rendering in v1
- Zustand or Redux Toolkit for client state management
- TanStack Query only if REST endpoints grow later

### Shared Engineering Direction

- Server-authoritative game state
- WebSocket for real-time actions and state updates
- Full snapshot sync in early versions
- Domain-first design centered on a reusable game engine

## 3. Product Scope for V1

### In Scope

- Create room
- Join room
- Ready/start game
- 3 to 4 player multiplayer session flow
- Base map generation
- Initial placement phase
- Turn rotation
- Dice rolling
- Resource distribution
- Build road
- Build settlement
- Upgrade city
- Buy development card
- Play supported development cards
- Robber movement
- Discard on rolling 7
- Bank trade
- Player trade
- Longest Road and Largest Army
- Victory point calculation
- Game over flow

### Out of Scope

- Expansions
- AI players
- Matchmaking
- Accounts and friends
- Spectator mode
- Replay system
- Offline mode
- Mobile adaptation
- Fancy animation-heavy presentation
- Persistent production-grade game recovery

## 4. Architecture Overview

The system should be split into three major modules.

### 4.1 `game-core`

Responsibilities:

- Represent the full game domain model
- Validate game rules
- Execute player commands
- Produce deterministic state transitions
- Calculate scoring and end conditions

Design rules:

- Pure Java, no Spring dependencies
- No network or UI code
- Strong unit test coverage

### 4.2 `server`

Responsibilities:

- Room lifecycle
- Player session management
- WebSocket message handling
- Command authorization and dispatch
- Game state storage in memory for v1
- Broadcasting snapshots and events

Design rules:

- Treat `game-core` as the only source of game logic
- Never let the client decide legality of a move

### 4.3 `web-client`

Responsibilities:

- Room and lobby UI
- Board rendering
- Local interaction state
- Real-time message handling
- Action submission
- User feedback and turn guidance

Design rules:

- State-driven rendering
- Client sends intent, not trusted state mutations
- Keep rendering and networking separated

## 5. Backend Design Plan

### 5.1 Module Structure

Suggested future structure:

```text
KaTanDao/
  build.gradle
  settings.gradle
  docs/
  game-core/
  server/
  web-client/
```

### 5.2 Core Domain Model

Important backend model objects:

- `GameState`
- `Board`
- `HexTile`
- `Intersection`
- `Edge`
- `PlayerState`
- `RobberState`
- `TurnState`
- `GamePhase`
- `ResourceType`
- `DevelopmentCardType`
- `BuildingType`

### 5.3 Command-Driven Rule Engine

Player operations should be modeled as commands:

- `RollDiceCommand`
- `BuildRoadCommand`
- `BuildSettlementCommand`
- `UpgradeCityCommand`
- `BuyDevelopmentCardCommand`
- `PlayDevelopmentCardCommand`
- `TradeWithBankCommand`
- `OfferPlayerTradeCommand`
- `AcceptPlayerTradeCommand`
- `MoveRobberCommand`
- `EndTurnCommand`

Processing pipeline:

1. Receive command
2. Verify player identity and turn ownership
3. Validate rule preconditions
4. Apply state transition
5. Emit updated snapshot and domain events

### 5.4 Server Communication Model

Use WebSocket for active gameplay.

Client-to-server messages:

- `create_room`
- `join_room`
- `set_ready`
- `start_game`
- `player_action`

Server-to-client messages:

- `room_state`
- `game_snapshot`
- `game_event`
- `action_rejected`
- `turn_prompt`
- `game_over`

V1 strategy:

- Broadcast a fresh snapshot after each accepted action
- Optionally include a lightweight event message for logs and animation hooks

### 5.5 Persistence Strategy

For v1:

- In-memory room and match state
- No database requirement

Later:

- PostgreSQL for room history and recovery
- Redis for distributed session/game coordination if scaling is needed

## 6. Frontend Design Plan

### 6.1 Page Flow

Core pages for v1:

- Home page
- Room/lobby page
- Game page
- End-game modal

### 6.2 Game Screen Layout

Recommended areas:

- Center board view
- Right or bottom action panel
- Player summary panel
- Resource and development card area
- Event log panel
- Trade modal

### 6.3 Rendering Strategy

For the board:

- Use SVG for hex tiles, edges, and intersections
- Bind each clickable edge and intersection to domain IDs
- Highlight valid actions based on current legal moves from the server

Why SVG first:

- Easier hit testing
- Easier styling
- Enough performance for a board game

### 6.4 Frontend State Model

Separate state into:

- `connection state`
- `room state`
- `game snapshot`
- `ui interaction state`

Examples of UI interaction state:

- selected action mode
- hovered tile
- pending trade draft
- currently highlighted legal positions

### 6.5 UX Rules for V1

- Always show whose turn it is
- Always show what actions are currently allowed
- Show rejection reasons clearly
- Highlight legal build positions before click
- Keep interaction latency low
- Prefer clarity over animation

## 7. Delivery Phases

### Phase 0: Foundation

Goal:

- Initialize repository structure and engineering baseline

Tasks:

- Create Gradle multi-module project
- Add server module
- Add game-core module
- Create web-client with Vite + React + TypeScript
- Set up linting, formatting, and test commands
- Add basic CI

Deliverable:

- Repository can build backend and frontend successfully

### Phase 1: Core Game Engine

Goal:

- Make the base game rules executable without UI

Tasks:

- Model board and player state
- Implement initial map setup
- Implement setup phase turn order
- Implement resource economy
- Implement build validation
- Implement robber logic
- Implement development cards
- Implement turn progression
- Implement scoring and win detection
- Add comprehensive unit tests

Deliverable:

- `game-core` can simulate a full legal game in tests

### Phase 2: Multiplayer Server

Goal:

- Expose the game engine as an online match service

Tasks:

- Room management
- Player connection/session mapping
- WebSocket message protocol
- Command handling and validation
- Snapshot broadcasting
- Reconnection policy for v1
- Basic server integration tests

Deliverable:

- Multiple players can connect and play through WebSocket APIs

### Phase 3: Playable Web Client

Goal:

- Build a usable browser client for online play

Tasks:

- Lobby page
- Room status UI
- Board rendering
- Player panels
- Turn prompts and action controls
- Build and robber interactions
- Trade interactions
- Error and event log display

Deliverable:

- A browser client can complete a full base-game match

### Phase 4: Polish and Stability

Goal:

- Improve usability and reduce gameplay bugs

Tasks:

- Better interaction hints
- Visual highlighting for legal actions
- Improved reconnect handling
- Better error states
- Basic animation for roll/build/robber
- Balance performance and render updates
- Add more end-to-end coverage

Deliverable:

- Stable internal alpha version

## 8. Milestones

### Milestone A

- Monorepo initialized
- Backend starts
- Frontend starts
- WebSocket handshake works

### Milestone B

- Full rule engine for base game passes tests

### Milestone C

- 2 to 4 local browser sessions can join one room and complete gameplay

### Milestone D

- Internal alpha with acceptable UX and major rule bugs addressed

## 9. Testing Strategy

### Backend Tests

- Unit tests for all rule validators
- Scenario tests for setup and turn flow
- Integration tests for room and WebSocket flows
- Deterministic tests for scoring and robber behavior

### Frontend Tests

- Component tests for action panels and status displays
- Interaction tests for board clicks and legal highlight behavior
- Contract tests against message payload examples

### Manual Test Checklist

- Start room and fill players
- Initial placement order correctness
- Dice roll resource distribution correctness
- Build restrictions correctness
- Robber behavior on 7
- Development card usage correctness
- Trade flow correctness
- Win condition correctness

## 10. Suggested Timeline

This is a realistic solo or small-team timeline for a solid first version.

### Week 1

- Create monorepo structure
- Initialize backend and frontend apps
- Define domain model and protocol draft

### Week 2 to 3

- Build and test core board/state model
- Implement setup phase and basic turns

### Week 4 to 5

- Implement full base-game rules
- Finish major rule tests

### Week 6

- Add multiplayer room and WebSocket flows

### Week 7 to 8

- Build playable web UI
- Connect board interaction to backend

### Week 9

- Fix gameplay bugs
- Improve usability
- Run full playtest rounds

## 11. Immediate Next Step

The best next implementation step is:

1. Create the multi-module project skeleton
2. Define the shared game protocol
3. Design the `GameState` and command model in `game-core`

The project should not start with detailed frontend visuals. The rule engine and message protocol need to settle first, because both the server and client depend on them.
