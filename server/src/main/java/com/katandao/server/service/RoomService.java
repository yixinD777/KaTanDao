package com.katandao.server.service;

import com.katandao.server.model.RoomPlayer;
import com.katandao.server.model.RoomState;
import com.katandao.server.model.RoomStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomState createRoom(String playerId, String playerName, Integer requestedMaxPlayers) {
        int maxPlayers = requestedMaxPlayers == null ? 4 : requestedMaxPlayers;
        Room room = new Room(generateRoomId(), playerId, maxPlayers);
        room.addPlayer(playerId, playerName);
        rooms.put(room.roomId, room);
        return room.toState();
    }

    public Optional<RoomState> joinRoom(String roomId, String playerId, String playerName) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return Optional.empty();
        }
        room.addPlayer(playerId, playerName);
        return Optional.of(room.toState());
    }

    public Optional<RoomState> findRoomState(String roomId) {
        Room room = rooms.get(roomId);
        return room == null ? Optional.empty() : Optional.of(room.toState());
    }

    public Optional<RoomState> setReady(String roomId, String playerId, boolean ready) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return Optional.empty();
        }
        room.setReady(playerId, ready);
        return Optional.of(room.toState());
    }

    public Optional<RoomState> startGame(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return Optional.empty();
        }
        room.startGame(playerId);
        return Optional.of(room.toState());
    }

    private String generateRoomId() {
        return "room-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static final class Room {
        private final String roomId;
        private final String hostPlayerId;
        private final int maxPlayers;
        private final Map<String, RoomPlayer> players = new LinkedHashMap<>();
        private RoomStatus status = RoomStatus.WAITING;

        private Room(String roomId, String hostPlayerId, int maxPlayers) {
            this.roomId = roomId;
            this.hostPlayerId = hostPlayerId;
            this.maxPlayers = maxPlayers;
        }

        private synchronized void addPlayer(String playerId, String playerName) {
            ensureStatus(RoomStatus.WAITING, "GAME_ALREADY_STARTED");
            if (players.containsKey(playerId)) {
                RoomPlayer existing = players.get(playerId);
                players.put(playerId, new RoomPlayer(
                        existing.playerId(),
                        playerName,
                        existing.seat(),
                        existing.ready(),
                        true
                ));
                return;
            }
            if (players.size() >= maxPlayers) {
                throw new IllegalStateException("ROOM_FULL");
            }
            players.put(playerId, new RoomPlayer(playerId, playerName, players.size(), false, true));
        }

        private synchronized void setReady(String playerId, boolean ready) {
            ensureStatus(RoomStatus.WAITING, "GAME_ALREADY_STARTED");
            RoomPlayer existing = players.get(playerId);
            if (existing == null) {
                throw new IllegalStateException("PLAYER_NOT_IN_ROOM");
            }
            players.put(playerId, new RoomPlayer(
                    existing.playerId(),
                    existing.playerName(),
                    existing.seat(),
                    ready,
                    existing.connected()
            ));
        }

        private synchronized void startGame(String playerId) {
            ensureStatus(RoomStatus.WAITING, "GAME_ALREADY_STARTED");
            if (!hostPlayerId.equals(playerId)) {
                throw new IllegalStateException("NOT_ROOM_HOST");
            }
            if (players.size() < 3) {
                throw new IllegalStateException("NOT_ENOUGH_PLAYERS");
            }
            boolean allReady = players.values().stream().allMatch(RoomPlayer::ready);
            if (!allReady) {
                throw new IllegalStateException("NOT_ALL_PLAYERS_READY");
            }
            status = RoomStatus.IN_GAME;
        }

        private synchronized RoomState toState() {
            List<RoomPlayer> orderedPlayers = new ArrayList<>(players.values());
            return new RoomState(roomId, status, hostPlayerId, maxPlayers, orderedPlayers);
        }

        private void ensureStatus(RoomStatus expectedStatus, String errorCode) {
            if (status != expectedStatus) {
                throw new IllegalStateException(errorCode);
            }
        }
    }
}
