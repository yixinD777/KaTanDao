package com.katandao.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public record InboundMessage(
        String type,
        String requestId,
        JsonNode payload
) {
}
