package com.katandao.server.protocol;

import java.time.Instant;

public record OutboundMessage(
        String type,
        String requestId,
        Instant timestamp,
        Object payload
) {
}
