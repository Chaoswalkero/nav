package com.patriot.nav.routing;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

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

        log.info("Preprocessing PBF and building graph: {}", pbfPath);
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
