package com.katandao.gamecore;

import java.util.List;

public record HexTileState(
        String hexId,
        ResourceType resourceType,
        int numberToken,
        List<String> adjacentIntersectionIds
) {
}
