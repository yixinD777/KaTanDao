package com.katandao.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katandao.server.model.PlayerSession;
import com.katandao.server.model.RoomPlayer;
import com.katandao.server.model.RoomState;
import com.katandao.server.game.GameSession;
import com.katandao.server.protocol.InboundMessage;
import com.katandao.server.protocol.OutboundMessage;
import com.katandao.server.protocol.payload.CreateRoomPayload;
import com.katandao.server.protocol.payload.ErrorPayload;
import com.katandao.server.protocol.payload.GameActionPayload;
import com.katandao.server.protocol.payload.GameSnapshotPayload;
import com.katandao.server.protocol.payload.GameStartedPayload;
import com.katandao.server.protocol.payload.HelloAckPayload;
import com.katandao.server.protocol.payload.HelloPayload;
import com.katandao.server.protocol.payload.JoinRoomPayload;
import com.katandao.server.protocol.payload.RoomStateEnvelopePayload;
import com.katandao.server.protocol.payload.SetReadyPayload;
import com.katandao.server.protocol.payload.StartGamePayload;
import com.katandao.server.service.GameSessionService;
import com.katandao.server.service.PlayerSessionRegistry;
import com.katandao.server.service.RoomService;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final String SERVER_VERSION = "0.1.0";

    private final ObjectMapper objectMapper;
    private final PlayerSessionRegistry playerSessionRegistry;
    private final RoomService roomService;
    private final GameSessionService gameSessionService;
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            PlayerSessionRegistry playerSessionRegistry,
            RoomService roomService,
            GameSessionService gameSessionService
    ) {
        this.objectMapper = objectMapper;
        this.playerSessionRegistry = playerSessionRegistry;
        this.roomService = roomService;
        this.gameSessionService = gameSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        activeSessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        InboundMessage inboundMessage;
        try {
            inboundMessage = objectMapper.readValue(message.getPayload(), InboundMessage.class);
        } catch (JsonProcessingException exception) {
            sendError(session, null, "INVALID_MESSAGE", "Unable to parse message payload.", Map.of());
            return;
        }

        if (inboundMessage.type() == null || inboundMessage.payload() == null) {
            sendError(session, inboundMessage.requestId(), "INVALID_MESSAGE", "Message type and payload are required.", Map.of());
            return;
        }

        switch (inboundMessage.type()) {
            case "hello" -> handleHello(session, inboundMessage);
            case "create_room" -> handleCreateRoom(session, inboundMessage);
            case "join_room" -> handleJoinRoom(session, inboundMessage);
            case "set_ready" -> handleSetReady(session, inboundMessage);
            case "start_game" -> handleStartGame(session, inboundMessage);
            case "player_action" -> handlePlayerAction(session, inboundMessage);
            default -> sendError(
                    session,
                    inboundMessage.requestId(),
                    "INVALID_MESSAGE",
                    "Unsupported message type: " + inboundMessage.type(),
                    Map.of("type", inboundMessage.type())
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.remove(session.getId());
        playerSessionRegistry.unregisterBySessionId(session.getId());
    }

    private void handleHello(WebSocketSession session, InboundMessage inboundMessage) throws IOException {
        HelloPayload payload = objectMapper.treeToValue(inboundMessage.payload(), HelloPayload.class);
        if (isBlank(payload.playerId()) || isBlank(payload.playerName())) {
            sendError(session, inboundMessage.requestId(), "INVALID_MESSAGE", "playerId and playerName are required.", Map.of());
            return;
        }

        PlayerSession playerSession = playerSessionRegistry.register(session, payload.playerId(), payload.playerName());
        sendMessage(session, new OutboundMessage(
                "hello_ack",
                inboundMessage.requestId(),
                Instant.now(),
                new HelloAckPayload(playerSession.playerId(), session.getId(), SERVER_VERSION)
        ));
    }

    private void handleCreateRoom(WebSocketSession session, InboundMessage inboundMessage) throws IOException {
        CreateRoomPayload payload = objectMapper.treeToValue(inboundMessage.payload(), CreateRoomPayload.class);
        if (!validateSessionOwnership(session, payload.playerId(), inboundMessage.requestId())) {
            return;
        }

        RoomState roomState;
        try {
            Integer maxPlayers = payload.config() == null ? null : payload.config().maxPlayers();
            roomState = roomService.createRoom(payload.playerId(), payload.playerName(), maxPlayers);
        } catch (IllegalStateException exception) {
            sendError(session, inboundMessage.requestId(), exception.getMessage(), "Unable to create room.", Map.of());
            return;
        }

        broadcastRoomState(roomState, inboundMessage.requestId());
    }

    private void handleJoinRoom(WebSocketSession session, InboundMessage inboundMessage) throws IOException {
        JoinRoomPayload payload = objectMapper.treeToValue(inboundMessage.payload(), JoinRoomPayload.class);
        if (!validateSessionOwnership(session, payload.playerId(), inboundMessage.requestId())) {
            return;
        }

        Optional<RoomState> maybeRoomState;
        try {
            maybeRoomState = roomService.joinRoom(payload.roomId(), payload.playerId(), payload.playerName());
        } catch (IllegalStateException exception) {
            sendError(session, inboundMessage.requestId(), exception.getMessage(), "Unable to join room.", Map.of("roomId", payload.roomId()));
            return;
        }

        if (maybeRoomState.isEmpty()) {
            sendError(session, inboundMessage.requestId(), "ROOM_NOT_FOUND", "Room does not exist.", Map.of("roomId", payload.roomId()));
            return;
        }

        broadcastRoomState(maybeRoomState.get(), inboundMessage.requestId());
    }

    private void handleSetReady(WebSocketSession session, InboundMessage inboundMessage) throws IOException {
        SetReadyPayload payload = objectMapper.treeToValue(inboundMessage.payload(), SetReadyPayload.class);
        if (!validateSessionOwnership(session, payload.playerId(), inboundMessage.requestId())) {
            return;
        }

        Optional<RoomState> maybeRoomState;
        try {
            maybeRoomState = roomService.setReady(payload.roomId(), payload.playerId(), payload.ready());
        } catch (IllegalStateException exception) {
            sendError(session, inboundMessage.requestId(), exception.getMessage(), "Unable to update ready state.", Map.of("roomId", payload.roomId()));
            return;
        }

        if (maybeRoomState.isEmpty()) {
            sendError(session, inboundMessage.requestId(), "ROOM_NOT_FOUND", "Room does not exist.", Map.of("roomId", payload.roomId()));
            return;
        }

        broadcastRoomState(maybeRoomState.get(), inboundMessage.requestId());
    }

    private void handleStartGame(WebSocketSession session, InboundMessage inboundMessage) throws IOException {
        StartGamePayload payload = objectMapper.treeToValue(inboundMessage.payload(), StartGamePayload.class);
        if (!validateSessionOwnership(session, payload.playerId(), inboundMessage.requestId())) {
            return;
        }

        Optional<RoomState> maybeRoomState;
        try {
            maybeRoomState = roomService.startGame(payload.roomId(), payload.playerId());
        } catch (IllegalStateException exception) {
            sendError(session, inboundMessage.requestId(), exception.getMessage(), "Unable to start game.", Map.of("roomId", payload.roomId()));
            return;
        }

        if (maybeRoomState.isEmpty()) {
            sendError(session, inboundMessage.requestId(), "ROOM_NOT_FOUND", "Room does not exist.", Map.of("roomId", payload.roomId()));
            return;
        }

        RoomState roomState = maybeRoomState.get();
        GameSession gameSession = gameSessionService.createForRoom(roomState);
        GameSnapshotPayload snapshotPayload = gameSessionService.createInitialSnapshot(gameSession);

        broadcastRoomState(roomState, inboundMessage.requestId());
        broadcastToRoom(roomState, new OutboundMessage(
                "game_started",
                inboundMessage.requestId(),
                Instant.now(),
                new GameStartedPayload(roomState.roomId(), gameSession.gameId())
        ));
        broadcastToRoom(roomState, new OutboundMessage(
                "game_snapshot",
                inboundMessage.requestId(),
                Instant.now(),
                snapshotPayload
        ));
    }

    private void handlePlayerAction(WebSocketSession session, InboundMessage inboundMessage) throws IOException {
        GameActionPayload payload = objectMapper.treeToValue(inboundMessage.payload(), GameActionPayload.class);
        if (!validateSessionOwnership(session, payload.playerId(), inboundMessage.requestId())) {
            return;
        }

        Optional<GameSnapshotPayload> maybeSnapshot;
        try {
            maybeSnapshot = gameSessionService.handleAction(payload);
        } catch (IllegalArgumentException exception) {
            sendError(session, inboundMessage.requestId(), exception.getMessage(), "Action validation failed.", Map.of(
                    "roomId", payload.roomId(),
                    "gameId", payload.gameId(),
                    "actionType", payload.action() == null ? null : payload.action().actionType()
            ));
            return;
        }
        if (maybeSnapshot.isEmpty()) {
            sendError(session, inboundMessage.requestId(), "GAME_NOT_FOUND", "Game session does not exist.", Map.of("roomId", payload.roomId(), "gameId", payload.gameId()));
            return;
        }

        roomService.findRoomState(payload.roomId()).ifPresent(roomState -> {
            try {
                broadcastToRoom(roomState, new OutboundMessage(
                        "action_accepted",
                        inboundMessage.requestId(),
                        Instant.now(),
                        Map.of("gameId", payload.gameId(), "version", maybeSnapshot.get().version())
                ));
                broadcastToRoom(roomState, new OutboundMessage(
                        "game_snapshot",
                        inboundMessage.requestId(),
                        Instant.now(),
                        maybeSnapshot.get()
                ));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private boolean validateSessionOwnership(WebSocketSession session, String playerId, String requestId) throws IOException {
        if (isBlank(playerId)) {
            sendError(session, requestId, "INVALID_MESSAGE", "playerId is required.", Map.of());
            return false;
        }
        if (!playerSessionRegistry.isSessionOwnedBy(session, playerId)) {
            sendError(session, requestId, "UNAUTHORIZED_PLAYER", "The socket session does not match the playerId.", Map.of("playerId", playerId));
            return false;
        }
        return true;
    }

    private void broadcastRoomState(RoomState roomState, String requestId) throws IOException {
        broadcastToRoom(roomState, new OutboundMessage(
                "room_state",
                requestId,
                Instant.now(),
                new RoomStateEnvelopePayload(roomState)
        ));
    }

    private void broadcastToRoom(RoomState roomState, OutboundMessage outboundMessage) throws IOException {
        for (RoomPlayer player : roomState.players()) {
            playerSessionRegistry.findByPlayerId(player.playerId())
                    .map(PlayerSession::webSocketSessionId)
                    .map(activeSessions::get)
                    .filter(Objects::nonNull)
                    .filter(WebSocketSession::isOpen)
                    .ifPresent(webSocketSession -> {
                        try {
                            sendMessage(webSocketSession, outboundMessage);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }

    private void sendError(
            WebSocketSession session,
            String requestId,
            String code,
            String message,
            Map<String, Object> details
    ) throws IOException {
        sendMessage(session, new OutboundMessage(
                "action_rejected",
                requestId,
                Instant.now(),
                new ErrorPayload(code, message, details)
        ));
    }

    private void sendMessage(WebSocketSession session, OutboundMessage outboundMessage) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(outboundMessage)));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
