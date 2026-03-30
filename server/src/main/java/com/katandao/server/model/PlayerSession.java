package com.katandao.server.model;

public record PlayerSession(
        String playerId,
        String playerName,
        String webSocketSessionId
) {
}
