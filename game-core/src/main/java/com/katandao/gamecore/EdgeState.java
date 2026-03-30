package com.katandao.gamecore;

public record EdgeState(
        String edgeId,
        String fromIntersectionId,
        String toIntersectionId,
        String roadOwnerPlayerId
) {

    public EdgeState withRoadOwner(String playerId) {
        return new EdgeState(edgeId, fromIntersectionId, toIntersectionId, playerId);
    }
}
