package com.katandao.gamecore;

import java.util.EnumMap;
import java.util.Map;

public record PlayerState(
        String playerId,
        int roadsRemaining,
        int settlementsRemaining,
        int citiesRemaining,
        int victoryPoints,
        Map<ResourceType, Integer> resources,
        String lastPlacedIntersectionId
) {

    public PlayerState withRoadsRemaining(int nextRoadsRemaining) {
        return new PlayerState(playerId, nextRoadsRemaining, settlementsRemaining, citiesRemaining, victoryPoints, resources, lastPlacedIntersectionId);
    }

    public PlayerState withSettlementsRemaining(int nextSettlementsRemaining) {
        return new PlayerState(playerId, roadsRemaining, nextSettlementsRemaining, citiesRemaining, victoryPoints, resources, lastPlacedIntersectionId);
    }

    public PlayerState withCitiesRemaining(int nextCitiesRemaining) {
        return new PlayerState(playerId, roadsRemaining, settlementsRemaining, nextCitiesRemaining, victoryPoints, resources, lastPlacedIntersectionId);
    }

    public PlayerState withVictoryPoints(int nextVictoryPoints) {
        return new PlayerState(playerId, roadsRemaining, settlementsRemaining, citiesRemaining, nextVictoryPoints, resources, lastPlacedIntersectionId);
    }

    public PlayerState withLastPlacedIntersectionId(String intersectionId) {
        return new PlayerState(playerId, roadsRemaining, settlementsRemaining, citiesRemaining, victoryPoints, resources, intersectionId);
    }

    public PlayerState withResources(Map<ResourceType, Integer> nextResources) {
        return new PlayerState(playerId, roadsRemaining, settlementsRemaining, citiesRemaining, victoryPoints, nextResources, lastPlacedIntersectionId);
    }

    public int resourceCount(ResourceType type) {
        return resources.getOrDefault(type, 0);
    }

    public static Map<ResourceType, Integer> emptyResources() {
        EnumMap<ResourceType, Integer> resources = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            resources.put(type, 0);
        }
        return Map.copyOf(resources);
    }
}
