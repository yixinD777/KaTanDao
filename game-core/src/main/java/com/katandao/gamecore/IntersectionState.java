package com.katandao.gamecore;

public record IntersectionState(
        String intersectionId,
        String ownerPlayerId,
        BuildingType buildingType
) {

    public IntersectionState withBuilding(String playerId, BuildingType nextBuildingType) {
        return new IntersectionState(intersectionId, playerId, nextBuildingType);
    }
}
