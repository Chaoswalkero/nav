package com.patriot.nav.routing;

import com.patriot.nav.model.VehicleProfile;

public class CompressedNearestNodeFinder {

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Find the nearest node to the given coordinate that the specified vehicle
     * profile is allowed to use.  Returns -1 if no such node exists.
     */
    public static int findNearest(CompressedGraph graph, double lat, double lon, VehicleProfile vehicle) {
        int bestIdx = -1;
        double bestDist = Double.POSITIVE_INFINITY;

        int n = graph.nodeCount();
        CompressedDijkstraRouter router = new CompressedDijkstraRouter();
        for (int i = 0; i < n; i++) {
            if (!hasAccessibleEdge(graph, i, vehicle, router))
                continue;

            double d = distanceMeters(lat, lon, graph.lat(i), graph.lon(i));
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static boolean hasAccessibleEdge(CompressedGraph g, int node, VehicleProfile vehicle, CompressedDijkstraRouter router) {
        int edge = g.firstEdge(node);
        while (edge != -1) {
            if (router.isEdgeAllowed(g, edge, vehicle)) {
                return true;
            }
            edge = g.edgeNext(edge);
        }
        return false;
    }

    // leave original method for backwards compatibility, though it no longer
    // considers vehicle restrictions; callers should prefer the 4‑arg version.
    public static int findNearest(CompressedGraph graph, double lat, double lon) {
        int bestIdx = -1;
        double bestDist = Double.POSITIVE_INFINITY;

        int n = graph.nodeCount();
        for (int i = 0; i < n; i++) {
            double d = distanceMeters(lat, lon, graph.lat(i), graph.lon(i));
            if (d < bestDist) {
                bestDist = d;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
}
