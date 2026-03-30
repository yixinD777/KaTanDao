package com.katandao.gamecore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class GameEngine {

    private GameEngine() {
    }

    public static GameState createInitialState(String gameId, List<String> playerIds) {
        List<PlayerState> players = playerIds.stream()
                .map(playerId -> new PlayerState(playerId, 15, 5, 4, 0, PlayerState.emptyResources(), null))
                .toList();

        return new GameState(
                gameId,
                GamePhase.SETUP,
                GameBoard.initial(),
                players,
                new TurnState(playerIds.get(0), 1, TurnStage.PLACE_INITIAL_SETTLEMENT),
                DiceState.initial(),
                1L,
                null
        );
    }

    public static GameState apply(GameState state, String playerId, GameAction action) {
        validateCurrentPlayer(state, playerId);

        return switch (action.actionType()) {
            case PLACE_INITIAL_SETTLEMENT -> placeInitialSettlement(state, playerId, requiredString(action.data(), "intersectionId"));
            case PLACE_INITIAL_ROAD -> placeInitialRoad(state, playerId, requiredString(action.data(), "edgeId"));
            case ROLL_DICE -> rollDice(state, playerId);
            case BUILD_ROAD -> buildRoad(state, playerId, requiredString(action.data(), "edgeId"));
            case BUILD_SETTLEMENT -> buildSettlement(state, playerId, requiredString(action.data(), "intersectionId"));
            case BUILD_CITY -> buildCity(state, playerId, requiredString(action.data(), "intersectionId"));
            case END_TURN -> endTurn(state, playerId);
        };
    }

    private static GameState placeInitialSettlement(GameState state, String playerId, String intersectionId) {
        ensurePhase(state, GamePhase.SETUP);
        ensureStage(state, TurnStage.PLACE_INITIAL_SETTLEMENT);

        IntersectionState target = state.board().intersections().stream()
                .filter(intersection -> intersection.intersectionId().equals(intersectionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("INVALID_BUILD_LOCATION"));

        if (target.ownerPlayerId() != null) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        List<IntersectionState> updatedIntersections = state.board().intersections().stream()
                .map(intersection -> intersection.intersectionId().equals(intersectionId)
                        ? intersection.withBuilding(playerId, BuildingType.SETTLEMENT)
                        : intersection)
                .toList();

        List<PlayerState> updatedPlayers = state.players().stream()
                .map(player -> player.playerId().equals(playerId)
                        ? player.withSettlementsRemaining(player.settlementsRemaining() - 1)
                                .withVictoryPoints(player.victoryPoints() + 1)
                                .withLastPlacedIntersectionId(intersectionId)
                        : player)
                .toList();

        return new GameState(
                state.gameId(),
                state.phase(),
                new GameBoard(state.board().hexes(), updatedIntersections, state.board().edges()),
                updatedPlayers,
                new TurnState(playerId, state.turn().turnNumber(), TurnStage.PLACE_INITIAL_ROAD),
                state.dice(),
                state.version() + 1,
                null
        );
    }

    private static GameState placeInitialRoad(GameState state, String playerId, String edgeId) {
        ensurePhase(state, GamePhase.SETUP);
        ensureStage(state, TurnStage.PLACE_INITIAL_ROAD);

        PlayerState player = player(state, playerId);
        EdgeState edge = state.board().edges().stream()
                .filter(candidate -> candidate.edgeId().equals(edgeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("INVALID_BUILD_LOCATION"));

        if (edge.roadOwnerPlayerId() != null) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        String requiredIntersectionId = player.lastPlacedIntersectionId();
        boolean adjacent = edge.fromIntersectionId().equals(requiredIntersectionId)
                || edge.toIntersectionId().equals(requiredIntersectionId);
        if (!adjacent) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        List<EdgeState> updatedEdges = state.board().edges().stream()
                .map(candidate -> candidate.edgeId().equals(edgeId)
                        ? candidate.withRoadOwner(playerId)
                        : candidate)
                .toList();

        List<PlayerState> updatedPlayers = state.players().stream()
                .map(candidate -> candidate.playerId().equals(playerId)
                        ? candidate.withRoadsRemaining(candidate.roadsRemaining() - 1)
                        : candidate)
                .toList();

        SetupProgress progress = SetupProgress.from(state);
        SetupProgress nextProgress = progress.afterRoadPlacement(playerId, state.players());

        return new GameState(
                state.gameId(),
                nextProgress.phase(),
                new GameBoard(state.board().hexes(), state.board().intersections(), updatedEdges),
                updatedPlayers,
                nextProgress.turnState(),
                DiceState.initial(),
                state.version() + 1,
                null
        );
    }

    private static GameState rollDice(GameState state, String playerId) {
        ensurePhase(state, GamePhase.IN_PROGRESS);
        ensureStage(state, TurnStage.ROLL);

        int turnNumber = state.turn().turnNumber();
        int dieA = (turnNumber % 6) + 1;
        int dieB = ((turnNumber + 1) % 6) + 1;
        int total = dieA + dieB;

        List<PlayerState> distributedPlayers = distributeResources(state.players(), state.board(), total);

        return new GameState(
                state.gameId(),
                state.phase(),
                state.board(),
                distributedPlayers,
                new TurnState(playerId, turnNumber, TurnStage.MAIN),
                new DiceState(total, dieA, dieB, true),
                state.version() + 1,
                null
        );
    }

    private static GameState buildRoad(GameState state, String playerId, String edgeId) {
        ensurePhase(state, GamePhase.IN_PROGRESS);
        ensureStage(state, TurnStage.MAIN);

        PlayerState player = player(state, playerId);
        requireResources(player, Map.of(ResourceType.WOOD, 1, ResourceType.BRICK, 1));

        EdgeState edge = state.board().edges().stream()
                .filter(candidate -> candidate.edgeId().equals(edgeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("INVALID_BUILD_LOCATION"));
        if (edge.roadOwnerPlayerId() != null) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        boolean connected = edgeTouchesOwnedBuilding(state, playerId, edge)
                || edgeTouchesOwnedRoad(state, playerId, edge);
        if (!connected) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        List<EdgeState> updatedEdges = state.board().edges().stream()
                .map(candidate -> candidate.edgeId().equals(edgeId) ? candidate.withRoadOwner(playerId) : candidate)
                .toList();

        List<PlayerState> updatedPlayers = updatePlayers(state.players(), playerId, currentPlayer ->
                spendResources(currentPlayer, Map.of(ResourceType.WOOD, 1, ResourceType.BRICK, 1))
                        .withRoadsRemaining(currentPlayer.roadsRemaining() - 1));

        return new GameState(
                state.gameId(),
                state.phase(),
                new GameBoard(state.board().hexes(), state.board().intersections(), updatedEdges),
                updatedPlayers,
                state.turn(),
                state.dice(),
                state.version() + 1,
                state.winnerPlayerId()
        );
    }

    private static GameState buildSettlement(GameState state, String playerId, String intersectionId) {
        ensurePhase(state, GamePhase.IN_PROGRESS);
        ensureStage(state, TurnStage.MAIN);

        PlayerState player = player(state, playerId);
        requireResources(player, Map.of(
                ResourceType.WOOD, 1,
                ResourceType.BRICK, 1,
                ResourceType.SHEEP, 1,
                ResourceType.WHEAT, 1
        ));

        IntersectionState target = intersection(state, intersectionId);
        if (target.ownerPlayerId() != null) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }
        if (!touchesOwnedRoad(state, playerId, intersectionId)) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        List<IntersectionState> updatedIntersections = state.board().intersections().stream()
                .map(intersection -> intersection.intersectionId().equals(intersectionId)
                        ? intersection.withBuilding(playerId, BuildingType.SETTLEMENT)
                        : intersection)
                .toList();

        List<PlayerState> updatedPlayers = updatePlayers(state.players(), playerId, currentPlayer ->
                spendResources(currentPlayer, Map.of(
                        ResourceType.WOOD, 1,
                        ResourceType.BRICK, 1,
                        ResourceType.SHEEP, 1,
                        ResourceType.WHEAT, 1
                )).withSettlementsRemaining(currentPlayer.settlementsRemaining() - 1)
                        .withVictoryPoints(currentPlayer.victoryPoints() + 1));

        return new GameState(
                state.gameId(),
                state.phase(),
                new GameBoard(state.board().hexes(), updatedIntersections, state.board().edges()),
                updatedPlayers,
                state.turn(),
                state.dice(),
                state.version() + 1,
                state.winnerPlayerId()
        );
    }

    private static GameState buildCity(GameState state, String playerId, String intersectionId) {
        ensurePhase(state, GamePhase.IN_PROGRESS);
        ensureStage(state, TurnStage.MAIN);

        PlayerState player = player(state, playerId);
        requireResources(player, Map.of(ResourceType.WHEAT, 2, ResourceType.ORE, 3));

        IntersectionState target = intersection(state, intersectionId);
        if (!playerId.equals(target.ownerPlayerId()) || target.buildingType() != BuildingType.SETTLEMENT) {
            throw new IllegalArgumentException("INVALID_BUILD_LOCATION");
        }

        List<IntersectionState> updatedIntersections = state.board().intersections().stream()
                .map(intersection -> intersection.intersectionId().equals(intersectionId)
                        ? intersection.withBuilding(playerId, BuildingType.CITY)
                        : intersection)
                .toList();

        List<PlayerState> updatedPlayers = updatePlayers(state.players(), playerId, currentPlayer ->
                spendResources(currentPlayer, Map.of(ResourceType.WHEAT, 2, ResourceType.ORE, 3))
                        .withSettlementsRemaining(currentPlayer.settlementsRemaining() + 1)
                        .withCitiesRemaining(currentPlayer.citiesRemaining() - 1)
                        .withVictoryPoints(currentPlayer.victoryPoints() + 1));

        return new GameState(
                state.gameId(),
                state.phase(),
                new GameBoard(state.board().hexes(), updatedIntersections, state.board().edges()),
                updatedPlayers,
                state.turn(),
                state.dice(),
                state.version() + 1,
                state.winnerPlayerId()
        );
    }

    private static GameState endTurn(GameState state, String playerId) {
        ensurePhase(state, GamePhase.IN_PROGRESS);
        ensureStage(state, TurnStage.MAIN);

        List<String> playerOrder = state.players().stream()
                .map(PlayerState::playerId)
                .toList();
        int currentIndex = playerOrder.indexOf(playerId);
        String nextPlayerId = playerOrder.get((currentIndex + 1) % playerOrder.size());

        return new GameState(
                state.gameId(),
                state.phase(),
                state.board(),
                state.players(),
                new TurnState(nextPlayerId, state.turn().turnNumber() + 1, TurnStage.ROLL),
                new DiceState(state.dice().lastRoll(), state.dice().dieA(), state.dice().dieB(), false),
                state.version() + 1,
                null
        );
    }

    private static void validateCurrentPlayer(GameState state, String playerId) {
        if (!Objects.equals(state.turn().currentPlayerId(), playerId)) {
            throw new IllegalArgumentException("NOT_YOUR_TURN");
        }
    }

    private static void ensurePhase(GameState state, GamePhase expected) {
        if (state.phase() != expected) {
            throw new IllegalArgumentException("INVALID_PHASE");
        }
    }

    private static void ensureStage(GameState state, TurnStage expected) {
        if (state.turn().stage() != expected) {
            throw new IllegalArgumentException("INVALID_ACTION");
        }
    }

    private static PlayerState player(GameState state, String playerId) {
        return state.players().stream()
                .filter(candidate -> candidate.playerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNAUTHORIZED_PLAYER"));
    }

    private static IntersectionState intersection(GameState state, String intersectionId) {
        return state.board().intersections().stream()
                .filter(candidate -> candidate.intersectionId().equals(intersectionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("INVALID_BUILD_LOCATION"));
    }

    private static List<PlayerState> distributeResources(List<PlayerState> players, GameBoard board, int roll) {
        List<PlayerState> updated = players;
        for (HexTileState hex : board.hexes()) {
            if (hex.numberToken() != roll) {
                continue;
            }
            for (String intersectionId : hex.adjacentIntersectionIds()) {
                IntersectionState intersection = board.intersections().stream()
                        .filter(candidate -> candidate.intersectionId().equals(intersectionId))
                        .findFirst()
                        .orElseThrow();
                if (intersection.ownerPlayerId() == null || intersection.buildingType() == null) {
                    continue;
                }
                int amount = intersection.buildingType() == BuildingType.CITY ? 2 : 1;
                updated = updatePlayers(updated, intersection.ownerPlayerId(),
                        currentPlayer -> gainResource(currentPlayer, hex.resourceType(), amount));
            }
        }
        return updated;
    }

    private static PlayerState gainResource(PlayerState player, ResourceType type, int amount) {
        Map<ResourceType, Integer> next = new HashMap<>(player.resources());
        next.put(type, next.getOrDefault(type, 0) + amount);
        return player.withResources(Map.copyOf(next));
    }

    private static PlayerState spendResources(PlayerState player, Map<ResourceType, Integer> cost) {
        requireResources(player, cost);
        Map<ResourceType, Integer> next = new HashMap<>(player.resources());
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            next.put(entry.getKey(), next.getOrDefault(entry.getKey(), 0) - entry.getValue());
        }
        return player.withResources(Map.copyOf(next));
    }

    private static void requireResources(PlayerState player, Map<ResourceType, Integer> cost) {
        for (Map.Entry<ResourceType, Integer> entry : cost.entrySet()) {
            if (player.resourceCount(entry.getKey()) < entry.getValue()) {
                throw new IllegalArgumentException("INSUFFICIENT_RESOURCES");
            }
        }
    }

    private static boolean edgeTouchesOwnedBuilding(GameState state, String playerId, EdgeState edge) {
        return state.board().intersections().stream()
                .filter(intersection -> intersection.intersectionId().equals(edge.fromIntersectionId())
                        || intersection.intersectionId().equals(edge.toIntersectionId()))
                .anyMatch(intersection -> playerId.equals(intersection.ownerPlayerId()));
    }

    private static boolean edgeTouchesOwnedRoad(GameState state, String playerId, EdgeState edge) {
        return state.board().edges().stream()
                .filter(candidate -> playerId.equals(candidate.roadOwnerPlayerId()))
                .anyMatch(candidate -> candidate.fromIntersectionId().equals(edge.fromIntersectionId())
                        || candidate.fromIntersectionId().equals(edge.toIntersectionId())
                        || candidate.toIntersectionId().equals(edge.fromIntersectionId())
                        || candidate.toIntersectionId().equals(edge.toIntersectionId()));
    }

    private static boolean touchesOwnedRoad(GameState state, String playerId, String intersectionId) {
        return state.board().edges().stream()
                .filter(edge -> playerId.equals(edge.roadOwnerPlayerId()))
                .anyMatch(edge -> edge.fromIntersectionId().equals(intersectionId) || edge.toIntersectionId().equals(intersectionId));
    }

    private static List<PlayerState> updatePlayers(List<PlayerState> players, String playerId, UnaryOperator<PlayerState> updater) {
        return players.stream()
                .map(candidate -> candidate.playerId().equals(playerId) ? updater.apply(candidate) : candidate)
                .toList();
    }

    private static String requiredString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("INVALID_ACTION");
        }
        return stringValue;
    }

    private record SetupProgress(GamePhase phase, TurnState turnState) {

        private static SetupProgress from(GameState state) {
            return new SetupProgress(state.phase(), state.turn());
        }

        private SetupProgress afterRoadPlacement(String playerId, List<PlayerState> players) {
            List<String> order = players.stream().map(PlayerState::playerId).toList();
            Map<String, Integer> settlementsByPlayer = new HashMap<>();
            for (PlayerState player : players) {
                settlementsByPlayer.put(player.playerId(), 5 - player.settlementsRemaining());
            }

            int currentIndex = order.indexOf(playerId);
            if (currentIndex < order.size() - 1) {
                return new SetupProgress(GamePhase.SETUP,
                        new TurnState(order.get(currentIndex + 1), turnState.turnNumber(), TurnStage.PLACE_INITIAL_SETTLEMENT));
            }

            boolean allPlacedOne = settlementsByPlayer.values().stream().allMatch(count -> count >= 1);
            if (allPlacedOne && settlementsByPlayer.values().stream().allMatch(count -> count == 1)) {
                return new SetupProgress(GamePhase.IN_PROGRESS, new TurnState(order.get(0), 1, TurnStage.ROLL));
            }

            return new SetupProgress(GamePhase.IN_PROGRESS, new TurnState(order.get(0), 1, TurnStage.ROLL));
        }
    }
}
