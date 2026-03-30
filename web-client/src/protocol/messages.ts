export type ClientMessageType =
  | "hello"
  | "create_room"
  | "join_room"
  | "leave_room"
  | "set_ready"
  | "start_game"
  | "player_action";

export type ServerMessageType =
  | "hello_ack"
  | "room_state"
  | "game_started"
  | "game_snapshot"
  | "game_event"
  | "turn_prompt"
  | "action_accepted"
  | "action_rejected"
  | "game_over";

export interface Envelope<TType extends string, TPayload> {
  type: TType;
  requestId?: string;
  timestamp?: string;
  payload: TPayload;
}

export interface HelloPayload {
  playerId: string;
  playerName: string;
}

export interface RoomConfigPayload {
  maxPlayers?: number;
}

export interface CreateRoomPayload {
  playerId: string;
  playerName: string;
  config?: RoomConfigPayload;
}

export interface JoinRoomPayload {
  roomId: string;
  playerId: string;
  playerName: string;
}

export interface SetReadyPayload {
  roomId: string;
  playerId: string;
  ready: boolean;
}

export interface StartGamePayload {
  roomId: string;
  playerId: string;
}

export type GameActionType =
  | "PLACE_INITIAL_SETTLEMENT"
  | "PLACE_INITIAL_ROAD"
  | "ROLL_DICE"
  | "BUILD_ROAD"
  | "BUILD_SETTLEMENT"
  | "BUILD_CITY"
  | "BUY_DEVELOPMENT_CARD"
  | "PLAY_KNIGHT"
  | "PLAY_ROAD_BUILDING"
  | "PLAY_YEAR_OF_PLENTY"
  | "PLAY_MONOPOLY"
  | "TRADE_WITH_BANK"
  | "PROPOSE_PLAYER_TRADE"
  | "RESPOND_PLAYER_TRADE"
  | "MOVE_ROBBER"
  | "STEAL_RESOURCE"
  | "END_TURN";

export interface PlayerActionPayload {
  roomId: string;
  gameId: string;
  playerId: string;
  action: {
    actionType: GameActionType;
    data: Record<string, unknown>;
  };
}

export interface RoomPlayer {
  playerId: string;
  playerName: string;
  seat: number;
  ready: boolean;
  connected: boolean;
}

export interface RoomState {
  roomId: string;
  status: "WAITING" | "IN_GAME" | "CLOSED";
  hostPlayerId: string;
  maxPlayers: number;
  players: RoomPlayer[];
}

export interface RoomStatePayload {
  room: RoomState;
}

export interface HelloAckPayload {
  playerId: string;
  sessionId: string;
  serverVersion: string;
}

export interface GameStartedPayload {
  roomId: string;
  gameId: string;
}

export interface HexTileState {
  hexId: string;
  resourceType: "WOOD" | "BRICK" | "SHEEP" | "WHEAT" | "ORE" | "DESERT";
  numberToken: number;
  q: number;
  r: number;
  adjacentIntersectionIds: string[];
}

export interface PortState {
  portId: string;
  tradeType: string;
  ratio: number;
  x: number;
  y: number;
  rotationDegrees: number;
}

export interface IntersectionState {
  intersectionId: string;
  ownerPlayerId: string | null;
  buildingType: "SETTLEMENT" | "CITY" | null;
  x: number;
  y: number;
}

export interface EdgeState {
  edgeId: string;
  fromIntersectionId: string;
  toIntersectionId: string;
  roadOwnerPlayerId: string | null;
}

export interface PlayerState {
  playerId: string;
  roadsRemaining: number;
  settlementsRemaining: number;
  citiesRemaining: number;
  victoryPoints: number;
  resources: Record<string, number>;
  lastPlacedIntersectionId: string | null;
}

export interface LegalActionPayload {
  actionType: GameActionType;
  targets: string[];
}

export interface GameSnapshotPayload {
  roomId: string;
  gameId: string;
  version: number;
  phase: string;
  turn: {
    currentPlayerId: string;
    turnNumber: number;
    stage: string;
  };
  legalActions: LegalActionPayload[];
  winner: string | null;
  state: {
    gameId: string;
    phase: string;
    board: {
      hexes: HexTileState[];
      ports: PortState[];
      intersections: IntersectionState[];
      edges: EdgeState[];
    };
    players: PlayerState[];
    turn: {
      currentPlayerId: string;
      turnNumber: number;
      stage: string;
    };
    dice: {
      lastRoll: number | null;
      dieA: number | null;
      dieB: number | null;
      rolledThisTurn: boolean;
    };
    version: number;
    winnerPlayerId: string | null;
  };
}

export interface ActionAcceptedPayload {
  gameId: string;
  version: number;
}

export interface ErrorPayload {
  code: string;
  message: string;
  details: Record<string, unknown>;
}

export type ClientEnvelope =
  | Envelope<"hello", HelloPayload>
  | Envelope<"create_room", CreateRoomPayload>
  | Envelope<"join_room", JoinRoomPayload>
  | Envelope<"set_ready", SetReadyPayload>
  | Envelope<"start_game", StartGamePayload>
  | Envelope<"player_action", PlayerActionPayload>;

export type ServerEnvelope =
  | Envelope<"hello_ack", HelloAckPayload>
  | Envelope<"room_state", RoomStatePayload>
  | Envelope<"game_started", GameStartedPayload>
  | Envelope<"game_snapshot", GameSnapshotPayload>
  | Envelope<"action_accepted", ActionAcceptedPayload>
  | Envelope<"action_rejected", ErrorPayload>;
