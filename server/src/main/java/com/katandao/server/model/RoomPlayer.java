package com.katandao.server.model;

public record RoomPlayer(
        String playerId,
        String playerName,
        int seat,
        boolean ready,
        boolean connected
) {
}
