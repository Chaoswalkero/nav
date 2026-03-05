package com.patriot.nav.routing;

public class NearestNodeFinder {

    public static int findNearest(Graph g, double lat, double lon) {
        int best = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < g.size(); i++) {
            Graph.Node n = g.node(i);
            double d = GraphBuilder.distanceMeters(lat, lon, n.lat, n.lon);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
