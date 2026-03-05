package com.patriot.nav.routing;

import java.util.*;

import lombok.Getter;


public class Graph {
	
	
	@Getter
    public static class Node {
        public final long osmId;
        public final double lat;
        public final double lon;
        
        public Node(long osmId, double lat, double lon) {
            this.osmId = osmId;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class Edge {
        public final int to;
        public final double weight;

        public Edge(int to, double weight) {
            this.to = to;
            this.weight = weight;
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<Long, Integer> osmIdToIndex = new HashMap<>();
    private final List<List<Edge>> adj = new ArrayList<>();

    public int addNode(long osmId, double lat, double lon) {
        int idx = nodes.size();
        nodes.add(new Node(osmId, lat, lon));
        osmIdToIndex.put(osmId, idx);
        adj.add(new ArrayList<>());
        return idx;
    }

    public void addEdge(int from, int to, double weight) {
        adj.get(from).add(new Edge(to, weight));
    }

    public int size() {
        return nodes.size();
    }

    public Node node(int idx) {
        return nodes.get(idx);
    }

    public List<Edge> edgesFrom(int idx) {
        return adj.get(idx);
    }

    public Integer indexOfOsmId(long id) {
        return osmIdToIndex.get(id);
    }
}
