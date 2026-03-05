package com.patriot.nav.routing;

import lombok.extern.slf4j.Slf4j;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;

import java.io.File;
import java.util.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;

@Slf4j
public class CompressedGraphBuilder {

        // Using primitive fastutil collections to reduce heap overhead for large PBFs

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

    private static byte mapHighwayType(String highway) {
        if (highway == null) return HighwayType.OTHER;
        return switch (highway) {
            case "motorway", "motorway_link" -> HighwayType.MOTORWAY;
            case "trunk", "trunk_link" -> HighwayType.TRUNK;
            case "primary", "primary_link" -> HighwayType.PRIMARY;
            case "secondary", "secondary_link" -> HighwayType.SECONDARY;
            case "tertiary", "tertiary_link" -> HighwayType.TERTIARY;
            case "residential", "living_street" -> HighwayType.RESIDENTIAL;
            case "service" -> HighwayType.SERVICE;
            case "track" -> HighwayType.TRACK;
            case "path" -> HighwayType.PATH;
            case "cycleway" -> HighwayType.CYCLEWAY;
            case "footway", "pedestrian", "sidewalk" -> HighwayType.FOOTWAY;
            case "steps" -> HighwayType.STEPS;
            default -> HighwayType.OTHER;
        };
    }

    // m/s
    private static float defaultSpeedFor(byte highwayType) {
        return switch (highwayType) {
            case HighwayType.MOTORWAY -> 33.3f;
            case HighwayType.TRUNK -> 27.7f;
            case HighwayType.PRIMARY -> 22.2f;
            case HighwayType.SECONDARY -> 19.4f;
            case HighwayType.TERTIARY -> 16.6f;
            case HighwayType.RESIDENTIAL -> 11.1f;
            case HighwayType.SERVICE -> 8.3f;
            case HighwayType.TRACK -> 6.9f;
            case HighwayType.PATH -> 4.2f;
            case HighwayType.CYCLEWAY -> 6.9f;
            case HighwayType.FOOTWAY -> 4.2f;
            case HighwayType.STEPS -> 1.5f;
            default -> 11.1f;
        };
    }

