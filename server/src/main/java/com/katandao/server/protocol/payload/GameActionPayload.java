package com.katandao.server.protocol.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.katandao.server.game.GameActionType;

public record GameActionPayload(
        String roomId,
        String gameId,
        String playerId,
        ActionBody action
) {
    public record ActionBody(
            GameActionType actionType,
            JsonNode data
    ) {
    }
}
