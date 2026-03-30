# KaTanDao

KaTanDao is a base-game Settlers of Catan project with:

- Java backend
- Web client
- Shared domain-oriented game architecture

## Repository Structure

```text
KaTanDao/
  docs/
  game-core/
  server/
  web-client/
  pom.xml
```

## Modules

- `game-core`: pure Java rule engine and domain model
- `server`: Spring Boot multiplayer backend
- `web-client`: React + TypeScript browser client

## Planned Stack

- Java 21
- Maven
- Spring Boot
- WebSocket
- React
- TypeScript
- Vite

## Next Step

Build the game protocol and core game state model first, then connect the frontend to the server over WebSocket.