    public static CompressedGraph buildFromPbf(String pbfPath) throws Exception {
        log.info("Building compressed graph from PBF: {}", pbfPath);

        LongOpenHashSet usedNodeIds = new LongOpenHashSet();

        // 1. Pass: Sammle alle Nodes, die zu Straßen gehören
        Sink waySink = new Sink() {
            @Override
            public void process(EntityContainer container) {
                Entity e = container.getEntity();
                if (e instanceof Way way) {
                    for (Tag t : way.getTags()) {
                        if ("highway".equals(t.getKey())) {
                            way.getWayNodes().forEach(wn -> usedNodeIds.add(wn.getNodeId()));
                            break;
                        }
                    }
                }
            }
            @Override public void initialize(Map<String, Object> metaData) {}
            @Override public void complete() {}
            @Override public void close() {}
        };

        RunnableSource reader1 = new PbfReader(new File(pbfPath), 1);
        reader1.setSink(waySink);
        reader1.run();

        log.info("Collected {} street nodes", usedNodeIds.size());

        // primitive lists for nodes
        LongArrayList nodeOsmIds = new LongArrayList();
        DoubleArrayList nodeLats = new DoubleArrayList();
        DoubleArrayList nodeLons = new DoubleArrayList();
        Long2IntOpenHashMap osmIdToIndex = new Long2IntOpenHashMap();
        osmIdToIndex.defaultReturnValue(-1);

        // primitive lists for edges
        IntArrayList edgeFromList = new IntArrayList();
        IntArrayList edgeToList = new IntArrayList();
        FloatArrayList edgeWeightList = new FloatArrayList();
        FloatArrayList edgeDistanceList = new FloatArrayList();
        FloatArrayList edgeSpeedList = new FloatArrayList();
        IntArrayList edgeFlagsList = new IntArrayList();
        ByteArrayList edgeHighwayTypeList = new ByteArrayList();
        ObjectArrayList<String> edgeWayTypeList = new ObjectArrayList<>();
        ObjectArrayList<Map<String,String>> edgeTagsList = new ObjectArrayList<>();
        BooleanArrayList edgeOneWayList = new BooleanArrayList();

        // 2. Pass: Baue Nodes + Edges
        Sink sink = new Sink() {
            @SuppressWarnings("unused")
			@Override
            public void process(EntityContainer container) {
                Entity e = container.getEntity();

                // --- NODE ---
                if (e instanceof Node node) {
                    if (!usedNodeIds.contains(node.getId())) return;
                    int idx = nodeOsmIds.size();
                    nodeOsmIds.add(node.getId());
                    nodeLats.add(node.getLatitude());
                    nodeLons.add(node.getLongitude());
                    osmIdToIndex.put(node.getId(), idx);
                }

                // --- WAY ---
                if (e instanceof Way way) {
                    // tags that we explicitly examine later; everything else is kept in the
                    // `allTags` map so that routing/weighting logic can make use of arbitrary
                    // OSM parameters without needing to re‑parse the file.
                    String highway = null;
                    String oneway = null;
                    String access = null;
                    String motorVehicle = null;
                    String bicycle = null;
                    String foot = null;
                    String surface = null;
                    String maxspeed = null;
                    String incline = null;
                    String bridge = null;
                    String tunnel = null;

                    Map<String,String> allTags = new HashMap<>();

                    for (Tag t : way.getTags()) {
                        allTags.put(t.getKey(), t.getValue());
                        switch (t.getKey()) {
                            case "highway" -> highway = t.getValue();
                            case "oneway" -> oneway = t.getValue();
                            case "access" -> access = t.getValue();
                            case "motor_vehicle" -> motorVehicle = t.getValue();
                            case "bicycle" -> bicycle = t.getValue();
                            case "foot" -> foot = t.getValue();
                            case "surface" -> surface = t.getValue();
                            case "maxspeed" -> maxspeed = t.getValue();
                            case "incline" -> incline = t.getValue();
                            case "bridge" -> bridge = t.getValue();
                            case "tunnel" -> tunnel = t.getValue();
                        }
                    }

                    if (highway == null) return;

                    byte hwType = mapHighwayType(highway);
                    float baseSpeedMs = defaultSpeedFor(hwType);
                    float baseSpeedKmh = baseSpeedMs * 3.6f;

                    // adjust speeds with explicit maxspeed tag if present
                    if (maxspeed != null) {
                        try {
                            float parsed = Float.parseFloat(maxspeed.replaceAll("[^0-9.]", ""));
                            // OSM maxspeed usually in km/h unless named 'mph'
                            // keep the lower of the two speeds
                            baseSpeedKmh = Math.min(baseSpeedKmh, parsed);
                            baseSpeedMs = baseSpeedKmh / 3.6f;
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    // incline slows traffic slightly (simple linear penalty)
                    if (incline != null) {
                        try {
                            float inc;
                            if (incline.endsWith("%")) {
                                inc = Float.parseFloat(incline.substring(0, incline.length() - 1));
                            } else {
                                inc = Float.parseFloat(incline);
                            }
                            baseSpeedMs *= Math.max(0.2f, 1.0f - Math.abs(inc) / 100.0f);
                            baseSpeedKmh = baseSpeedMs * 3.6f;
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    // ---------------------------------------------------------
                    // access flags based on explicit tags; we make the graph
                    // describe what modes are allowed, the router will later
                    // intersect this with the vehicle profile.
                    int baseFlags = 0;

                    // start permissive, then remove based on "no" tags
                    baseFlags |= EdgeFlags.ACCESS_FOOT | EdgeFlags.ACCESS_BICYCLE | EdgeFlags.ACCESS_MOTOR_VEHICLE
                            | EdgeFlags.ACCESS_HGV | EdgeFlags.ACCESS_BUS | EdgeFlags.ACCESS_TAXI
                            | EdgeFlags.ACCESS_EMERGENCY | EdgeFlags.ACCESS_DELIVERY | EdgeFlags.ACCESS_AGRICULTURAL;

                    if (access != null) {
                        if ("no".equals(access) || "private".equals(access) || "restricted".equals(access)) {
                            // zero out everything except emergency services
                            baseFlags &= EdgeFlags.ACCESS_EMERGENCY;
                        }
                    }
                    if ("no".equals(motorVehicle)) baseFlags &= ~EdgeFlags.ACCESS_MOTOR_VEHICLE;
                    if ("no".equals(bicycle)) baseFlags &= ~EdgeFlags.ACCESS_BICYCLE;
                    if ("no".equals(foot)) baseFlags &= ~EdgeFlags.ACCESS_FOOT;

                    // Surface-Flags bleiben erhalten
                    if (surface != null) {
                        if (surface.contains("unpaved") || surface.contains("gravel") || surface.contains("dirt"))
                            baseFlags |= EdgeFlags.SURFACE_UNPAVED;
                        if (surface.contains("track"))
                            baseFlags |= EdgeFlags.SURFACE_TRACK;
                        if (surface.contains("steps"))
                            baseFlags |= EdgeFlags.SURFACE_STEPS;
                    }

                    boolean forward = true;
                    boolean backward = true;

                    if ("yes".equals(oneway)) backward = false;
                    else if ("-1".equals(oneway)) forward = false;

                    var nodes = way.getWayNodes();
                    for (int i = 0; i < nodes.size() - 1; i++) {
                        long fromId = nodes.get(i).getNodeId();
                        long toId = nodes.get(i + 1).getNodeId();

                        int fromIdx = osmIdToIndex.get(fromId);
                        int toIdx = osmIdToIndex.get(toId);
                        if (fromIdx == -1 || toIdx == -1) continue;

                        double fromLat = nodeLats.getDouble(fromIdx);
                        double fromLon = nodeLons.getDouble(fromIdx);
                        double toLat = nodeLats.getDouble(toIdx);
                        double toLon = nodeLons.getDouble(toIdx);

                        float dist = (float) distanceMeters(fromLat, fromLon, toLat, toLon);
                        // recalculated weight using updated speed
                        float timeSec = dist / baseSpeedMs;

                        boolean isOneWay = (forward ^ backward);

                        if (forward) {
                            int flags = baseFlags | EdgeFlags.DIR_FORWARD;
                            edgeFromList.add(fromIdx);
                            edgeToList.add(toIdx);
                            edgeWeightList.add(timeSec);
                            edgeDistanceList.add(dist);
                            edgeSpeedList.add(baseSpeedKmh);
                            edgeFlagsList.add(flags);
                            edgeHighwayTypeList.add(hwType);
                            edgeWayTypeList.add(highway);
                            edgeTagsList.add(allTags);
                            edgeOneWayList.add(isOneWay);
                        }
                        if (backward) {
                            int flags = baseFlags | EdgeFlags.DIR_BACKWARD;
                            edgeFromList.add(toIdx);
                            edgeToList.add(fromIdx);
                            edgeWeightList.add(timeSec);
                            edgeDistanceList.add(dist);
                            edgeSpeedList.add(baseSpeedKmh);
                            edgeFlagsList.add(flags);
                            edgeHighwayTypeList.add(hwType);
                            edgeWayTypeList.add(highway);
                            edgeTagsList.add(allTags);
                            edgeOneWayList.add(isOneWay);
                        }
                    }
                }
            }

            @Override public void initialize(Map<String, Object> metaData) {}
            @Override public void complete() {}
            @Override public void close() {}
        };

        RunnableSource reader2 = new PbfReader(new File(pbfPath), 1);
        reader2.setSink(sink);
        reader2.run();

        int nodeCount = nodeOsmIds.size();
        int edgeCount = edgeFromList.size();

        int[] lat = new int[nodeCount];
        int[] lon = new int[nodeCount];
        int[] firstEdge = new int[nodeCount];
        Arrays.fill(firstEdge, -1);

        int[] edgeTo = new int[edgeCount];
        int[] edgeNext = new int[edgeCount];
        float[] weight = new float[edgeCount];
        int[] flags = new int[edgeCount];
        byte[] highwayType = new byte[edgeCount];

        float[] distance = new float[edgeCount];
        float[] speed = new float[edgeCount];
        String[] wayType = new String[edgeCount];
        @SuppressWarnings("unchecked")
        Map<String,String>[] tags = (Map<String,String>[]) new Map[edgeCount];
        boolean[] oneWay = new boolean[edgeCount];

        for (int i = 0; i < nodeCount; i++) {
            lat[i] = (int) Math.round(nodeLats.getDouble(i) * 1e6);
            lon[i] = (int) Math.round(nodeLons.getDouble(i) * 1e6);
        }

        for (int i = 0; i < edgeCount; i++) {
            int from = edgeFromList.getInt(i);
            int to = edgeToList.getInt(i);

            edgeTo[i] = to;
            edgeNext[i] = firstEdge[from];
            firstEdge[from] = i;

            weight[i] = edgeWeightList.getFloat(i);
            flags[i] = edgeFlagsList.getInt(i);
            highwayType[i] = edgeHighwayTypeList.getByte(i);

            distance[i] = edgeDistanceList.getFloat(i);
            speed[i] = edgeSpeedList.getFloat(i);
            wayType[i] = edgeWayTypeList.get(i);
            tags[i] = edgeTagsList.get(i);
            oneWay[i] = edgeOneWayList.getBoolean(i);
        }

        log.info("Compressed graph built: {} street nodes, {} edges", nodeCount, edgeCount);

        return new CompressedGraph(
                lat, lon, firstEdge,
                edgeTo, edgeNext,
                weight, flags, highwayType,
                distance, speed, wayType, tags, oneWay
        );
    }
    }
