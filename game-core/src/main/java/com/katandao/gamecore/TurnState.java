package com.katandao.gamecore;

public record TurnState(
        String currentPlayerId,
        int turnNumber,
        TurnStage stage
) {
}
