package com.patriot.nav.routing;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Slf4j
public class GraphPreprocessor {

    public static CompressedGraph loadOrPreprocess(String pbfPath, String configuredChunksPath) throws Exception {
        File pbfFile = new File(pbfPath);
        long pbfLast = pbfFile.exists() ? pbfFile.lastModified() : 0L;

        File chunksDir = resolveChunksDir(configuredChunksPath);
        if (!chunksDir.exists()) chunksDir.mkdirs();

        File meta = new File(chunksDir, "metadata.properties");
        File cache = new File(chunksDir, "graph.bin");

        if (cache.exists() && meta.exists()) {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(meta)) { props.load(in); }
            String val = props.getProperty("pbf.lastModified", "0");
            long prev = Long.parseLong(val);
            if (prev == pbfLast) {
                log.info("Graph cache up-to-date, loading from {}", cache.getAbsolutePath());
                return CompressedGraph.loadFromFile(cache);
            }
        }

        log.info("Preprocessing PBF: {}", pbfPath);

        // Try to create hierarchy tiles using a disk-backed streaming approach
        try {
            createTilesFromPbf(pbfPath, chunksDir, 4);
        } catch (Throwable t) {
            log.warn("Streaming tile creation failed (falling back to in-memory build): {}", t.getMessage());
        }

        log.info("Building compressed graph from PBF (may require more memory): {}", pbfPath);
        CompressedGraph g = CompressedGraphBuilder.buildFromPbf(pbfPath);

        // save full graph cache
        try {
            cache.getParentFile().mkdirs();
            g.saveToFile(cache);
            Properties p = new Properties();
            p.setProperty("pbf.lastModified", String.valueOf(pbfLast));
            try (FileOutputStream out = new FileOutputStream(meta)) { p.store(out, "Graph metadata"); }
            log.info("Graph cache saved to {}", cache.getAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not write graph cache: {}", e.getMessage());
        }

        // build a simple hierarchical tiling (2 levels -> 4x4 grid) and save node lists per tile
        try {
            saveHierarchyTiles(g, chunksDir, 4);
        } catch (Exception e) {
            log.warn("Failed to write hierarchy tiles: {}", e.getMessage());
        }

        return g;
    }

