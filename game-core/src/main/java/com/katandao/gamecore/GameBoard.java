package com.katandao.gamecore;

import java.util.List;

public record GameBoard(
        List<HexTileState> hexes,
        List<IntersectionState> intersections,
        List<EdgeState> edges
) {

    public static GameBoard initial() {
        return new GameBoard(
                List.of(
                        new HexTileState("H-01", ResourceType.WOOD, 5, List.of("I-01", "I-02", "I-03")),
                        new HexTileState("H-04", ResourceType.BRICK, 5, List.of("I-01", "I-06", "I-02")),
                        new HexTileState("H-02", ResourceType.BRICK, 6, List.of("I-03", "I-04", "I-05")),
                        new HexTileState("H-03", ResourceType.WHEAT, 8, List.of("I-05", "I-06", "I-01"))
                ),
                List.of(
                        new IntersectionState("I-01", null, null),
                        new IntersectionState("I-02", null, null),
                        new IntersectionState("I-03", null, null),
                        new IntersectionState("I-04", null, null),
                        new IntersectionState("I-05", null, null),
                        new IntersectionState("I-06", null, null)
                ),
                List.of(
                        new EdgeState("E-01", "I-01", "I-02", null),
                        new EdgeState("E-02", "I-02", "I-03", null),
                        new EdgeState("E-03", "I-03", "I-04", null),
                        new EdgeState("E-04", "I-04", "I-05", null),
                        new EdgeState("E-05", "I-05", "I-06", null),
                        new EdgeState("E-06", "I-06", "I-01", null)
                )
        );
    }
}
