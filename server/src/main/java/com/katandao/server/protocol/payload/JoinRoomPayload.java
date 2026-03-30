package com.katandao.server.protocol.payload;

public record JoinRoomPayload(
        String roomId,
        String playerId,
        String playerName
) {
}