    /**
     * Create hierarchy tiles directly from the PBF using disk-backed maps to avoid keeping
     * all nodes/ids in memory. Produces the same files under <baseDir>/hierarchy as
     * `saveHierarchyTiles` would.
     */
    private static void createTilesFromPbf(String pbfPath, File baseDir, int gridSize) throws Exception {
        File dbFile = new File(baseDir, "tmp_nodes.db");
        dbFile.getParentFile().mkdirs();

        // If a previous tmp DB exists, check if it's stale (older than PBF) or corrupted.
        File pbfFile = new File(pbfPath);
        long pbfLast = pbfFile.exists() ? pbfFile.lastModified() : 0L;
        if (dbFile.exists()) {
            // stale check: tmp DB older than PBF -> remove and start fresh
            if (dbFile.lastModified() < pbfLast) {
                log.info("createTilesFromPbf: existing tmp DB is older than PBF — removing {}", dbFile.getAbsolutePath());
                File parent = dbFile.getParentFile();
                String prefix = dbFile.getName();
                File[] siblings = parent.listFiles((d, name) -> name.startsWith(prefix));
                if (siblings != null) for (File f : siblings) {
                    try { f.delete(); } catch (Exception ignored) {}
                }
            } else {
                // try opening once without bypass to detect corruption
                try {
                    DB probe = DBMaker.fileDB(dbFile).fileMmapEnableIfSupported().transactionEnable().closeOnJvmShutdown().make();
                    probe.close();
                    log.info("createTilesFromPbf: existing tmp DB looks consistent: {}", dbFile.getAbsolutePath());
                } catch (Throwable t) {
                    log.warn("createTilesFromPbf: tmp DB appears corrupted ({}). Deleting files.", t.getMessage());
                    File parent = dbFile.getParentFile();
                    String prefix = dbFile.getName();
                    File[] siblings = parent.listFiles((d, name) -> name.startsWith(prefix));
                    if (siblings != null) for (File f : siblings) {
                        try { f.delete(); } catch (Exception ignored) {}
                    }
                }
            }
        }

        // Open DB with transactions enabled and close-on-shutdown to ensure cleaner state.
        DB db = DBMaker.fileDB(dbFile).fileMmapEnableIfSupported().transactionEnable().closeOnJvmShutdown().make();
        log.info("createTilesFromPbf: opening MapDB file={}", dbFile.getAbsolutePath());
        HTreeMap<Long, Boolean> usedNodes = db.hashMap("usedNodes", Serializer.LONG, Serializer.BOOLEAN).createOrOpen();
        HTreeMap<Long, String> nodeData = db.hashMap("nodeData", Serializer.LONG, Serializer.STRING).createOrOpen();

        // PASS 1: scan ways and mark used node ids (ways with highway tag)
        RunnableSource wayReader = new PbfReader(new File(pbfPath), 1);
        wayReader.setSink(new Sink() {
            @Override public void process(EntityContainer container) {
                var e = container.getEntity();
                if (e instanceof Way way) {
                    for (var t : way.getTags()) {
                        if ("highway".equals(t.getKey())) {
                            way.getWayNodes().forEach(wn -> usedNodes.put(wn.getNodeId(), Boolean.TRUE));
                            break;
                        }
                    }
                }
            }
            @Override public void initialize(Map<String, Object> meta) {}
            @Override public void complete() {}
            @Override public void close() {}
        });
        wayReader.run();
        db.commit();
        log.info("createTilesFromPbf: PASS1 complete - marked {} used nodes (on-disk) and committed", usedNodes.size());

        // PASS 2: scan nodes and store coordinates only for used nodes
        RunnableSource nodeReader = new PbfReader(new File(pbfPath), 1);
        final double[] minMax = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        nodeReader.setSink(new Sink() {
            @Override public void process(EntityContainer container) {
                var e = container.getEntity();
                if (e instanceof Node node) {
                    if (usedNodes.containsKey(node.getId())) {
                        nodeData.put(node.getId(), node.getLatitude() + "," + node.getLongitude());
                        if (node.getLatitude() < minMax[0]) minMax[0] = node.getLatitude();
                        if (node.getLatitude() > minMax[1]) minMax[1] = node.getLatitude();
                        if (node.getLongitude() < minMax[2]) minMax[2] = node.getLongitude();
                        if (node.getLongitude() > minMax[3]) minMax[3] = node.getLongitude();
                    }
                }
            }
            @Override public void initialize(Map<String, Object> meta) {}
            @Override public void complete() {}
            @Override public void close() {}
        });
        nodeReader.run();
        db.commit();
        log.info("createTilesFromPbf: PASS2 complete - stored {} node coords (on-disk) and committed", nodeData.size());
        log.info("createTilesFromPbf: bounds minLat={}, maxLat={}, minLon={}, maxLon={}", minMax[0], minMax[1], minMax[2], minMax[3]);

        if (nodeData.size() == 0) {
            db.close();
            return;
        }

        double minLat = minMax[0], maxLat = minMax[1], minLon = minMax[2], maxLon = minMax[3];

        File hier = new File(baseDir, "hierarchy");
        if (!hier.exists()) hier.mkdirs();

        double latStep = (maxLat - minLat) / gridSize;
        double lonStep = (maxLon - minLon) / gridSize;

        // Prepare per-tile collections on disk (temporary in-memory sets of global ids per tile)
        List<Set<Integer>> tileNodes = new ArrayList<>(gridSize * gridSize);
        List<Set<Integer>> tileGhosts = new ArrayList<>(gridSize * gridSize);
        List<DataOutputStream> tileEdgeStreams = new ArrayList<>();
        for (int i = 0; i < gridSize * gridSize; i++) {
            tileNodes.add(new HashSet<>());
            tileGhosts.add(new HashSet<>());
            File ef = new File(hier, String.format("tile_%d_%d.edges", i / gridSize, i % gridSize));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(ef)));
            tileEdgeStreams.add(dos);
            // we'll write count later; use placeholder
        }

        // We need a global numbering for node indices; assign sequential IDs for nodes encountered
        // Create an on-disk map nodeId -> globalIndex
        HTreeMap<Long, Integer> globalIndex = db.hashMap("globalIndex", Serializer.LONG, Serializer.INTEGER).createOrOpen();
        java.util.concurrent.atomic.AtomicInteger nextGlobal = new java.util.concurrent.atomic.AtomicInteger(0);

        // PASS 3: scan ways again, create edges and assign nodes to tiles
        // Parallelize processing of ways: submit tasks to thread pool to process individual ways
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(threads);
        RunnableSource wayReader2 = new PbfReader(new File(pbfPath), 1);
        wayReader2.setSink(new Sink() {
            @Override public void process(EntityContainer container) {
                var e = container.getEntity();
                if (!(e instanceof Way way)) return;
                // check highway tag quickly
                boolean isHighway = false;
                for (var t : way.getTags()) if ("highway".equals(t.getKey())) { isHighway = true; break; }
                if (!isHighway) return;

                // submit processing of this way to thread pool
                exec.submit(() -> {
                    try {
                        var nodes = way.getWayNodes();
                        for (int i = 0; i < nodes.size() - 1; i++) {
                            long fromId = nodes.get(i).getNodeId();
                            long toId = nodes.get(i + 1).getNodeId();
                            String fromCoord = nodeData.get(fromId);
                            String toCoord = nodeData.get(toId);
                            if (fromCoord == null || toCoord == null) continue;
                            String[] fa = fromCoord.split(",");
                            String[] ta = toCoord.split(",");
                            double flat = Double.parseDouble(fa[0]);
                            double flon = Double.parseDouble(fa[1]);
                            double tlat = Double.parseDouble(ta[0]);
                            double tlon = Double.parseDouble(ta[1]);

                            int fromTileR = (int) Math.floor((flat - minLat) / latStep);
                            int fromTileC = (int) Math.floor((flon - minLon) / lonStep);
                            int toTileR = (int) Math.floor((tlat - minLat) / latStep);
                            int toTileC = (int) Math.floor((tlon - minLon) / lonStep);
                            fromTileR = Math.max(0, Math.min(gridSize - 1, fromTileR));
                            fromTileC = Math.max(0, Math.min(gridSize - 1, fromTileC));
                            toTileR = Math.max(0, Math.min(gridSize - 1, toTileR));
                            toTileC = Math.max(0, Math.min(gridSize - 1, toTileC));
                            int fromTileIdx = fromTileR * gridSize + fromTileC;
                            int toTileIdx = toTileR * gridSize + toTileC;

                            // assign global indices atomically using computeIfAbsent
                            Integer fg = globalIndex.computeIfAbsent(fromId, k -> nextGlobal.getAndIncrement());
                            Integer tg = globalIndex.computeIfAbsent(toId, k -> nextGlobal.getAndIncrement());

                            // add nodes to respective tile node sets
                            tileNodes.get(fromTileIdx).add(fg);
                            if (fromTileIdx != toTileIdx) tileGhosts.get(fromTileIdx).add(tg);

                            // write edge: synchronized on per-stream object to allow concurrent writes
                            DataOutputStream dos = tileEdgeStreams.get(fromTileIdx);
                            synchronized (dos) {
                                dos.writeInt(fg);
                                dos.writeInt(tg);
                                dos.writeFloat(0f);
                                dos.writeInt(0);
                            }
                        }
                    } catch (RuntimeException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            @Override public void initialize(Map<String, Object> meta) {}
            @Override public void complete() {}
            @Override public void close() {}
        });
        wayReader2.run();

        // shutdown executor and wait for tasks to finish
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, java.util.concurrent.TimeUnit.MINUTES)) {
                exec.shutdownNow();
                log.warn("createTilesFromPbf: Executor did not finish within timeout; forced shutdown executed");
            }
        } catch (InterruptedException ie) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
        db.commit();
        log.info("createTilesFromPbf: PASS3 complete - created {} global index entries and committed", globalIndex.size());

        // close edge streams
        for (DataOutputStream dos : tileEdgeStreams) dos.close();

        // write out per-tile files
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int idx = r * gridSize + c;
                String prefix = String.format("tile_%d_%d", r, c);
                File nodesFile = new File(hier, prefix + ".nodes");
                Set<Integer> nodesSet = tileNodes.get(idx);
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(nodesFile)))) {
                    out.writeInt(nodesSet.size());
                    for (int gid : nodesSet) {
                        // find original osm id by scanning globalIndex (inverse lookup)
                        // MapDB doesn't provide inverse; do a linear scan of keys in globalIndex (on-disk) - acceptable for moderate counts
                        long osmId = -1L;
                        for (Map.Entry<Long, Integer> e : globalIndex.entrySet()) if (e.getValue() == gid) { osmId = e.getKey(); break; }
                        String coord = nodeData.get(osmId);
                        String[] a = coord.split(",");
                        double lat = Double.parseDouble(a[0]);
                        double lon = Double.parseDouble(a[1]);
                        out.writeInt(gid);
                        out.writeDouble(lat);
                        out.writeDouble(lon);
                    }
                }

                File ghostFile = new File(hier, prefix + ".ghost");
                Set<Integer> ghosts = tileGhosts.get(idx);
                if (!ghosts.isEmpty()) {
                    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(ghostFile)))) {
                        out.writeInt(ghosts.size());
                        for (int gid : ghosts) {
                            long osmId = -1L;
                            for (Map.Entry<Long, Integer> e : globalIndex.entrySet()) if (e.getValue() == gid) { osmId = e.getKey(); break; }
                            String coord = nodeData.get(osmId);
                            String[] a = coord.split(",");
                            double lat = Double.parseDouble(a[0]);
                            double lon = Double.parseDouble(a[1]);
                            out.writeInt(gid);
                            out.writeDouble(lat);
                            out.writeDouble(lon);
                        }
                    }
                }

                // rewrite .edges with counts at front
                File edgesFile = new File(hier, prefix + ".edges");
                // read content and count entries (each entry is 4+4+4+4 = 16 bytes)
                long entries = edgesFile.exists() ? edgesFile.length() / 16 : 0;
                log.info("createTilesFromPbf: tile {}_{}, nodes={}, ghosts={}, rawEdgeEntries={}", r, c, nodesSet.size(), ghosts.size(), entries);
                if (entries > 0) {
                    File tmp = new File(hier, prefix + ".edges.tmp");
                    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(edgesFile)));
                         DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                        out.writeInt((int)entries);
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = in.read(buf)) > 0) out.write(buf, 0, read);
                    }
                    // replace
                    if (!edgesFile.delete()) throw new IOException("Could not replace edges file");
                    if (!tmp.renameTo(edgesFile)) throw new IOException("Could not rename temp edges file");
                } else {
                    // write empty edges file
                    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(edgesFile)))) {
                        out.writeInt(0);
                    }
                }
            }
        }

        // write hierarchy metadata
        File meta = new File(baseDir, "hierarchy.properties");
        try (FileOutputStream fos = new FileOutputStream(meta)) {
            Properties p = new Properties();
            p.setProperty("minLat", String.valueOf(minLat));
            p.setProperty("maxLat", String.valueOf(maxLat));
            p.setProperty("minLon", String.valueOf(minLon));
            p.setProperty("maxLon", String.valueOf(maxLon));
            p.setProperty("gridSize", String.valueOf(gridSize));
            p.store(fos, "Hierarchy metadata (streamed)");
        }

        // final commit & close
        try { db.commit(); } catch (Exception ignored) {}
        db.close();
    }

    private static File resolveChunksDir(String configured) {
        if (configured != null && !configured.isBlank()) return new File(configured);
        try {
            File code = new File(CompressedGraphBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = code.isDirectory() ? code : code.getParentFile();
            return new File(dir, "graph-chunks");
        } catch (Exception e) {
            return new File("graph-chunks");
        }
    }

    private static void saveHierarchyTiles(CompressedGraph g, File baseDir, int gridSize) throws IOException {
        int n = g.nodeCount();
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double la = g.lat(i); double lo = g.lon(i);
            if (la < minLat) minLat = la;
            if (la > maxLat) maxLat = la;
            if (lo < minLon) minLon = lo;
            if (lo > maxLon) maxLon = lo;
        }

        File hier = new File(baseDir, "hierarchy");
        if (!hier.exists()) hier.mkdirs();

        double latStep = (maxLat - minLat) / gridSize;
        double lonStep = (maxLon - minLon) / gridSize;

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                double la0 = minLat + r * latStep;
                double la1 = (r == gridSize - 1) ? maxLat + 1e-9 : minLat + (r + 1) * latStep;
                double lo0 = minLon + c * lonStep;
                double lo1 = (c == gridSize - 1) ? maxLon + 1e-9 : minLon + (c + 1) * lonStep;

                List<Integer> nodes = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    double la = g.lat(i); double lo = g.lon(i);
                    if (la >= la0 && la < la1 && lo >= lo0 && lo < lo1) nodes.add(i);
                }
                // build a fast lookup set for membership tests to avoid O(n) list.contains calls
                IntOpenHashSet nodeSet = new IntOpenHashSet(nodes.size());
                for (int id : nodes) nodeSet.add(id);

                // write node index file
                File tileIdx = new File(hier, String.format("tile_%d_%d.idx", r, c));
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tileIdx)))) {
                    out.writeInt(nodes.size());
                    for (int id : nodes) out.writeInt(id);
                }

                // write per-tile node positions
                File tileNodes = new File(hier, String.format("tile_%d_%d.nodes", r, c));
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tileNodes)))) {
                    out.writeInt(nodes.size());
                    for (int id : nodes) {
                        out.writeInt(id);
                        out.writeDouble(g.lat(id));
                        out.writeDouble(g.lon(id));
                    }
                }

                // collect ghost nodes (targets outside this tile) with positions
                java.util.Set<Integer> ghostIds = new java.util.HashSet<>();
                for (int nid : nodes) {
                    int e = g.firstEdge(nid);
                    while (e != -1) {
                        int to = g.edgeTo(e);
                        if (!nodeSet.contains(to)) ghostIds.add(to);
                        e = g.edgeNext(e);
                    }
                }

                if (!ghostIds.isEmpty()) {
                    File tileGhosts = new File(hier, String.format("tile_%d_%d.ghost", r, c));
                    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tileGhosts)))) {
                        out.writeInt(ghostIds.size());
                        for (int gid : ghostIds) {
                            out.writeInt(gid);
                            out.writeDouble(g.lat(gid));
                            out.writeDouble(g.lon(gid));
                        }
                    }
                }

                // write per-tile edges (edges whose source node is in this tile)
                File tileEdges = new File(hier, String.format("tile_%d_%d.edges", r, c));
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tileEdges)))) {
                    List<Integer> outEdges = new ArrayList<>();
                    List<Integer> fromNodes = new ArrayList<>();
                    for (int nid : nodes) {
                        int e = g.firstEdge(nid);
                        while (e != -1) {
                            outEdges.add(e);
                            fromNodes.add(nid);
                            e = g.edgeNext(e);
                        }
                    }
                    out.writeInt(outEdges.size());
                    for (int i = 0; i < outEdges.size(); i++) {
                        int edgeId = outEdges.get(i);
                        int fromNode = fromNodes.get(i);
                        out.writeInt(fromNode);
                        out.writeInt(g.edgeTo(edgeId));
                        out.writeFloat(g.weight(edgeId));
                        out.writeInt(g.flags(edgeId));
                    }
                }
            }
        }

        // write hierarchy metadata (bounds + grid)
        File meta = new File(baseDir, "hierarchy.properties");
        try (FileOutputStream fos = new FileOutputStream(meta)) {
            Properties p = new Properties();
            p.setProperty("minLat", String.valueOf(minLat));
            p.setProperty("maxLat", String.valueOf(maxLat));
            p.setProperty("minLon", String.valueOf(minLon));
            p.setProperty("maxLon", String.valueOf(maxLon));
            p.setProperty("gridSize", String.valueOf(gridSize));
            p.store(fos, "Hierarchy metadata");
        }
    }
}
