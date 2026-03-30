package com.katandao.server.protocol.payload;

public record HelloPayload(
        String playerId,
        String playerName
) {
}
