package com.katandao.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katandao.gamecore.GameAction;
import com.katandao.gamecore.GameActionType;
import com.katandao.gamecore.GameBoard;
import com.katandao.gamecore.GameEngine;
import com.katandao.gamecore.GamePhase;
import com.katandao.gamecore.GameState;
import com.katandao.gamecore.IntersectionState;
import com.katandao.gamecore.PlayerState;
import com.katandao.gamecore.ResourceType;
import com.katandao.gamecore.TurnStage;
import com.katandao.server.game.GameSession;
import com.katandao.server.model.RoomState;
import com.katandao.server.protocol.payload.GameActionPayload;
import com.katandao.server.protocol.payload.GameSnapshotPayload;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class GameSessionService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Map<String, GameSession> byRoomId = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public GameSessionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GameSession createForRoom(RoomState roomState) {
        GameSession gameSession = GameSession.create(
                "game-" + UUID.randomUUID().toString().substring(0, 8),
                roomState.roomId(),
                roomState.players().stream().map(player -> player.playerId()).toList()
        );
        byRoomId.put(roomState.roomId(), gameSession);
        return gameSession;
    }

    public Optional<GameSession> findByRoomId(String roomId) {
        return Optional.ofNullable(byRoomId.get(roomId));
    }

    public Optional<GameSnapshotPayload> handleAction(GameActionPayload payload) {
        GameSession gameSession = byRoomId.get(payload.roomId());
        if (gameSession == null) {
            return Optional.empty();
        }

        Map<String, Object> actionData = payload.action().data() == null
                ? Map.of()
                : objectMapper.convertValue(payload.action().data(), MAP_TYPE);

        GameState nextState = GameEngine.apply(
                gameSession.gameState(),
                payload.playerId(),
                new GameAction(GameActionType.valueOf(payload.action().actionType().name()), actionData)
        );

        GameSession updatedSession = new GameSession(gameSession.gameId(), gameSession.roomId(), nextState);
        byRoomId.put(payload.roomId(), updatedSession);

        return Optional.of(toSnapshot(updatedSession.gameState(), updatedSession.roomId(), updatedSession.gameId()));
    }

    public GameSnapshotPayload createInitialSnapshot(GameSession gameSession) {
        return toSnapshot(gameSession.gameState(), gameSession.roomId(), gameSession.gameId());
    }

    private GameSnapshotPayload toSnapshot(GameState state, String roomId, String gameId) {
        return new GameSnapshotPayload(
                roomId,
                gameId,
                state.version(),
                state.phase().name(),
                new GameSnapshotPayload.TurnPayload(
                        state.turn().currentPlayerId(),
                        state.turn().turnNumber(),
                        state.turn().stage().name()
                ),
                legalActionsFor(state),
                state.winnerPlayerId(),
                state
        );
    }

    private List<GameSnapshotPayload.LegalActionPayload> legalActionsFor(GameState state) {
        if (state.phase() == GamePhase.SETUP) {
            return switch (state.turn().stage()) {
                case PLACE_INITIAL_SETTLEMENT -> List.of(new GameSnapshotPayload.LegalActionPayload(
                        com.katandao.server.game.GameActionType.PLACE_INITIAL_SETTLEMENT,
                        state.board().intersections().stream()
                                .filter(intersection -> intersection.ownerPlayerId() == null)
                                .map(intersection -> intersection.intersectionId())
                                .toList()
                ));
                case PLACE_INITIAL_ROAD -> List.of(new GameSnapshotPayload.LegalActionPayload(
                        com.katandao.server.game.GameActionType.PLACE_INITIAL_ROAD,
                        state.board().edges().stream()
                                .filter(edge -> edge.roadOwnerPlayerId() == null)
                                .map(edge -> edge.edgeId())
                                .toList()
                ));
                default -> List.of();
            };
        }

        if (state.turn().stage() == TurnStage.ROLL) {
            return List.of(new GameSnapshotPayload.LegalActionPayload(
                    com.katandao.server.game.GameActionType.ROLL_DICE,
                    List.of()
            ));
        }

        List<GameSnapshotPayload.LegalActionPayload> actions = new java.util.ArrayList<>();
        PlayerState currentPlayer = state.players().stream()
                .filter(player -> player.playerId().equals(state.turn().currentPlayerId()))
                .findFirst()
                .orElseThrow();

        List<String> buildRoadTargets = availableRoadTargets(state.board(), state.turn().currentPlayerId());
        if (!buildRoadTargets.isEmpty()
                && hasResources(currentPlayer, Map.of(ResourceType.WOOD, 1, ResourceType.BRICK, 1))) {
            actions.add(new GameSnapshotPayload.LegalActionPayload(
                    com.katandao.server.game.GameActionType.BUILD_ROAD,
                    buildRoadTargets
            ));
        }

        List<String> settlementTargets = availableSettlementTargets(state.board(), state.turn().currentPlayerId());
        if (!settlementTargets.isEmpty()
                && hasResources(currentPlayer, Map.of(
                ResourceType.WOOD, 1,
                ResourceType.BRICK, 1,
                ResourceType.SHEEP, 1,
                ResourceType.WHEAT, 1))) {
            actions.add(new GameSnapshotPayload.LegalActionPayload(
                    com.katandao.server.game.GameActionType.BUILD_SETTLEMENT,
                    settlementTargets
            ));
        }

        List<String> cityTargets = state.board().intersections().stream()
                .filter(intersection -> state.turn().currentPlayerId().equals(intersection.ownerPlayerId()))
                .filter(intersection -> intersection.buildingType() == com.katandao.gamecore.BuildingType.SETTLEMENT)
                .map(IntersectionState::intersectionId)
                .toList();
        if (!cityTargets.isEmpty()
                && hasResources(currentPlayer, Map.of(ResourceType.WHEAT, 2, ResourceType.ORE, 3))) {
            actions.add(new GameSnapshotPayload.LegalActionPayload(
                    com.katandao.server.game.GameActionType.BUILD_CITY,
                    cityTargets
            ));
        }

        actions.add(new GameSnapshotPayload.LegalActionPayload(
                com.katandao.server.game.GameActionType.END_TURN,
                List.of()
        ));
        return List.copyOf(actions);
    }

    private boolean hasResources(PlayerState player, Map<ResourceType, Integer> required) {
        return required.entrySet().stream()
                .allMatch(entry -> player.resourceCount(entry.getKey()) >= entry.getValue());
    }

    private List<String> availableRoadTargets(GameBoard board, String playerId) {
        return board.edges().stream()
                .filter(edge -> edge.roadOwnerPlayerId() == null)
                .filter(edge -> edgeTouchesOwnedRoad(board, playerId, edge.fromIntersectionId(), edge.toIntersectionId())
                        || edgeTouchesOwnedBuilding(board, playerId, edge.fromIntersectionId(), edge.toIntersectionId()))
                .map(edge -> edge.edgeId())
                .toList();
    }

    private List<String> availableSettlementTargets(GameBoard board, String playerId) {
        return board.intersections().stream()
                .filter(intersection -> intersection.ownerPlayerId() == null)
                .filter(intersection -> touchesOwnedRoad(board, playerId, intersection.intersectionId()))
                .map(IntersectionState::intersectionId)
                .toList();
    }

    private boolean edgeTouchesOwnedRoad(GameBoard board, String playerId, String fromIntersectionId, String toIntersectionId) {
        return board.edges().stream()
                .filter(edge -> playerId.equals(edge.roadOwnerPlayerId()))
                .anyMatch(edge -> edge.fromIntersectionId().equals(fromIntersectionId)
                        || edge.fromIntersectionId().equals(toIntersectionId)
                        || edge.toIntersectionId().equals(fromIntersectionId)
                        || edge.toIntersectionId().equals(toIntersectionId));
    }

    private boolean edgeTouchesOwnedBuilding(GameBoard board, String playerId, String fromIntersectionId, String toIntersectionId) {
        return board.intersections().stream()
                .filter(intersection -> intersection.intersectionId().equals(fromIntersectionId)
                        || intersection.intersectionId().equals(toIntersectionId))
                .anyMatch(intersection -> playerId.equals(intersection.ownerPlayerId()));
    }

    private boolean touchesOwnedRoad(GameBoard board, String playerId, String intersectionId) {
        return board.edges().stream()
                .filter(edge -> playerId.equals(edge.roadOwnerPlayerId()))
                .anyMatch(edge -> edge.fromIntersectionId().equals(intersectionId) || edge.toIntersectionId().equals(intersectionId));
    }
}
