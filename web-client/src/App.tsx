import { useEffect, useMemo, useRef, useState } from "react";
import { GameSocket } from "./lib/gameSocket";
import type {
  ClientEnvelope,
  GameActionType,
  GameSnapshotPayload,
  LegalActionPayload,
  RoomState,
  ServerEnvelope
} from "./protocol/messages";

const socketUrl = "ws://127.0.0.1:8080/ws/game";

const hexCenters: Record<string, { x: number; y: number }> = {
  "H-04": { x: 320, y: 120 },
  "H-01": { x: 440, y: 200 },
  "H-02": { x: 440, y: 340 },
  "H-03": { x: 320, y: 420 }
};

const intersectionPositions: Record<string, { x: number; y: number }> = {
  "I-01": { x: 280, y: 100 },
  "I-02": { x: 420, y: 100 },
  "I-03": { x: 500, y: 240 },
  "I-04": { x: 420, y: 380 },
  "I-05": { x: 280, y: 380 },
  "I-06": { x: 200, y: 240 }
};

const resourcePalette: Record<string, { fill: string; tint: string; label: string }> = {
  WOOD: { fill: "#527e47", tint: "#eaf4df", label: "Wood" },
  BRICK: { fill: "#b55c40", tint: "#feeee8", label: "Brick" },
  SHEEP: { fill: "#8abf62", tint: "#f3fbe7", label: "Sheep" },
  WHEAT: { fill: "#d2af45", tint: "#fff7dd", label: "Wheat" },
  ORE: { fill: "#687485", tint: "#eef2f8", label: "Ore" }
};

const playerColors = ["#d97706", "#2563eb", "#059669", "#b91c1c"];

function createRequestId() {
  return `req-${Math.random().toString(36).slice(2, 10)}`;
}

function createPlayerId() {
  return `player-${Math.random().toString(36).slice(2, 8)}`;
}

function initialProfile() {
  const savedPlayerId = window.sessionStorage.getItem("katandao.playerId");
  const savedPlayerName = window.localStorage.getItem("katandao.playerName");
  return {
    playerId: savedPlayerId ?? createPlayerId(),
    playerName: savedPlayerName ?? `Player-${Math.random().toString(36).slice(2, 5)}`
  };
}

function hexPoints(x: number, y: number, radius: number) {
  return Array.from({ length: 6 }, (_, index) => {
    const angle = ((60 * index - 30) * Math.PI) / 180;
    return `${x + radius * Math.cos(angle)},${y + radius * Math.sin(angle)}`;
  }).join(" ");
}

function playerColor(playerId: string, orderedIds: string[]) {
  const index = orderedIds.indexOf(playerId);
  return playerColors[index >= 0 ? index % playerColors.length : 0];
}

function actionLabel(actionType: GameActionType) {
  return actionType
    .replace("PLACE_INITIAL_", "SETUP ")
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (match: string) => match.toUpperCase());
}

