package com.katandao.server.game;

import com.katandao.gamecore.GameEngine;
import com.katandao.gamecore.GameState;
import java.util.List;

public record GameSession(
        String gameId,
        String roomId,
        GameState gameState
) {

    public static GameSession create(String gameId, String roomId, List<String> playerIds) {
        return new GameSession(gameId, roomId, GameEngine.createInitialState(gameId, playerIds));
    }
}
