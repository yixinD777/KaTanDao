package com.katandao.gamecore;

public record PortState(
        String portId,
        String tradeType,
        int ratio,
        double x,
        double y,
        int rotationDegrees
) {
}
