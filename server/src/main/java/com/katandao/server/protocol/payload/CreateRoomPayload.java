package com.katandao.server.protocol.payload;

public record CreateRoomPayload(
        String playerId,
        String playerName,
        RoomConfigPayload config
) {
}
