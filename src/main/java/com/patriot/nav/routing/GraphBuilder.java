package com.patriot.nav.routing;

import lombok.extern.slf4j.Slf4j;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a routing graph directly from OSM .pbf file using Osmosis CORE
 */
@Slf4j
public class GraphBuilder {

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public static Graph buildFromPbf(String pbfPath) throws Exception {
        Graph graph = new Graph();
        Map<Long, Integer> nodeIndex = new HashMap<>();

        Sink sink = new Sink() {
            @Override
            public void process(EntityContainer container) {

                if (container.getEntity() instanceof Node node) {
                    int idx = graph.addNode(node.getId(), node.getLatitude(), node.getLongitude());
                    nodeIndex.put(node.getId(), idx);
                }

                if (container.getEntity() instanceof Way way) {
                    // collect some useful tags for later decisions
                    Map<String,String> tags = new HashMap<>();
                    for (Tag t : way.getTags()) {
                        tags.put(t.getKey(), t.getValue());
                    }

                    if (!tags.containsKey("highway"))
                        return; // not a road

                    // determine base speed in m/s using highway type; fall back to 13.9
                    double speedMs = 13.9;
                    String hw = tags.get("highway");
                    if (hw != null) {
                        switch (hw) {
                            case "motorway" -> speedMs = 33.3; // 120 km/h
                            case "trunk" -> speedMs = 27.7;
                            case "primary" -> speedMs = 22.2;
                            case "secondary" -> speedMs = 19.4;
                            case "tertiary" -> speedMs = 16.6;
                            case "residential" -> speedMs = 11.1;
                            case "service" -> speedMs = 8.3;
                            default -> speedMs = 13.9;
                        }
                    }

                    // maxspeed tag (km/h) reduces the base speed
                    String maxspeedTag = tags.get("maxspeed");
                    if (maxspeedTag != null) {
                        try {
                            double m = Double.parseDouble(maxspeedTag.replaceAll("[^0-9.]", ""));
                            speedMs = Math.min(speedMs, m / 3.6);
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    // oneway handling
                    boolean forward = true;
                    boolean backward = true;
                    String oneway = tags.get("oneway");
                    if ("yes".equals(oneway)) backward = false;
                    else if ("-1".equals(oneway)) forward = false;

                    for (int i = 0; i < way.getWayNodes().size() - 1; i++) {
                        long fromId = way.getWayNodes().get(i).getNodeId();
                        long toId = way.getWayNodes().get(i + 1).getNodeId();

                        Integer from = nodeIndex.get(fromId);
                        Integer to = nodeIndex.get(toId);

                        if (from == null || to == null) continue;

                        double lat1 = graph.node(from).getLat();
                        double lon1 = graph.node(from).getLon();
                        double lat2 = graph.node(to).getLat();
                        double lon2 = graph.node(to).getLon();

                        double dist = distanceMeters(lat1, lon1, lat2, lon2);
                        double time = dist / speedMs;

                        if (forward) graph.addEdge(from, to, time);
                        if (backward) graph.addEdge(to, from, time);
                    }
                }
            }

            @Override public void initialize(Map<String, Object> metaData) {}
            @Override public void complete() {}
            @Override public void close() {}
        };

        RunnableSource reader = new PbfReader(new java.io.File(pbfPath), 1);
        reader.setSink(sink);
        reader.run();

        log.info("OSM Graph built: {} nodes", graph.size());
        return graph;
    }
}