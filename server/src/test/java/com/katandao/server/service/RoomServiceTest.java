package com.katandao.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.katandao.server.model.RoomState;
import com.katandao.server.model.RoomStatus;
import org.junit.jupiter.api.Test;

class RoomServiceTest {

    private final RoomService roomService = new RoomService();

    @Test
    void shouldCreateRoomWithHostAsFirstPlayer() {
        RoomState roomState = roomService.createRoom("player-a", "Alice", 4);

        assertTrue(roomState.roomId().startsWith("room-"));
        assertEquals("player-a", roomState.hostPlayerId());
        assertEquals(1, roomState.players().size());
        assertEquals("player-a", roomState.players().get(0).playerId());
        assertEquals(0, roomState.players().get(0).seat());
    }

    @Test
    void shouldJoinRoomAndAssignNextSeat() {
        RoomState roomState = roomService.createRoom("player-a", "Alice", 4);

        RoomState updatedRoomState = roomService.joinRoom(roomState.roomId(), "player-b", "Bob")
                .orElseThrow();

        assertEquals(2, updatedRoomState.players().size());
        assertEquals("player-b", updatedRoomState.players().get(1).playerId());
        assertEquals(1, updatedRoomState.players().get(1).seat());
    }

    @Test
    void shouldRejectJoiningFullRoom() {
        RoomState roomState = roomService.createRoom("player-a", "Alice", 1);

        assertThrows(IllegalStateException.class, () -> roomService.joinRoom(roomState.roomId(), "player-b", "Bob"));
    }

    @Test
    void shouldSetPlayerReadyState() {
        RoomState roomState = roomService.createRoom("player-a", "Alice", 4);

        RoomState updatedRoomState = roomService.setReady(roomState.roomId(), "player-a", true)
                .orElseThrow();

        assertTrue(updatedRoomState.players().get(0).ready());
    }

    @Test
    void shouldStartGameWhenAllPlayersReady() {
        RoomState roomState = roomService.createRoom("player-a", "Alice", 4);
        roomService.joinRoom(roomState.roomId(), "player-b", "Bob");
        roomService.joinRoom(roomState.roomId(), "player-c", "Carol");
        roomService.setReady(roomState.roomId(), "player-a", true);
        roomService.setReady(roomState.roomId(), "player-b", true);
        roomService.setReady(roomState.roomId(), "player-c", true);

        RoomState started = roomService.startGame(roomState.roomId(), "player-a")
                .orElseThrow();

        assertEquals(RoomStatus.IN_GAME, started.status());
    }

    @Test
    void shouldRejectStartGameWhenNotAllPlayersReady() {
        RoomState roomState = roomService.createRoom("player-a", "Alice", 4);
        roomService.joinRoom(roomState.roomId(), "player-b", "Bob");
        roomService.joinRoom(roomState.roomId(), "player-c", "Carol");
        roomService.setReady(roomState.roomId(), "player-a", true);
        roomService.setReady(roomState.roomId(), "player-b", true);

        assertThrows(IllegalStateException.class, () -> roomService.startGame(roomState.roomId(), "player-a"));
    }
}
