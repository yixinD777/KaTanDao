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

const resourcePalette: Record<string, { fill: string; tint: string; label: string }> = {
  WOOD: { fill: "#527e47", tint: "#eaf4df", label: "Wood" },
  BRICK: { fill: "#b55c40", tint: "#feeee8", label: "Brick" },
  SHEEP: { fill: "#8abf62", tint: "#f3fbe7", label: "Sheep" },
  WHEAT: { fill: "#d2af45", tint: "#fff7dd", label: "Wheat" },
  ORE: { fill: "#687485", tint: "#eef2f8", label: "Ore" },
  DESERT: { fill: "#29211b", tint: "#ece7dd", label: "Desert" }
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

function hexCenterFromAxial(q: number, r: number) {
  const radius = 82;
  return {
    x: 470 + Math.sqrt(3) * radius * (q + r / 2),
    y: 320 + radius * 1.5 * r
  };
}

function fallbackHexCenter(index: number) {
  const fallbackCenters = [
    { x: 350, y: 110 },
    { x: 470, y: 110 },
    { x: 590, y: 110 },
    { x: 290, y: 215 },
    { x: 410, y: 215 },
    { x: 530, y: 215 },
    { x: 650, y: 215 },
    { x: 230, y: 320 },
    { x: 350, y: 320 },
    { x: 470, y: 320 },
    { x: 590, y: 320 },
    { x: 710, y: 320 },
    { x: 290, y: 425 },
    { x: 410, y: 425 },
    { x: 530, y: 425 },
    { x: 650, y: 425 },
    { x: 770, y: 425 },
    { x: 470, y: 530 },
    { x: 590, y: 530 }
  ];
  return fallbackCenters[index] ?? { x: 470, y: 320 };
}

function isFinitePoint(point: { x: number; y: number }) {
  return Number.isFinite(point.x) && Number.isFinite(point.y);
}

function boardViewBox(snapshot: GameSnapshotPayload | null) {
  const board = snapshot?.state.board;
  if (!board) {
    return "0 0 940 720";
  }

  const points: Array<{ x: number; y: number }> = [];
  const hexRadius = 92;
  for (const [index, hex] of board.hexes.entries()) {
    const center = typeof hex.q === "number" && typeof hex.r === "number"
      ? hexCenterFromAxial(hex.q, hex.r)
      : fallbackHexCenter(index);
    const bounds = [
      { x: center.x - hexRadius, y: center.y - hexRadius },
      { x: center.x + hexRadius, y: center.y + hexRadius }
    ].filter(isFinitePoint);
    points.push(...bounds);
  }

  for (const intersection of board.intersections) {
    const point = {
      x: Number(intersection.x),
      y: Number(intersection.y)
    };
    if (isFinitePoint(point)) {
      points.push(point);
    }
  }

  for (const port of board.ports ?? []) {
    const bounds = [
      { x: Number(port.x) - 60, y: Number(port.y) - 60 },
      { x: Number(port.x) + 60, y: Number(port.y) + 60 }
    ].filter(isFinitePoint);
    points.push(...bounds);
  }

  if (points.length === 0) {
    return "0 0 940 720";
  }

  const minX = Math.min(...points.map((point) => point.x));
  const minY = Math.min(...points.map((point) => point.y));
  const maxX = Math.max(...points.map((point) => point.x));
  const maxY = Math.max(...points.map((point) => point.y));
  if (![minX, minY, maxX, maxY].every(Number.isFinite)) {
    return "80 0 780 720";
  }
  const padding = 56;
  return `${minX - padding} ${minY - padding} ${maxX - minX + padding * 2} ${maxY - minY + padding * 2}`;
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

function playerDisplayName(playerId: string, roomState: RoomState | null) {
  return roomState?.players.find((player) => player.playerId === playerId)?.playerName ?? playerId;
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
  const [resourceGainLines, setResourceGainLines] = useState<string[]>([]);
  const socketRef = useRef<GameSocket | null>(null);
  const previousSnapshotRef = useRef<GameSnapshotPayload | null>(null);

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
        setResourceGainLines(resourceGainSummary(previousSnapshotRef.current, message.payload, roomState));
        previousSnapshotRef.current = message.payload;
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
  const svgViewBox = useMemo(() => boardViewBox(gameSnapshot), [gameSnapshot]);
  const intersectionPositions = useMemo(
    () => {
      const entries = (board?.intersections ?? [])
        .map((intersection) => [
          intersection.intersectionId,
          {
            x: Number(intersection.x),
            y: Number(intersection.y)
          }
        ] as const)
        .filter(([, position]) => isFinitePoint(position));
      return Object.fromEntries(entries) as Record<string, { x: number; y: number }>;
    },
    [board?.intersections]
  );

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

          {resourceGainLines.length > 0 ? (
            <section className="panel compact-panel gain-panel">
              <div className="panel-heading">
                <h2>Latest Gain</h2>
                <span className="subtle">from the newest snapshot</span>
              </div>
              <ul className="gain-list">
                {resourceGainLines.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </section>
          ) : null}

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
            <svg viewBox={svgViewBox} className="board-svg" role="img" aria-label="KaTanDao board" preserveAspectRatio="xMidYMid meet">
              <defs>
                <linearGradient id="seaBase" x1="0%" x2="0%" y1="0%" y2="100%">
                  <stop offset="0%" stopColor="#0f6eac" />
                  <stop offset="45%" stopColor="#1f89c4" />
                  <stop offset="100%" stopColor="#63acd5" />
                </linearGradient>
                <radialGradient id="seaGlow" cx="50%" cy="38%" r="74%">
                  <stop offset="0%" stopColor="rgba(255,255,255,0.22)" />
                  <stop offset="100%" stopColor="rgba(255,255,255,0)" />
                </radialGradient>
                <filter id="islandGlow" x="-40%" y="-40%" width="180%" height="180%">
                  <feDropShadow dx="0" dy="0" stdDeviation="8" floodColor="#fff4b2" floodOpacity="0.9" />
                </filter>
                <pattern id="seaPattern" width="36" height="36" patternUnits="userSpaceOnUse">
                  <path d="M0 8 C7 3, 14 3, 18 8 S29 13, 36 8" fill="none" stroke="rgba(255,255,255,0.10)" strokeWidth="1.4" />
                  <path d="M0 24 C7 19, 14 19, 18 24 S29 29, 36 24" fill="none" stroke="rgba(6,55,88,0.18)" strokeWidth="1.2" />
                </pattern>
                <pattern id="woodPattern" width="80" height="80" patternUnits="userSpaceOnUse">
                  <rect width="80" height="80" fill="#3f6a34" />
                  <circle cx="18" cy="18" r="18" fill="rgba(255,255,255,0.05)" />
                  <circle cx="52" cy="32" r="22" fill="rgba(19,47,15,0.22)" />
                  <circle cx="34" cy="58" r="20" fill="rgba(255,255,255,0.04)" />
                </pattern>
                <pattern id="brickPattern" width="84" height="84" patternUnits="userSpaceOnUse">
                  <rect width="84" height="84" fill="#b15a3c" />
                  <path d="M0 21 H84 M0 42 H84 M0 63 H84" stroke="rgba(90,39,23,0.35)" strokeWidth="3" />
                  <path d="M21 0 V21 M63 21 V42 M21 42 V63 M63 63 V84" stroke="rgba(239,197,182,0.18)" strokeWidth="3" />
                </pattern>
                <pattern id="sheepPattern" width="88" height="88" patternUnits="userSpaceOnUse">
                  <rect width="88" height="88" fill="#95c85b" />
                  <path d="M0 74 C18 54, 30 54, 44 74 S70 94, 88 74 V88 H0 Z" fill="rgba(64,114,40,0.28)" />
                  <path d="M0 18 C20 8, 36 8, 52 18 S72 28, 88 18" fill="none" stroke="rgba(255,255,255,0.14)" strokeWidth="2" />
                </pattern>
                <pattern id="wheatPattern" width="88" height="88" patternUnits="userSpaceOnUse">
                  <rect width="88" height="88" fill="#d6b046" />
                  <path d="M18 80 C28 62, 30 42, 30 8" stroke="rgba(125,90,17,0.4)" strokeWidth="3" />
                  <path d="M42 80 C50 60, 54 40, 54 6" stroke="rgba(125,90,17,0.4)" strokeWidth="3" />
                  <path d="M66 80 C72 62, 78 42, 78 12" stroke="rgba(125,90,17,0.4)" strokeWidth="3" />
                </pattern>
                <pattern id="orePattern" width="88" height="88" patternUnits="userSpaceOnUse">
                  <rect width="88" height="88" fill="#7a8090" />
                  <polygon points="0,88 28,28 46,44 68,8 88,48 88,88" fill="rgba(55,60,73,0.45)" />
                  <path d="M0 60 L22 34 L42 50 L61 18 L88 54" fill="none" stroke="rgba(230,235,244,0.18)" strokeWidth="3" />
                </pattern>
                <pattern id="desertPattern" width="88" height="88" patternUnits="userSpaceOnUse">
                  <rect width="88" height="88" fill="#1c1713" />
                  <circle cx="44" cy="44" r="28" fill="rgba(255,255,255,0.06)" />
                  <path d="M18 28 L70 56 M24 58 L64 30" stroke="rgba(255,255,255,0.05)" strokeWidth="4" />
                </pattern>
              </defs>
              <rect x="18" y="18" width="904" height="684" rx="42" fill="url(#seaBase)" />
              <rect x="18" y="18" width="904" height="684" rx="42" fill="url(#seaPattern)" opacity="0.8" />
              <rect x="18" y="18" width="904" height="684" rx="42" fill="url(#seaGlow)" />
              <path d="M 80 566 Q 232 394 372 428 T 804 458 L 832 702 L 64 702 Z" fill="#8fb08f" opacity="0.42" />
              {(board?.ports ?? []).map((port) => (
                <g key={port.portId} transform={`translate(${port.x} ${port.y}) rotate(${port.rotationDegrees})`}>
                  <polygon points="-28,0 0,-48 28,0" fill="#f2e2bf" stroke="#d2be9b" strokeWidth="2" />
                  <text textAnchor="middle" y="-10" className="port-label">{`${port.ratio}:1`}</text>
                  <text textAnchor="middle" y="6" className="port-subtext">{port.tradeType === "ANY" ? "" : port.tradeType}</text>
                </g>
              ))}
              {board?.hexes.map((hex, index) => {
                const center = typeof hex.q === "number" && typeof hex.r === "number"
                  ? hexCenterFromAxial(hex.q, hex.r)
                  : fallbackHexCenter(index);
                const color = resourcePalette[hex.resourceType];
                const fillMap: Record<string, string> = {
                  WOOD: "url(#woodPattern)",
                  BRICK: "url(#brickPattern)",
                  SHEEP: "url(#sheepPattern)",
                  WHEAT: "url(#wheatPattern)",
                  ORE: "url(#orePattern)",
                  DESERT: "url(#desertPattern)"
                };
                return (
                  <g key={hex.hexId} filter="url(#islandGlow)">
                    <polygon points={hexPoints(center.x, center.y, 84)} fill={fillMap[hex.resourceType]} stroke="#efe3bf" strokeWidth="8" />
                    <polygon points={hexPoints(center.x, center.y, 82)} fill="transparent" stroke="rgba(86, 59, 27, 0.38)" strokeWidth="3" />
                    {hex.resourceType === "DESERT" ? (
                      <>
                        <circle cx={center.x} cy={center.y} r="31" fill="rgba(14,10,8,0.84)" stroke="#f2e3bf" strokeWidth="4" />
                        <text x={center.x} y={center.y - 8} textAnchor="middle" className="hex-label robber-label">Robber</text>
                        <text x={center.x} y={center.y + 14} textAnchor="middle" className="hex-number robber-label">R</text>
                      </>
                    ) : (
                      <>
                        <circle cx={center.x} cy={center.y} r="30" fill="rgba(255,249,239,0.94)" stroke="#5a4024" strokeWidth="3" />
                        <text x={center.x} y={center.y - 10} textAnchor="middle" className="hex-label">{color.label}</text>
                        <text x={center.x} y={center.y + 12} textAnchor="middle" className="hex-number">{hex.numberToken}</text>
                      </>
                    )}
                  </g>
                );
              })}

              {board?.edges.map((edge) => {
                const from = intersectionPositions[edge.fromIntersectionId];
                const to = intersectionPositions[edge.toIntersectionId];
                if (!from || !to) {
                  return null;
                }
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
                if (!position) {
                  return null;
                }
                const selectable = targetIsSelectable(intersection.intersectionId);
                const ownerColor = intersection.ownerPlayerId ? playerColor(intersection.ownerPlayerId, orderedPlayerIds) : "#fff7e6";
                const ownerName = intersection.ownerPlayerId ? playerDisplayName(intersection.ownerPlayerId, roomState) : null;
                return (
                  <g
                    key={intersection.intersectionId}
                    className={selectable ? "board-node is-selectable" : "board-node"}
                    onClick={() => selectable && selectedActionType && sendAction(selectedActionType, intersection.intersectionId)}
                  >
                    {intersection.buildingType === "CITY" ? (
                      <>
                        <rect x={position.x - 18} y={position.y - 18} width="36" height="26" rx="6" fill={ownerColor} stroke="#4c341f" strokeWidth="4" />
                        <polygon points={`${position.x - 20},${position.y - 6} ${position.x},${position.y - 28} ${position.x + 20},${position.y - 6}`} fill={ownerColor} stroke="#4c341f" strokeWidth="4" />
                      </>
                    ) : (
                      <polygon
                        points={`${position.x - 16},${position.y + 10} ${position.x},${position.y - 18} ${position.x + 16},${position.y + 10}`}
                        fill={ownerColor}
                        stroke="#4c341f"
                        strokeWidth="4"
                      />
                    )}
                    <text x={position.x} y={position.y + 36} textAnchor="middle" className="node-label">
                      {intersection.intersectionId}
                    </text>
                    {ownerName ? (
                      <text x={position.x} y={position.y + 50} textAnchor="middle" className="owner-label">
                        {ownerName}
                      </text>
                    ) : null}
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
                const playerNameLabel = playerDisplayName(player.playerId, roomState);
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

function resourceGainSummary(
  previousSnapshot: GameSnapshotPayload | null,
  nextSnapshot: GameSnapshotPayload,
  roomState: RoomState | null
) {
  if (!previousSnapshot) {
    return [];
  }

  const lines: string[] = [];
  for (const nextPlayer of nextSnapshot.state.players) {
    const previousPlayer = previousSnapshot.state.players.find((player) => player.playerId === nextPlayer.playerId);
    if (!previousPlayer) {
      continue;
    }

    const changes = Object.entries(nextPlayer.resources)
      .map(([resource, amount]) => ({
        resource,
        delta: amount - (previousPlayer.resources[resource] ?? 0)
      }))
      .filter((entry) => entry.delta > 0);

    if (changes.length === 0) {
      continue;
    }

    const name = playerDisplayName(nextPlayer.playerId, roomState);
    const summary = changes.map((entry) => `+${entry.delta} ${resourcePalette[entry.resource].label}`).join(", ");
    lines.push(`${name}: ${summary}`);
  }

  return lines;
}
