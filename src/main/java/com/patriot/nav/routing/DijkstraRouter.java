package com.patriot.nav.routing;

import java.util.*;

public class DijkstraRouter {

    public record Result(List<Integer> path, double cost) {}

    public Result shortestPath(Graph g, int source, int target) {
        int n = g.size();
        double[] dist = new double[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        dist[source] = 0;

        PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingDouble(i -> dist[i]));
        pq.add(source);
        boolean[] visited = new boolean[n];

        while (!pq.isEmpty()) {
            int u = pq.poll();
            if (visited[u]) continue;
            visited[u] = true;
            if (u == target) break;

            for (Graph.Edge e : g.edgesFrom(u)) {
                int v = e.to;
                double alt = dist[u] + e.weight;
                if (alt < dist[v]) {
                    dist[v] = alt;
                    prev[v] = u;
                    pq.add(v);
                }
            }
        }

        if (Double.isInfinite(dist[target])) return null;

        List<Integer> path = new ArrayList<>();
        for (int at = target; at != -1; at = prev[at]) path.add(at);
        Collections.reverse(path);
        return new Result(path, dist[target]);
    }
}
