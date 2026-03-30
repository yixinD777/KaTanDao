package com.katandao.gamecore;

import java.util.List;

public record GameState(
        String gameId,
        GamePhase phase,
        GameBoard board,
        List<PlayerState> players,
        TurnState turn,
        DiceState dice,
        long version,
        String winnerPlayerId
) {
}
