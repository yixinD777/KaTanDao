package com.katandao.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.katandao.server.game.GameSession;
import com.katandao.server.model.RoomState;
import com.katandao.server.protocol.payload.GameActionPayload;
import com.katandao.server.protocol.payload.GameSnapshotPayload;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GameSessionServiceTest {

    private final RoomService roomService = new RoomService();
    private final GameSessionService gameSessionService = new GameSessionService(new ObjectMapper());

    @Test
    void shouldCreateInitialSnapshotInSetupPhase() {
        RoomState roomState = roomService.createRoom("p1", "Alice", 4);
        roomService.joinRoom(roomState.roomId(), "p2", "Bob");
        roomService.joinRoom(roomState.roomId(), "p3", "Carol");
        roomService.setReady(roomState.roomId(), "p1", true);
        roomService.setReady(roomState.roomId(), "p2", true);
        roomService.setReady(roomState.roomId(), "p3", true);
        RoomState started = roomService.startGame(roomState.roomId(), "p1").orElseThrow();

        GameSession session = gameSessionService.createForRoom(started);
        GameSnapshotPayload snapshot = gameSessionService.createInitialSnapshot(session);

        assertEquals("SETUP", snapshot.phase());
        assertEquals("PLACE_INITIAL_SETTLEMENT", snapshot.turn().stage());
    }

    @Test
    void shouldAdvanceSnapshotAfterSetupAction() {
        RoomState roomState = roomService.createRoom("p1", "Alice", 4);
        roomService.joinRoom(roomState.roomId(), "p2", "Bob");
        roomService.joinRoom(roomState.roomId(), "p3", "Carol");
        roomService.setReady(roomState.roomId(), "p1", true);
        roomService.setReady(roomState.roomId(), "p2", true);
        roomService.setReady(roomState.roomId(), "p3", true);
        RoomState started = roomService.startGame(roomState.roomId(), "p1").orElseThrow();

        GameSession session = gameSessionService.createForRoom(started);
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("intersectionId", "I-01");

        GameSnapshotPayload snapshot = gameSessionService.handleAction(new GameActionPayload(
                started.roomId(),
                session.gameId(),
                "p1",
                new GameActionPayload.ActionBody(com.katandao.server.game.GameActionType.PLACE_INITIAL_SETTLEMENT, data)
        )).orElseThrow();

        assertEquals("PLACE_INITIAL_ROAD", snapshot.turn().stage());
        assertEquals(2L, snapshot.version());
    }

    @Test
    void shouldExposeBuildActionsAfterRollingDice() {
        RoomState roomState = roomService.createRoom("p1", "Alice", 4);
        roomService.joinRoom(roomState.roomId(), "p2", "Bob");
        roomService.joinRoom(roomState.roomId(), "p3", "Carol");
        roomService.setReady(roomState.roomId(), "p1", true);
        roomService.setReady(roomState.roomId(), "p2", true);
        roomService.setReady(roomState.roomId(), "p3", true);
        RoomState started = roomService.startGame(roomState.roomId(), "p1").orElseThrow();

        GameSession session = gameSessionService.createForRoom(started);
        apply(session, started.roomId(), "p1", com.katandao.server.game.GameActionType.PLACE_INITIAL_SETTLEMENT, "intersectionId", "I-01");
        apply(session, started.roomId(), "p1", com.katandao.server.game.GameActionType.PLACE_INITIAL_ROAD, "edgeId", "E-01");
        apply(session, started.roomId(), "p2", com.katandao.server.game.GameActionType.PLACE_INITIAL_SETTLEMENT, "intersectionId", "I-03");
        apply(session, started.roomId(), "p2", com.katandao.server.game.GameActionType.PLACE_INITIAL_ROAD, "edgeId", "E-02");
        apply(session, started.roomId(), "p3", com.katandao.server.game.GameActionType.PLACE_INITIAL_SETTLEMENT, "intersectionId", "I-05");
        GameSnapshotPayload snapshot = apply(session, started.roomId(), "p3", com.katandao.server.game.GameActionType.PLACE_INITIAL_ROAD, "edgeId", "E-05");
        snapshot = apply(session, started.roomId(), "p1", com.katandao.server.game.GameActionType.ROLL_DICE, null, null);

        Set<String> actionTypes = snapshot.legalActions().stream()
                .map(action -> action.actionType().name())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(actionTypes.contains("BUILD_ROAD"));
        assertTrue(actionTypes.contains("END_TURN"));
    }

    private GameSnapshotPayload apply(
            GameSession session,
            String roomId,
            String playerId,
            com.katandao.server.game.GameActionType actionType,
            String field,
            String value
    ) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        if (field != null && value != null) {
            data.put(field, value);
        }
        return gameSessionService.handleAction(new GameActionPayload(
                roomId,
                session.gameId(),
                playerId,
                new GameActionPayload.ActionBody(actionType, data)
        )).orElseThrow();
    }
}
