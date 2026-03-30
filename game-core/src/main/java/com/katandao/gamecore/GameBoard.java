package com.katandao.gamecore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GameBoard(
        List<HexTileState> hexes,
        List<PortState> ports,
        List<IntersectionState> intersections,
        List<EdgeState> edges
) {

    public static GameBoard initial() {
        List<HexSpec> specs = List.of(
                new HexSpec("H-01", 0, -2),
                new HexSpec("H-02", 1, -2),
                new HexSpec("H-03", 2, -2),
                new HexSpec("H-04", -1, -1),
                new HexSpec("H-05", 0, -1),
                new HexSpec("H-06", 1, -1),
                new HexSpec("H-07", 2, -1),
                new HexSpec("H-08", -2, 0),
                new HexSpec("H-09", -1, 0),
                new HexSpec("H-10", 0, 0),
                new HexSpec("H-11", 1, 0),
                new HexSpec("H-12", 2, 0),
                new HexSpec("H-13", -2, 1),
                new HexSpec("H-14", -1, 1),
                new HexSpec("H-15", 0, 1),
                new HexSpec("H-16", 1, 1),
                new HexSpec("H-17", -2, 2),
                new HexSpec("H-18", -1, 2),
                new HexSpec("H-19", 0, 2)
        );
        List<ResourceType> shuffledResources = shuffledResources();
        List<Integer> shuffledNumberTokens = shuffledNumberTokens();
        Map<String, HexAssignment> assignmentByHexId = new LinkedHashMap<>();
        int numberIndex = 0;
        for (int index = 0; index < specs.size(); index++) {
            HexSpec spec = specs.get(index);
            ResourceType resourceType = shuffledResources.get(index);
            int numberToken = resourceType == ResourceType.DESERT ? 0 : shuffledNumberTokens.get(numberIndex++);
            assignmentByHexId.put(spec.hexId(), new HexAssignment(resourceType, numberToken));
        }

        Map<String, Point> pointsByKey = new LinkedHashMap<>();
        Map<String, List<String>> vertexKeysByHexId = new LinkedHashMap<>();

        for (HexSpec spec : specs) {
            Point center = axialToPoint(spec.q(), spec.r());
            List<String> vertexKeys = new ArrayList<>();
            for (int cornerIndex = 0; cornerIndex < 6; cornerIndex++) {
                Point vertex = hexCorner(center, cornerIndex);
                String key = keyFor(vertex);
                pointsByKey.putIfAbsent(key, vertex);
                vertexKeys.add(key);
            }
            vertexKeysByHexId.put(spec.hexId(), vertexKeys);
        }

        List<String> desertVertexKeys = vertexKeysByHexId.get("H-10");
        Map<String, String> intersectionIdByKey = new LinkedHashMap<>();
        for (int index = 0; index < desertVertexKeys.size(); index++) {
            intersectionIdByKey.put(desertVertexKeys.get(index), "I-%02d".formatted(index + 1));
        }

        List<String> remainingVertexKeys = pointsByKey.keySet().stream()
                .filter(key -> !intersectionIdByKey.containsKey(key))
                .sorted(Comparator
                        .comparingDouble((String key) -> pointsByKey.get(key).y())
                        .thenComparingDouble(key -> pointsByKey.get(key).x()))
                .toList();
        int nextIntersectionIndex = 7;
        for (String key : remainingVertexKeys) {
            intersectionIdByKey.put(key, "I-%02d".formatted(nextIntersectionIndex++));
        }

        List<IntersectionState> intersections = intersectionIdByKey.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .map(entry -> {
                    Point point = pointsByKey.get(entry.getKey());
                    return new IntersectionState(entry.getValue(), null, null, point.x(), point.y());
                })
                .toList();

        List<HexTileState> hexes = specs.stream()
                .map(spec -> {
                    HexAssignment assignment = assignmentByHexId.get(spec.hexId());
                    return new HexTileState(
                            spec.hexId(),
                            assignment.resourceType(),
                            assignment.numberToken(),
                            spec.q(),
                            spec.r(),
                            vertexKeysByHexId.get(spec.hexId()).stream().map(intersectionIdByKey::get).toList()
                    );
                })
                .toList();

        Set<String> desertEdgeKeys = edgeKeysFor(vertexKeysByHexId.get("H-10")).stream().collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, String> edgeIdByKey = new LinkedHashMap<>();
        int desertEdgeIndex = 1;
        for (String edgeKey : desertEdgeKeys) {
            edgeIdByKey.put(edgeKey, "E-%02d".formatted(desertEdgeIndex++));
        }

        List<String> allEdgeKeys = specs.stream()
                .flatMap(spec -> edgeKeysFor(vertexKeysByHexId.get(spec.hexId())).stream())
                .distinct()
                .toList();
        List<String> remainingEdgeKeys = allEdgeKeys.stream()
                .filter(key -> !edgeIdByKey.containsKey(key))
                .sorted(Comparator
                        .comparingDouble((String key) -> midpoint(pointsByKey, key).y())
                        .thenComparingDouble(key -> midpoint(pointsByKey, key).x()))
                .toList();
        int nextEdgeIndex = 7;
        for (String edgeKey : remainingEdgeKeys) {
            edgeIdByKey.put(edgeKey, "E-%02d".formatted(nextEdgeIndex++));
        }

        List<EdgeState> edges = edgeIdByKey.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .map(entry -> {
                    String[] keys = entry.getKey().split("\\|");
                    return new EdgeState(
                            entry.getValue(),
                            intersectionIdByKey.get(keys[0]),
                            intersectionIdByKey.get(keys[1]),
                            null
                    );
                })
                .toList();

        return new GameBoard(hexes, defaultPorts(), intersections, edges);
    }

    private static List<PortState> defaultPorts() {
        return List.of(
                new PortState("P-01", "WOOD", 2, 280, 32, -28),
                new PortState("P-02", "ANY", 3, 640, 32, 28),
                new PortState("P-03", "ANY", 3, 798, 165, 88),
                new PortState("P-04", "BRICK", 2, 808, 395, 122),
                new PortState("P-05", "WOOD", 2, 685, 612, 150),
                new PortState("P-06", "ANY", 3, 470, 652, 180),
                new PortState("P-07", "WHEAT", 2, 252, 612, -150),
                new PortState("P-08", "ORE", 2, 130, 395, -122),
                new PortState("P-09", "ANY", 3, 120, 165, -88)
        );
    }

    private static List<ResourceType> shuffledResources() {
        List<ResourceType> resources = new ArrayList<>(List.of(
                ResourceType.WOOD, ResourceType.WOOD, ResourceType.WOOD, ResourceType.WOOD,
                ResourceType.BRICK, ResourceType.BRICK, ResourceType.BRICK,
                ResourceType.SHEEP, ResourceType.SHEEP, ResourceType.SHEEP, ResourceType.SHEEP,
                ResourceType.WHEAT, ResourceType.WHEAT, ResourceType.WHEAT, ResourceType.WHEAT,
                ResourceType.ORE, ResourceType.ORE, ResourceType.ORE,
                ResourceType.DESERT
        ));
        Collections.shuffle(resources);
        return List.copyOf(resources);
    }

    private static List<Integer> shuffledNumberTokens() {
        List<Integer> tokens = new ArrayList<>(List.of(
                2,
                3, 3,
                4, 4,
                5, 5,
                6, 6,
                8, 8,
                9, 9,
                10, 10,
                11, 11,
                12
        ));
        Collections.shuffle(tokens);
        return List.copyOf(tokens);
    }

    private static Point axialToPoint(int q, int r) {
        double radius = 82;
        return new Point(
                470 + Math.sqrt(3) * radius * (q + r / 2.0),
                320 + radius * 1.5 * r
        );
    }

    private static Point hexCorner(Point center, int cornerIndex) {
        double radius = 82;
        double angleDegrees = 60 * cornerIndex - 30;
        double angle = Math.toRadians(angleDegrees);
        return new Point(
                center.x() + radius * Math.cos(angle),
                center.y() + radius * Math.sin(angle)
        );
    }

    private static String keyFor(Point point) {
        return Math.round(point.x() * 1000) + ":" + Math.round(point.y() * 1000);
    }

    private static List<String> edgeKeysFor(List<String> vertexKeys) {
        List<String> edgeKeys = new ArrayList<>();
        for (int index = 0; index < vertexKeys.size(); index++) {
            String first = vertexKeys.get(index);
            String second = vertexKeys.get((index + 1) % vertexKeys.size());
            edgeKeys.add(first.compareTo(second) < 0 ? first + "|" + second : second + "|" + first);
        }
        return edgeKeys;
    }

    private static Point midpoint(Map<String, Point> pointsByKey, String edgeKey) {
        String[] keys = edgeKey.split("\\|");
        Point first = pointsByKey.get(keys[0]);
        Point second = pointsByKey.get(keys[1]);
        return new Point((first.x() + second.x()) / 2.0, (first.y() + second.y()) / 2.0);
    }

    private record HexSpec(String hexId, int q, int r) {
    }

    private record HexAssignment(ResourceType resourceType, int numberToken) {
    }

    private record Point(double x, double y) {
    }
}
