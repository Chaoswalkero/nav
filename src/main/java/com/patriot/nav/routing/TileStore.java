package com.patriot.nav.routing;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

@Slf4j
public class TileStore {

    private final File baseDir;
    private final double minLat, maxLat, minLon, maxLon;
    private final int gridSize;

    public TileStore(File baseDir) throws IOException {
        this.baseDir = baseDir;
        File meta = new File(baseDir, "metadata.properties");
        Properties p = new Properties();
        boolean loaded = false;
        if (meta.exists()) {
            try (FileInputStream in = new FileInputStream(meta)) { p.load(in); loaded = true; }
            catch (IOException ignored) {}
        }

        // GraphPreprocessor now may write hierarchy metadata to 'hierarchy.properties'
        if (!loaded || p.getProperty("minLat") == null) {
            File hierMeta = new File(baseDir, "hierarchy.properties");
            if (hierMeta.exists()) {
                try (FileInputStream in = new FileInputStream(hierMeta)) { p.load(in); loaded = true; }
                catch (IOException ignored) {}
            }
        }

        if (!loaded || p.getProperty("minLat") == null)
            throw new IOException("Tile metadata not found or incomplete in: " + baseDir.getAbsolutePath());

        minLat = Double.parseDouble(p.getProperty("minLat"));
        maxLat = Double.parseDouble(p.getProperty("maxLat"));
        minLon = Double.parseDouble(p.getProperty("minLon"));
        maxLon = Double.parseDouble(p.getProperty("maxLon"));
        gridSize = Integer.parseInt(p.getProperty("gridSize"));
    }

    public int getGridSize() { return gridSize; }

    public int[] tileFor(double lat, double lon) {
        int r = (int) Math.floor((lat - minLat) / ((maxLat - minLat) / gridSize));
        int c = (int) Math.floor((lon - minLon) / ((maxLon - minLon) / gridSize));
        r = Math.max(0, Math.min(gridSize - 1, r));
        c = Math.max(0, Math.min(gridSize - 1, c));
        return new int[]{r, c};
    }

    public CompressedGraph assembleGraphForTiles(Set<String> tiles) throws IOException {
        return assembleGraphForTiles(tiles, null);
    }

