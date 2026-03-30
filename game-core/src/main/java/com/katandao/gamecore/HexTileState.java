package com.katandao.gamecore;

import java.util.List;

public record HexTileState(
        String hexId,
        ResourceType resourceType,
        int numberToken,
        int q,
        int r,
        List<String> adjacentIntersectionIds
) {
}
