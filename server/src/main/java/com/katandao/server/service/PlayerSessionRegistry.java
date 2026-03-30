package com.katandao.server.service;

import com.katandao.server.model.PlayerSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
public class PlayerSessionRegistry {

    private final Map<String, PlayerSession> byPlayerId = new ConcurrentHashMap<>();
    private final Map<String, String> playerIdBySessionId = new ConcurrentHashMap<>();

    public PlayerSession register(WebSocketSession session, String playerId, String playerName) {
        PlayerSession playerSession = new PlayerSession(playerId, playerName, session.getId());
        byPlayerId.put(playerId, playerSession);
        playerIdBySessionId.put(session.getId(), playerId);
        return playerSession;
    }

    public Optional<PlayerSession> findByPlayerId(String playerId) {
        return Optional.ofNullable(byPlayerId.get(playerId));
    }

    public Optional<PlayerSession> findBySessionId(String sessionId) {
        String playerId = playerIdBySessionId.get(sessionId);
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byPlayerId.get(playerId));
    }

    public boolean isSessionOwnedBy(WebSocketSession session, String playerId) {
        return findBySessionId(session.getId())
                .map(player -> player.playerId().equals(playerId))
                .orElse(false);
    }

    public void unregisterBySessionId(String sessionId) {
        String playerId = playerIdBySessionId.remove(sessionId);
        if (playerId != null) {
            byPlayerId.remove(playerId);
        }
    }
}