export default function App() {
  const [{ playerId, playerName }, setProfile] = useState(initialProfile);
  const [draftName, setDraftName] = useState(playerName);
  const [joinRoomId, setJoinRoomId] = useState("");
  const [connectionState, setConnectionState] = useState<"disconnected" | "connecting" | "connected">("disconnected");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [roomState, setRoomState] = useState<RoomState | null>(null);
  const [gameSnapshot, setGameSnapshot] = useState<GameSnapshotPayload | null>(null);
  const [gameId, setGameId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedActionType, setSelectedActionType] = useState<GameActionType | null>(null);
  const socketRef = useRef<GameSocket | null>(null);

  const currentRoomId = roomState?.roomId ?? gameSnapshot?.roomId ?? "";
  const selfRoomPlayer = roomState?.players.find((player) => player.playerId === playerId) ?? null;
  const isHost = roomState?.hostPlayerId === playerId;
  const orderedPlayerIds = useMemo(
    () => gameSnapshot?.state.players.map((player) => player.playerId) ?? roomState?.players.map((player) => player.playerId) ?? [],
    [gameSnapshot, roomState]
  );
  const legalActions = useMemo(() => gameSnapshot?.legalActions ?? [], [gameSnapshot]);
  const selectedLegalAction = useMemo(
    () => legalActions.find((action) => action.actionType === selectedActionType) ?? null,
    [legalActions, selectedActionType]
  );
  const readyMissingCount = roomState ? roomState.players.filter((player) => !player.ready).length : 0;
  const currentPlayerName = roomState?.players.find((player) => player.playerId === gameSnapshot?.turn.currentPlayerId)?.playerName
    ?? gameSnapshot?.turn.currentPlayerId
    ?? "No one";

  useEffect(() => {
    window.sessionStorage.setItem("katandao.playerId", playerId);
    window.localStorage.setItem("katandao.playerName", playerName);
  }, [playerId, playerName]);

  useEffect(() => {
    setSelectedActionType(null);
  }, [gameSnapshot?.version]);

  useEffect(() => {
    const socket = new GameSocket();
    socketRef.current = socket;
    setConnectionState("connecting");

    socket.connect(
      socketUrl,
      (message) => handleServerMessage(message),
      {
        onOpen: () => {
          setConnectionState("connected");
          setErrorMessage(null);
          send(
            {
              type: "hello",
              requestId: createRequestId(),
              payload: {
                playerId,
                playerName
              }
            },
            socket
          );
        },
        onClose: () => setConnectionState("disconnected"),
        onError: () => setErrorMessage("Unable to connect to the game server.")
      }
    );

    return () => socket.close();
  }, [playerId, playerName]);

  function send(message: ClientEnvelope, socketOverride?: GameSocket) {
    try {
      (socketOverride ?? socketRef.current)?.send(message);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to send message.");
    }
  }

  function handleServerMessage(message: ServerEnvelope) {
    switch (message.type) {
      case "hello_ack":
        setSessionId(message.payload.sessionId);
        setErrorMessage(null);
        break;
      case "room_state":
        setRoomState(message.payload.room);
        setErrorMessage(null);
        break;
      case "game_started":
        setGameId(message.payload.gameId);
        setErrorMessage(null);
        break;
      case "game_snapshot":
        setGameSnapshot(message.payload);
        setGameId(message.payload.gameId);
        setErrorMessage(null);
        break;
      case "action_accepted":
        setErrorMessage(null);
        break;
      case "action_rejected":
        setErrorMessage(message.payload.message);
        break;
    }
  }

  function updateName() {
    const nextName = draftName.trim();
    if (!nextName) {
      setErrorMessage("Player name is required.");
      return;
    }
    setProfile((current) => ({ playerId: current.playerId, playerName: nextName }));
  }

  function resetPlayerIdentity() {
    const nextPlayerId = createPlayerId();
    setProfile({
      playerId: nextPlayerId,
      playerName: draftName.trim() || playerName
    });
    setSessionId(null);
    setRoomState(null);
    setGameSnapshot(null);
    setGameId(null);
    setSelectedActionType(null);
    setErrorMessage(null);
  }

  function createRoom() {
    send({
      type: "create_room",
      requestId: createRequestId(),
      payload: {
        playerId,
        playerName,
        config: { maxPlayers: 4 }
      }
    });
  }

  function joinRoom() {
    if (!joinRoomId.trim()) {
      setErrorMessage("Please enter a room ID.");
      return;
    }
    send({
      type: "join_room",
      requestId: createRequestId(),
      payload: {
        roomId: joinRoomId.trim(),
        playerId,
        playerName
      }
    });
  }

  function toggleReady() {
    if (!currentRoomId || !selfRoomPlayer) {
      return;
    }
    send({
      type: "set_ready",
      requestId: createRequestId(),
      payload: {
        roomId: currentRoomId,
        playerId,
        ready: !selfRoomPlayer.ready
      }
    });
  }

  function startGame() {
    if (!currentRoomId) {
      return;
    }
    send({
      type: "start_game",
      requestId: createRequestId(),
      payload: {
        roomId: currentRoomId,
        playerId
      }
    });
  }

  function selectAction(action: LegalActionPayload) {
    if (action.targets.length === 0) {
      sendAction(action.actionType);
      return;
    }
    setSelectedActionType((current) => (current === action.actionType ? null : action.actionType));
  }

  function sendAction(actionType: GameActionType, target?: string) {
    if (!currentRoomId || !gameId) {
      return;
    }

    const data =
      actionType === "PLACE_INITIAL_SETTLEMENT" || actionType === "BUILD_SETTLEMENT" || actionType === "BUILD_CITY"
        ? { intersectionId: target }
        : actionType === "PLACE_INITIAL_ROAD" || actionType === "BUILD_ROAD"
          ? { edgeId: target }
          : {};

    send({
      type: "player_action",
      requestId: createRequestId(),
      payload: {
        roomId: currentRoomId,
        gameId,
        playerId,
        action: {
          actionType,
          data
        }
      }
    });
  }

  function targetIsSelectable(targetId: string) {
    return Boolean(selectedLegalAction?.targets.includes(targetId));
  }

  const board = gameSnapshot?.state.board;
  const players = gameSnapshot?.state.players ?? [];
  const selfState = players.find((player) => player.playerId === playerId) ?? null;

  if (!gameSnapshot) {
    return (
      <main className="lobby-shell">
        <section className="lobby-hero">
          <div>
            <p className="eyebrow">KaTanDao</p>
            <h1>Prepare The Table</h1>
            <p className="lobby-copy">
              Build the room, bring in at least three players, ready everyone up, and then launch the match.
            </p>
          </div>
          <div className={`connection-chip is-${connectionState}`}>
            <span className="status-dot" />
            {connectionState}
          </div>
        </section>

        {errorMessage ? <div className="alert-banner">{errorMessage}</div> : null}

        <section className="lobby-grid">
          <section className="panel">
            <div className="panel-heading">
              <h2>Your Seat</h2>
              <span className="subtle">{sessionId ? "connected" : "handshaking"}</span>
            </div>
            <label className="field">
              <span>Player name</span>
              <input value={draftName} onChange={(event) => setDraftName(event.target.value)} />
            </label>
            <div className="button-row">
              <button onClick={updateName}>Update Name</button>
              <button className="ghost-button" onClick={resetPlayerIdentity}>New Player ID</button>
            </div>
            <div className="lobby-meta">
              <p><strong>Name:</strong> {playerName}</p>
              <p><strong>Player ID:</strong> {playerId}</p>
              <p><strong>Session:</strong> {sessionId ?? "pending"}</p>
            </div>
          </section>

          <section className="panel">
            <div className="panel-heading">
              <h2>Room</h2>
              <span className="subtle">{roomState?.status ?? "not joined"}</span>
            </div>
            <div className="button-row">
              <button onClick={createRoom} disabled={connectionState !== "connected"}>Create Room</button>
            </div>
            <label className="field">
              <span>Join by room ID</span>
              <input value={joinRoomId} onChange={(event) => setJoinRoomId(event.target.value)} placeholder="room-xxxx" />
            </label>
            <div className="button-row">
              <button onClick={joinRoom} disabled={connectionState !== "connected"}>Join Room</button>
              <button onClick={toggleReady} disabled={!selfRoomPlayer}>{selfRoomPlayer?.ready ? "Unready" : "Ready"}</button>
            </div>
            <button
              onClick={startGame}
              disabled={!isHost || roomState?.status !== "WAITING" || (roomState?.players.length ?? 0) < 3 || readyMissingCount > 0}
            >
              Start Match
            </button>
            <div className="lobby-meta">
              <p><strong>Room:</strong> {roomState?.roomId ?? "not joined"}</p>
              <p><strong>Need to start:</strong> {Math.max(0, 3 - (roomState?.players.length ?? 0))} more player(s)</p>
              <p><strong>Still not ready:</strong> {readyMissingCount}</p>
            </div>
            <ul className="player-stack">
              {roomState?.players.map((player) => (
                <li key={player.playerId} className={player.playerId === playerId ? "is-self" : ""}>
                  <span className="player-dot" style={{ backgroundColor: playerColor(player.playerId, orderedPlayerIds) }} />
                  <div>
                    <strong>{player.playerName}</strong>
                    <p>Seat {player.seat} · {player.ready ? "Ready" : "Waiting"}</p>
                  </div>
                </li>
              )) ?? <li>No players yet.</li>}
            </ul>
          </section>
        </section>
      </main>
    );
  }

  return (
    <main className="game-shell">
      <header className="game-topbar">
        <div className="topbar-block">
          <p className="eyebrow">KaTanDao Match</p>
          <h1 className="title">Room {currentRoomId}</h1>
        </div>
        <div className="topbar-strip">
          <div className="hud-chip">
            <span className="hud-label">Turn</span>
            <strong>{currentPlayerName}</strong>
          </div>
          <div className="hud-chip">
            <span className="hud-label">Stage</span>
            <strong>{gameSnapshot.turn.stage}</strong>
          </div>
          <div className="hud-chip">
            <span className="hud-label">Roll</span>
            <strong>{gameSnapshot.state.dice.lastRoll ?? "--"}</strong>
          </div>
        </div>
      </header>

      {errorMessage ? <div className="alert-banner">{errorMessage}</div> : null}

      <section className="game-board-layout">
        <aside className="left-hud">
          <section className="panel compact-panel">
            <div className="panel-heading">
              <h2>Your Hand</h2>
              <span className="subtle">{playerName}</span>
            </div>
            {selfState ? (
              <div className="resource-hand">
                {Object.entries(selfState.resources).map(([resource, amount]) => {
                  const color = resourcePalette[resource];
                  return (
                    <div key={resource} className="resource-card" style={{ background: color.tint }}>
                      <span className="resource-pill" style={{ background: color.fill }} />
                      <div>
                        <strong>{color.label}</strong>
                        <p>{amount}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="subtle">Waiting for your player state.</p>
            )}
          </section>

          <section className="panel compact-panel">
            <div className="panel-heading">
              <h2>Actions</h2>
              <span className="subtle">{legalActions.length} available</span>
            </div>
            <div className="action-toolbar">
              {legalActions.map((action) => (
                <button
                  key={action.actionType}
                  className={selectedActionType === action.actionType ? "action-button is-selected" : "action-button"}
                  onClick={() => selectAction(action)}
                >
                  {actionLabel(action.actionType)}
                </button>
              ))}
            </div>
            {selectedLegalAction ? (
              <p className="selection-hint">
                Selected: {actionLabel(selectedLegalAction.actionType)}. Click the glowing target on the board.
              </p>
            ) : (
              <p className="selection-hint">Choose an action, then use the board directly.</p>
            )}
          </section>
        </aside>

        <section className="board-stage">
          <div className="board-stage-inner">
            <svg viewBox="0 0 700 520" className="board-svg" role="img" aria-label="KaTanDao board">
              <defs>
                <radialGradient id="seaGlow" cx="50%" cy="30%" r="75%">
                  <stop offset="0%" stopColor="#c7e7f3" />
                  <stop offset="100%" stopColor="#7ab1c8" />
                </radialGradient>
              </defs>
              <rect x="18" y="18" width="664" height="484" rx="42" fill="url(#seaGlow)" />
              <path d="M 110 420 Q 220 310 290 335 T 560 360 L 590 502 L 100 502 Z" fill="#6c9e57" opacity="0.45" />
              {board?.hexes.map((hex) => {
                const center = hexCenters[hex.hexId] ?? { x: 350, y: 260 };
                const color = resourcePalette[hex.resourceType];
                return (
                  <g key={hex.hexId}>
                    <polygon points={hexPoints(center.x, center.y, 82)} fill={color.fill} stroke="rgba(32, 24, 18, 0.22)" strokeWidth="5" />
                    <circle cx={center.x} cy={center.y} r="28" fill="rgba(255,255,255,0.88)" />
                    <text x={center.x} y={center.y - 10} textAnchor="middle" className="hex-label">{color.label}</text>
                    <text x={center.x} y={center.y + 12} textAnchor="middle" className="hex-number">{hex.numberToken}</text>
                  </g>
                );
              })}

              {board?.edges.map((edge) => {
                const from = intersectionPositions[edge.fromIntersectionId];
                const to = intersectionPositions[edge.toIntersectionId];
                const selectable = targetIsSelectable(edge.edgeId);
                const roadColor = edge.roadOwnerPlayerId ? playerColor(edge.roadOwnerPlayerId, orderedPlayerIds) : "#e8d8b5";
                return (
                  <line
                    key={edge.edgeId}
                    x1={from.x}
                    y1={from.y}
                    x2={to.x}
                    y2={to.y}
                    className={selectable ? "board-edge is-selectable" : "board-edge"}
                    stroke={roadColor}
                    strokeWidth={edge.roadOwnerPlayerId ? 14 : 9}
                    onClick={() => selectable && selectedActionType && sendAction(selectedActionType, edge.edgeId)}
                  />
                );
              })}

              {board?.intersections.map((intersection) => {
                const position = intersectionPositions[intersection.intersectionId];
                const selectable = targetIsSelectable(intersection.intersectionId);
                const ownerColor = intersection.ownerPlayerId ? playerColor(intersection.ownerPlayerId, orderedPlayerIds) : "#fff7e6";
                return (
                  <g
                    key={intersection.intersectionId}
                    className={selectable ? "board-node is-selectable" : "board-node"}
                    onClick={() => selectable && selectedActionType && sendAction(selectedActionType, intersection.intersectionId)}
                  >
                    <circle cx={position.x} cy={position.y} r={intersection.buildingType === "CITY" ? 19 : 15} fill={ownerColor} stroke="#4c341f" strokeWidth="4" />
                    <text x={position.x} y={position.y + 36} textAnchor="middle" className="node-label">
                      {intersection.intersectionId}
                    </text>
                  </g>
                );
              })}
            </svg>
          </div>
        </section>

        <aside className="right-hud">
          <section className="panel compact-panel">
            <div className="panel-heading">
              <h2>Table</h2>
              <span className="subtle">{players.length} players</span>
            </div>
            <div className="table-stack">
              {players.map((player) => {
                const playerNameLabel = roomState?.players.find((item) => item.playerId === player.playerId)?.playerName ?? player.playerId;
                return (
                  <article key={player.playerId} className={player.playerId === playerId ? "table-player is-self" : "table-player"}>
                    <div className="player-badge" style={{ backgroundColor: playerColor(player.playerId, orderedPlayerIds) }} />
                    <div>
                      <strong>{playerNameLabel}</strong>
                      <p>VP {player.victoryPoints} · Roads {player.roadsRemaining}</p>
                      <p>Settlements {player.settlementsRemaining} · Cities {player.citiesRemaining}</p>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>

          <section className="panel compact-panel">
            <div className="panel-heading">
              <h2>Match</h2>
              <span className="subtle">Live</span>
            </div>
            <div className="lobby-meta">
              <p><strong>Game:</strong> {gameId}</p>
              <p><strong>Version:</strong> {gameSnapshot.version}</p>
              <p><strong>Phase:</strong> {gameSnapshot.phase}</p>
              <p><strong>Your session:</strong> {sessionId ?? "pending"}</p>
            </div>
          </section>
        </aside>
      </section>
    </main>
  );
}
