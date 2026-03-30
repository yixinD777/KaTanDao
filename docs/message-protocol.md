# KaTanDao Message Protocol v1

## 1. Protocol Goal

This document defines the first version of the communication contract between:

- Java backend server
- Web client

The protocol is designed for:

- base-game Catan only
- real-time multiplayer
- server-authoritative game state
- full snapshot updates after accepted actions

V1 prioritizes simplicity and correctness over bandwidth optimization.

## 2. Transport Strategy

### 2.1 HTTP

Use HTTP for lightweight service checks and optional future endpoints such as:

- health checks
- server info
- static config

V1 gameplay does not depend on REST APIs.

### 2.2 WebSocket

Use WebSocket for:

- room lifecycle events
- ready state changes
- game start
- player actions
- game state updates
- event log messages
- error feedback

Recommended endpoint:

- `/ws/game`

## 3. Envelope Format

Every WebSocket message uses the same top-level envelope:

```json
{
  "type": "message_type",
  "requestId": "optional-client-generated-id",
  "timestamp": "2026-03-29T21:00:00Z",
  "payload": {}
}
```

### Field Rules

- `type`: required, identifies message kind
- `requestId`: optional on server pushes, recommended on client actions
- `timestamp`: optional in client messages, recommended in server messages
- `payload`: required, message-specific body

## 4. Session Model

V1 keeps session identity simple.

### Client Session Assumptions

- Client generates a temporary `playerId`
- Client provides a display name
- Server binds the WebSocket session to that player identity

This avoids account/auth complexity in v1.

Example session bootstrap message:

```json
{
  "type": "hello",
  "requestId": "req-001",
  "payload": {
    "playerId": "player-a8f2",
    "playerName": "Alice"
  }
}
```

Server response:

```json
{
  "type": "hello_ack",
  "requestId": "req-001",
  "timestamp": "2026-03-29T21:00:00Z",
  "payload": {
    "playerId": "player-a8f2",
    "sessionId": "session-01",
    "serverVersion": "0.1.0"
  }
}
```

## 5. Room Flow Messages

### 5.1 Create Room

Client:

```json
{
  "type": "create_room",
  "requestId": "req-002",
  "payload": {
    "playerId": "player-a8f2",
    "playerName": "Alice",
    "config": {
      "maxPlayers": 4
    }
  }
}
```

Server:

```json
{
  "type": "room_state",
  "requestId": "req-002",
  "timestamp": "2026-03-29T21:00:02Z",
  "payload": {
    "room": {
      "roomId": "room-1234",
      "status": "WAITING",
      "hostPlayerId": "player-a8f2",
      "maxPlayers": 4,
      "players": [
        {
          "playerId": "player-a8f2",
          "playerName": "Alice",
          "seat": 0,
          "ready": false,
          "connected": true
        }
      ]
    }
  }
}
```

### 5.2 Join Room

Client:

```json
{
  "type": "join_room",
  "requestId": "req-003",
  "payload": {
    "roomId": "room-1234",
    "playerId": "player-b7c1",
    "playerName": "Bob"
  }
}
```

Server:

- broadcast updated `room_state` to all room members

### 5.3 Leave Room

Client:

```json
{
  "type": "leave_room",
  "requestId": "req-004",
  "payload": {
    "roomId": "room-1234",
    "playerId": "player-b7c1"
  }
}
```

Server:

- broadcast updated `room_state`

### 5.4 Set Ready

Client:

```json
{
  "type": "set_ready",
  "requestId": "req-005",
  "payload": {
    "roomId": "room-1234",
    "playerId": "player-a8f2",
    "ready": true
  }
}
```

Server:

- broadcast updated `room_state`

### 5.5 Start Game

Client:

```json
{
  "type": "start_game",
  "requestId": "req-006",
  "payload": {
    "roomId": "room-1234",
    "playerId": "player-a8f2"
  }
}
```

Server:

1. Validate host and ready requirements
2. Create game state
3. Broadcast `game_started`
4. Broadcast initial `game_snapshot`

Example:

```json
{
  "type": "game_started",
  "requestId": "req-006",
  "timestamp": "2026-03-29T21:01:00Z",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001"
  }
}
```

## 6. Gameplay Action Messages

Gameplay actions use a generic wrapper so the transport stays stable even when new actions are added.

### 6.1 Generic Action Envelope

Client:

```json
{
  "type": "player_action",
  "requestId": "req-100",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "action": {
      "actionType": "ROLL_DICE",
      "data": {}
    }
  }
}
```

### 6.2 Supported V1 Action Types

- `PLACE_INITIAL_SETTLEMENT`
- `PLACE_INITIAL_ROAD`
- `ROLL_DICE`
- `BUILD_ROAD`
- `BUILD_SETTLEMENT`
- `BUILD_CITY`
- `BUY_DEVELOPMENT_CARD`
- `PLAY_KNIGHT`
- `PLAY_ROAD_BUILDING`
- `PLAY_YEAR_OF_PLENTY`
- `PLAY_MONOPOLY`
- `TRADE_WITH_BANK`
- `PROPOSE_PLAYER_TRADE`
- `RESPOND_PLAYER_TRADE`
- `MOVE_ROBBER`
- `STEAL_RESOURCE`
- `END_TURN`

