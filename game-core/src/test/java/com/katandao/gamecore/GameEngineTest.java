package com.katandao.gamecore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameEngineTest {

    @Test
    void shouldCreateInitialSetupState() {
        GameState state = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));

        assertEquals(GamePhase.SETUP, state.phase());
        assertEquals("p1", state.turn().currentPlayerId());
        assertEquals(TurnStage.PLACE_INITIAL_SETTLEMENT, state.turn().stage());
        assertEquals(6, state.board().intersections().size());
    }

    @Test
    void shouldAdvanceFromSettlementToRoadPlacement() {
        GameState initial = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));

        GameState updated = GameEngine.apply(
                initial,
                "p1",
                new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-01"))
        );

        assertEquals(TurnStage.PLACE_INITIAL_ROAD, updated.turn().stage());
        assertEquals("p1", updated.turn().currentPlayerId());
        assertEquals("p1", updated.board().intersections().get(0).ownerPlayerId());
        assertEquals(1, updated.players().get(0).victoryPoints());
    }

    @Test
    void shouldMoveToNextPlayerAfterInitialRoad() {
        GameState initial = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));
        GameState afterSettlement = GameEngine.apply(
                initial,
                "p1",
                new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-01"))
        );

        GameState afterRoad = GameEngine.apply(
                afterSettlement,
                "p1",
                new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-01"))
        );

        assertEquals("p2", afterRoad.turn().currentPlayerId());
        assertEquals(TurnStage.PLACE_INITIAL_SETTLEMENT, afterRoad.turn().stage());
    }

    @Test
    void shouldEnterMainGameAfterAllPlayersPlaceInitialRoads() {
        GameState state = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-01")));
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-01")));
        state = GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-03")));
        state = GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-02")));
        state = GameEngine.apply(state, "p3", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-05")));
        state = GameEngine.apply(state, "p3", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-05")));

        assertEquals(GamePhase.IN_PROGRESS, state.phase());
        assertEquals("p1", state.turn().currentPlayerId());
        assertEquals(TurnStage.ROLL, state.turn().stage());
    }

    @Test
    void shouldRollDiceDuringMainGame() {
        GameState state = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-01")));
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-01")));
        state = GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-03")));
        state = GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-02")));
        state = GameEngine.apply(state, "p3", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-05")));
        state = GameEngine.apply(state, "p3", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-05")));

        GameState rolled = GameEngine.apply(state, "p1", new GameAction(GameActionType.ROLL_DICE, Map.of()));

        assertEquals(TurnStage.MAIN, rolled.turn().stage());
        assertEquals(Integer.valueOf(5), rolled.dice().lastRoll());
        assertEquals(1, rolled.players().get(0).resourceCount(ResourceType.WOOD));
        assertEquals(1, rolled.players().get(0).resourceCount(ResourceType.BRICK));
        assertEquals(1, rolled.players().get(1).resourceCount(ResourceType.WOOD));
    }

    @Test
    void shouldRejectWrongTurnPlayer() {
        GameState state = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));

        assertThrows(IllegalArgumentException.class, () ->
                GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-01"))));
    }

    @Test
    void shouldBuildRoadInMainPhaseWhenResourcesAreAvailable() {
        GameState state = readyForMainPhase();
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.ROLL_DICE, Map.of()));
        state = withPlayerResources(state, "p1", Map.of(
                ResourceType.WOOD, 1,
                ResourceType.BRICK, 1
        ));

        GameState updated = GameEngine.apply(state, "p1", new GameAction(GameActionType.BUILD_ROAD, Map.of("edgeId", "E-06")));

        assertEquals("p1", updated.board().edges().get(5).roadOwnerPlayerId());
        assertEquals(13, updated.players().get(0).roadsRemaining());
        assertEquals(0, updated.players().get(0).resourceCount(ResourceType.WOOD));
    }

    @Test
    void shouldBuildSettlementInMainPhaseWhenConnectedRoadExists() {
        GameState state = readyForMainPhase();
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.ROLL_DICE, Map.of()));
        state = withPlayerResources(state, "p1", Map.of(
                ResourceType.WOOD, 1,
                ResourceType.BRICK, 1,
                ResourceType.SHEEP, 1,
                ResourceType.WHEAT, 1
        ));

        GameState updated = GameEngine.apply(state, "p1", new GameAction(GameActionType.BUILD_SETTLEMENT, Map.of("intersectionId", "I-02")));

        assertEquals("p1", updated.board().intersections().get(1).ownerPlayerId());
        assertEquals(2, updated.players().get(0).victoryPoints());
    }

    @Test
    void shouldUpgradeSettlementToCity() {
        GameState state = readyForMainPhase();
        state = new GameState(
                state.gameId(),
                state.phase(),
                new GameBoard(
                        state.board().hexes(),
                        List.of(
                                new IntersectionState("I-01", "p1", BuildingType.SETTLEMENT),
                                state.board().intersections().get(1),
                                state.board().intersections().get(2),
                                state.board().intersections().get(3),
                                state.board().intersections().get(4),
                                state.board().intersections().get(5)
                        ),
                        state.board().edges()
                ),
                List.of(
                        new PlayerState("p1", 14, 4, 4, 1, Map.of(
                                ResourceType.WOOD, 0,
                                ResourceType.BRICK, 0,
                                ResourceType.SHEEP, 0,
                                ResourceType.WHEAT, 2,
                                ResourceType.ORE, 3
                        ), "I-01"),
                        state.players().get(1),
                        state.players().get(2)
                ),
                new TurnState("p1", 1, TurnStage.MAIN),
                state.dice(),
                state.version(),
                state.winnerPlayerId()
        );

        GameState updated = GameEngine.apply(state, "p1", new GameAction(GameActionType.BUILD_CITY, Map.of("intersectionId", "I-01")));

        assertEquals(BuildingType.CITY, updated.board().intersections().get(0).buildingType());
        assertEquals(2, updated.players().get(0).victoryPoints());
    }

    private GameState readyForMainPhase() {
        GameState state = GameEngine.createInitialState("game-1", List.of("p1", "p2", "p3"));
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-01")));
        state = GameEngine.apply(state, "p1", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-01")));
        state = GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-03")));
        state = GameEngine.apply(state, "p2", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-02")));
        state = GameEngine.apply(state, "p3", new GameAction(GameActionType.PLACE_INITIAL_SETTLEMENT, Map.of("intersectionId", "I-05")));
        state = GameEngine.apply(state, "p3", new GameAction(GameActionType.PLACE_INITIAL_ROAD, Map.of("edgeId", "E-05")));
        return state;
    }

    private GameState withPlayerResources(GameState state, String playerId, Map<ResourceType, Integer> resources) {
        return new GameState(
                state.gameId(),
                state.phase(),
                state.board(),
                state.players().stream()
                        .map(player -> player.playerId().equals(playerId)
                                ? player.withResources(Map.of(
                                ResourceType.WOOD, resources.getOrDefault(ResourceType.WOOD, 0),
                                ResourceType.BRICK, resources.getOrDefault(ResourceType.BRICK, 0),
                                ResourceType.SHEEP, resources.getOrDefault(ResourceType.SHEEP, 0),
                                ResourceType.WHEAT, resources.getOrDefault(ResourceType.WHEAT, 0),
                                ResourceType.ORE, resources.getOrDefault(ResourceType.ORE, 0)
                        ))
                                : player)
                        .toList(),
                state.turn(),
                state.dice(),
                state.version(),
                state.winnerPlayerId()
        );
    }
}
