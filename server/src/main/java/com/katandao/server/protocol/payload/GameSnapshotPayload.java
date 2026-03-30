package com.katandao.server.protocol.payload;

import com.katandao.gamecore.GameState;
import com.katandao.server.game.GameActionType;
import java.util.List;

public record GameSnapshotPayload(
        String roomId,
        String gameId,
        long version,
        String phase,
        TurnPayload turn,
        List<LegalActionPayload> legalActions,
        String winner,
        GameState state
) {
    public record TurnPayload(
            String currentPlayerId,
            int turnNumber,
            String stage
    ) {
    }

    public record LegalActionPayload(
            GameActionType actionType,
            List<String> targets
    ) {
    }
}