### 6.3 Action Payload Examples

#### Build Road

```json
{
  "type": "player_action",
  "requestId": "req-101",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "action": {
      "actionType": "BUILD_ROAD",
      "data": {
        "edgeId": "E-42"
      }
    }
  }
}
```

#### Build Settlement

```json
{
  "type": "player_action",
  "requestId": "req-102",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "action": {
      "actionType": "BUILD_SETTLEMENT",
      "data": {
        "intersectionId": "I-13"
      }
    }
  }
}
```

#### Move Robber

```json
{
  "type": "player_action",
  "requestId": "req-103",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "action": {
      "actionType": "MOVE_ROBBER",
      "data": {
        "hexId": "H-09"
      }
    }
  }
}
```

#### Trade With Bank

```json
{
  "type": "player_action",
  "requestId": "req-104",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "action": {
      "actionType": "TRADE_WITH_BANK",
      "data": {
        "give": {
          "resource": "WOOD",
          "amount": 4
        },
        "receive": {
          "resource": "BRICK",
          "amount": 1
        }
      }
    }
  }
}
```

#### Player Trade Proposal

```json
{
  "type": "player_action",
  "requestId": "req-105",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "action": {
      "actionType": "PROPOSE_PLAYER_TRADE",
      "data": {
        "targetPlayerId": "player-b7c1",
        "offer": {
          "WOOD": 1
        },
        "request": {
          "ORE": 1
        }
      }
    }
  }
}
```

## 7. Server Push Messages

### 7.1 Game Snapshot

The snapshot is the main source of truth for client rendering.

```json
{
  "type": "game_snapshot",
  "timestamp": "2026-03-29T21:02:00Z",
  "payload": {
    "roomId": "room-1234",
    "gameId": "game-001",
    "version": 18,
    "phase": "IN_PROGRESS",
    "turn": {
      "currentPlayerId": "player-a8f2",
      "turnNumber": 6,
      "stage": "MAIN"
    },
    "board": {
      "hexes": [],
      "intersections": [],
      "edges": [],
      "robberHexId": "H-09"
    },
    "players": [
      {
        "playerId": "player-a8f2",
        "playerName": "Alice",
        "seat": 0,
        "resourceCount": 5,
        "resources": {
          "WOOD": 2,
          "BRICK": 1,
          "SHEEP": 1,
          "WHEAT": 1,
          "ORE": 0
        },
        "roadsRemaining": 13,
        "settlementsRemaining": 4,
        "citiesRemaining": 4,
        "developmentCardCount": 2,
        "visibleVictoryPoints": 3,
        "totalVictoryPoints": 3,
        "hasLongestRoad": false,
        "hasLargestArmy": false
      }
    ],
    "dice": {
      "lastRoll": 8,
      "dieA": 3,
      "dieB": 5
    },
    "pendingAction": null,
    "legalActions": [
      {
        "actionType": "BUILD_ROAD",
        "targets": ["E-40", "E-41", "E-42"]
      },
      {
        "actionType": "END_TURN",
        "targets": []
      }
    ],
    "winner": null
  }
}
```

### 7.2 Room State

Used before the match starts and when lobby membership changes.

```json
{
  "type": "room_state",
  "timestamp": "2026-03-29T21:00:10Z",
  "payload": {
    "room": {
      "roomId": "room-1234",
      "status": "WAITING",
      "hostPlayerId": "player-a8f2",
      "maxPlayers": 4,
      "players": [
        {
          "playerId": "player-a8f2",
          "playerName": "Alice",
          "seat": 0,
          "ready": true,
          "connected": true
        },
        {
          "playerId": "player-b7c1",
          "playerName": "Bob",
          "seat": 1,
          "ready": false,
          "connected": true
        }
      ]
    }
  }
}
```

### 7.3 Game Event

Use event messages for logs and lightweight animation hooks.

```json
{
  "type": "game_event",
  "timestamp": "2026-03-29T21:02:03Z",
  "payload": {
    "eventType": "ROAD_BUILT",
    "roomId": "room-1234",
    "gameId": "game-001",
    "playerId": "player-a8f2",
    "message": "Alice built a road.",
    "data": {
      "edgeId": "E-42"
    }
  }
}
```

### 7.4 Turn Prompt

Optional helper message for simpler UI guidance.

```json
{
  "type": "turn_prompt",
  "timestamp": "2026-03-29T21:02:05Z",
  "payload": {
    "playerId": "player-a8f2",
    "stage": "MAIN",
    "allowedActionTypes": [
      "BUILD_ROAD",
      "BUILD_SETTLEMENT",
      "BUILD_CITY",
      "BUY_DEVELOPMENT_CARD",
      "TRADE_WITH_BANK",
      "PROPOSE_PLAYER_TRADE",
      "END_TURN"
    ]
  }
}
```

### 7.5 Action Accepted

Optional acknowledgement for optimistic UI coordination.

```json
{
  "type": "action_accepted",
  "requestId": "req-101",
  "timestamp": "2026-03-29T21:02:03Z",
  "payload": {
    "gameId": "game-001",
    "version": 18
  }
}
```