    public CompressedGraph assembleGraphForTiles(Set<String> tiles, CompressedGraph fullGraph) throws IOException {
        // mapping from global node id to local index
        IntMap globalToLocal = new IntMap();
        List<Integer> globalIds = new ArrayList<>();

        List<int[]> edges = new ArrayList<>(); // fromLocal,toLocal,edgeId
        List<Float> weights = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        List<Byte> highwayType = new ArrayList<>();
        List<Float> distance = new ArrayList<>();
        List<Float> speed = new ArrayList<>();
        List<String> wayType = new ArrayList<>();
        List<Map<String,String>> tags = new ArrayList<>();
        List<Boolean> oneWay = new ArrayList<>();

        // first pass: collect nodes (including ghost nodes)
        File hier = new File(baseDir, "hierarchy");
        for (String t : tiles) {
            File nodesFile = new File(hier, t + ".nodes");
            if (!nodesFile.exists()) continue;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(nodesFile)))) {
                int cnt = in.readInt();
                for (int i = 0; i < cnt; i++) {
                    int gid = in.readInt();
                    // skip lat/lon
                    in.readDouble();
                    in.readDouble();
                    if (!globalToLocal.contains(gid)) {
                        globalToLocal.put(gid, globalIds.size());
                        globalIds.add(gid);
                    }
                }
            }
            // read ghosts for this tile (optional)
            File ghostFile = new File(hier, t + ".ghost");
            if (ghostFile.exists()) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(ghostFile)))) {
                    int gc = in.readInt();
                    for (int i = 0; i < gc; i++) {
                        int gid = in.readInt();
                        // read lat/lon
                        in.readDouble();
                        in.readDouble();
                        if (!globalToLocal.contains(gid)) {
                            globalToLocal.put(gid, globalIds.size());
                            globalIds.add(gid);
                        }
                    }
                }
            }
        }

        int nodeCount = globalIds.size();
        int[] latArr = new int[nodeCount];
        int[] lonArr = new int[nodeCount];
        Arrays.fill(latArr, 0);
        Arrays.fill(lonArr, 0);

        // fill positions
        for (String t : tiles) {
            File nodesFile = new File(hier, t + ".nodes");
            if (!nodesFile.exists()) continue;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(nodesFile)))) {
                int cnt = in.readInt();
                for (int i = 0; i < cnt; i++) {
                    int gid = in.readInt();
                    double lat = in.readDouble();
                    double lon = in.readDouble();
                    if (globalToLocal.contains(gid)) {
                        int local = globalToLocal.get(gid);
                        latArr[local] = (int) Math.round(lat * 1e6);
                        lonArr[local] = (int) Math.round(lon * 1e6);
                    }
                }
            }
            // ghosts
            File ghostFile = new File(hier, t + ".ghost");
            if (ghostFile.exists()) {
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(ghostFile)))) {
                    int gc = in.readInt();
                    for (int i = 0; i < gc; i++) {
                        int gid = in.readInt();
                        double lat = in.readDouble();
                        double lon = in.readDouble();
                        if (globalToLocal.contains(gid)) {
                            int local = globalToLocal.get(gid);
                            latArr[local] = (int) Math.round(lat * 1e6);
                            lonArr[local] = (int) Math.round(lon * 1e6);
                        }
                    }
                }
            }
        }

        // second pass: collect edges
        for (String t : tiles) {
            File edgesFile = new File(hier, "" + t + ".edges");
            if (!edgesFile.exists()) continue;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(edgesFile)))) {
                    int cnt = in.readInt();
                    for (int i = 0; i < cnt; i++) {
                        int fromGlobal = in.readInt();
                        int toGlobal = in.readInt();
                        float w = in.readFloat();
                        int f = in.readInt();
                        if (!globalToLocal.contains(fromGlobal) || !globalToLocal.contains(toGlobal)) continue;
                        int fromLocal = globalToLocal.get(fromGlobal);
                        int toLocal = globalToLocal.get(toGlobal);
                        edges.add(new int[]{fromLocal, toLocal});
                        weights.add(w);
                        flags.add(f);
                        // try to copy metadata from full graph if available
                        if (fullGraph != null) {
                            // find matching edge in full graph
                            int matched = -1;
                            int e = fullGraph.firstEdge(fromGlobal);
                            while (e != -1) {
                                if (fullGraph.edgeTo(e) == toGlobal) {
                                    // weight match is a helpful heuristic
                                    if (Math.abs(fullGraph.weight(e) - w) < 1e-3f) { matched = e; break; }
                                    if (matched == -1) matched = e; // remember first candidate
                                }
                                e = fullGraph.edgeNext(e);
                            }
                            if (matched != -1) {
                                highwayType.add(fullGraph.highwayType(matched));
                                distance.add(fullGraph.distance(matched));
                                speed.add(fullGraph.speed(matched));
                                String wt = fullGraph.wayType(matched);
                                wayType.add(wt == null ? "" : wt);
                                tags.add(fullGraph.tags(matched));
                                oneWay.add(fullGraph.oneWay(matched));
                            } else {
                                highwayType.add((byte)0);
                                distance.add(0f);
                                speed.add(0f);
                                wayType.add("");
                                tags.add(null);
                                oneWay.add(false);
                            }
                        } else {
                            highwayType.add((byte)0);
                            distance.add(0f);
                            speed.add(0f);
                            wayType.add("");
                            tags.add(null);
                            oneWay.add(false);
                        }
                    }
            }
        }

        int edgeCount = edges.size();
        int[] firstEdge = new int[nodeCount];
        Arrays.fill(firstEdge, -1);
        int[] edgeTo = new int[edgeCount];
        int[] edgeNext = new int[edgeCount];
        float[] weight = new float[edgeCount];
        int[] fflags = new int[edgeCount];
        byte[] hw = new byte[edgeCount];
        float[] dist = new float[edgeCount];
        float[] sp = new float[edgeCount];
        String[] wt = new String[edgeCount];
        @SuppressWarnings("unchecked")
        Map<String,String>[] ttags = (Map<String,String>[]) new Map[edgeCount];
        boolean[] ow = new boolean[edgeCount];

        for (int i = 0; i < edgeCount; i++) {
            int from = edges.get(i)[0];
            int to = edges.get(i)[1];
            edgeTo[i] = to == -1 ? from : to; // if missing, self-loop placeholder
            edgeNext[i] = firstEdge[from];
            firstEdge[from] = i;
            weight[i] = weights.get(i);
            fflags[i] = flags.get(i);
            hw[i] = highwayType.get(i);
            dist[i] = distance.get(i);
            sp[i] = speed.get(i);
            wt[i] = wayType.get(i);
            ttags[i] = tags.get(i);
            ow[i] = oneWay.get(i);
        }

        return new CompressedGraph(latArr, lonArr, firstEdge, edgeTo, edgeNext, weight, fflags, hw, dist, sp, wt, ttags, ow);
    }

    // tiny int->int map backed by HashMap to avoid external deps
    private static class IntMap {
        private final Map<Integer,Integer> m = new HashMap<>();
        boolean contains(int k) { return m.containsKey(k); }
        void put(int k, int v) { m.put(k, v); }
        int get(int k) { return m.getOrDefault(k, -1); }
    }
}
