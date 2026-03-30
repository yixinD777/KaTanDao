package com.katandao.server.model;

import java.util.List;

public record RoomState(
        String roomId,
        RoomStatus status,
        String hostPlayerId,
        int maxPlayers,
        List<RoomPlayer> players
) {
}