### 7.6 Action Rejected

Used when a command is invalid.

```json
{
  "type": "action_rejected",
  "requestId": "req-101",
  "timestamp": "2026-03-29T21:02:02Z",
  "payload": {
    "code": "INVALID_BUILD_LOCATION",
    "message": "The selected edge is not connected to your existing network.",
    "details": {
      "actionType": "BUILD_ROAD",
      "edgeId": "E-42"
    }
  }
}
```

### 7.7 Game Over

```json
{
  "type": "game_over",
  "timestamp": "2026-03-29T21:30:00Z",
  "payload": {
    "gameId": "game-001",
    "winnerPlayerId": "player-a8f2",
    "finalStandings": [
      {
        "playerId": "player-a8f2",
        "victoryPoints": 10
      },
      {
        "playerId": "player-b7c1",
        "victoryPoints": 8
      }
    ]
  }
}
```

## 8. Snapshot Data Contracts

### 8.1 Board Contract

Board data should be stable and ID-driven.

```json
{
  "hexes": [
    {
      "hexId": "H-01",
      "resourceType": "WOOD",
      "numberToken": 11,
      "x": 100,
      "y": 100
    }
  ],
  "intersections": [
    {
      "intersectionId": "I-01",
      "x": 90,
      "y": 70,
      "building": {
        "ownerPlayerId": "player-a8f2",
        "buildingType": "SETTLEMENT"
      }
    }
  ],
  "edges": [
    {
      "edgeId": "E-01",
      "fromIntersectionId": "I-01",
      "toIntersectionId": "I-02",
      "roadOwnerPlayerId": "player-a8f2"
    }
  ],
  "robberHexId": "H-10"
}
```

### 8.2 Player Contract

For v1, the server may send each client full information for all players in local development mode, but the production-safe target should be:

- full resource detail only for the requesting player
- public summary only for opponents

Recommended long-term shape:

```json
{
  "playerId": "player-a8f2",
  "playerName": "Alice",
  "seat": 0,
  "resourceCount": 5,
  "resources": {
    "WOOD": 2,
    "BRICK": 1,
    "SHEEP": 1,
    "WHEAT": 1,
    "ORE": 0
  },
  "developmentCardCount": 2,
  "visibleVictoryPoints": 3,
  "totalVictoryPoints": 3,
  "roadsRemaining": 13,
  "settlementsRemaining": 4,
  "citiesRemaining": 4,
  "hasLongestRoad": false,
  "hasLargestArmy": false
}
```

Opponent-safe version:

```json
{
  "playerId": "player-b7c1",
  "playerName": "Bob",
  "seat": 1,
  "resourceCount": 4,
  "developmentCardCount": 1,
  "visibleVictoryPoints": 2,
  "totalVictoryPoints": 2,
  "roadsRemaining": 12,
  "settlementsRemaining": 4,
  "citiesRemaining": 4,
  "hasLongestRoad": false,
  "hasLargestArmy": false
}
```

## 9. Error Codes

Suggested first-pass error codes:

- `INVALID_MESSAGE`
- `UNAUTHORIZED_PLAYER`
- `ROOM_NOT_FOUND`
- `GAME_NOT_FOUND`
- `ROOM_FULL`
- `PLAYER_ALREADY_IN_ROOM`
- `GAME_ALREADY_STARTED`
- `NOT_ROOM_HOST`
- `NOT_ALL_PLAYERS_READY`
- `NOT_YOUR_TURN`
- `INVALID_PHASE`
- `INVALID_ACTION`
- `INSUFFICIENT_RESOURCES`
- `INVALID_BUILD_LOCATION`
- `INVALID_TRADE`
- `INVALID_ROBBER_TARGET`
- `INVALID_DEVELOPMENT_CARD_USAGE`
- `INTERNAL_ERROR`

## 10. Versioning Rules

V1 versioning approach:

- include a monotonically increasing `version` in `game_snapshot`
- clients should replace local state only when receiving a newer version
- action requests should reference `gameId`, not necessarily version

Later, if needed:

- add protocol version negotiation in `hello`
- add server replay or delta events

## 11. Frontend Handling Rules

The client should:

1. Keep one active WebSocket connection
2. Send `hello` immediately after connection
3. Store `room_state` separately from `game_snapshot`
4. Treat `game_snapshot` as authoritative
5. Use `game_event` for logs and animation triggers only
6. Show `action_rejected` errors prominently
7. Clear stale pending interactions when snapshot version changes

## 12. Backend Handling Rules

The server should:

1. Validate all incoming message envelopes
2. Bind player identity to the connected session
3. Reject malformed or out-of-room actions
4. Execute only validated commands against `game-core`
5. Broadcast the newest snapshot after accepted state changes
6. Avoid trusting any client-side legality hints

## 13. Recommended Next Implementation

The next coding step should be:

1. Create Java DTOs for the WebSocket envelope and key payloads
2. Implement `hello`, `create_room`, and `join_room`
3. Define `GameActionType` and action payload classes
4. Expand `GameState` to align with the snapshot contract
