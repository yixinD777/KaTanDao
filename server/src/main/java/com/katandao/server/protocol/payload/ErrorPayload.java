package com.katandao.server.protocol.payload;

import java.util.Map;

public record ErrorPayload(
        String code,
        String message,
        Map<String, Object> details
) {
}
