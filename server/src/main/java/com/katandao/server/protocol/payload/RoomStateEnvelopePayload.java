package com.katandao.server.protocol.payload;

import com.katandao.server.model.RoomState;

public record RoomStateEnvelopePayload(
        RoomState room
) {
}
