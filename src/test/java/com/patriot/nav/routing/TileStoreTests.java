package com.patriot.nav.routing;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TileStoreTests {

    @Test
    void testAssembleAdjacentTilesWithGhosts() throws Exception {
        File tmp = Files.createTempDirectory("tilestoretest").toFile();
        try {
            // metadata
            File meta = new File(tmp, "metadata.properties");
            try (var out = new java.io.FileOutputStream(meta)) {
                var p = new java.util.Properties();
                p.setProperty("minLat", "0");
                p.setProperty("maxLat", "2");
                p.setProperty("minLon", "0");
                p.setProperty("maxLon", "2");
                p.setProperty("gridSize", "2");
                p.store(out, "test");
            }

            File hier = new File(tmp, "hierarchy");
            hier.mkdirs();

            // tile_0_0: nodes 1,2 and ghost 3
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(hier, "tile_0_0.nodes"))))) {
                out.writeInt(2);
                out.writeInt(1); out.writeDouble(0.1); out.writeDouble(0.1);
                out.writeInt(2); out.writeDouble(0.15); out.writeDouble(0.15);
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(hier, "tile_0_0.ghost"))))) {
                out.writeInt(1);
                out.writeInt(3); out.writeDouble(0.2); out.writeDouble(1.1);
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(hier, "tile_0_0.edges"))))) {
                out.writeInt(1);
                out.writeInt(1); out.writeInt(3); out.writeFloat(1.0f); out.writeInt(0);
            }

            // tile_0_1: nodes 3,4 and edge 3->4
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(hier, "tile_0_1.nodes"))))) {
                out.writeInt(2);
                out.writeInt(3); out.writeDouble(0.2); out.writeDouble(1.1);
                out.writeInt(4); out.writeDouble(0.25); out.writeDouble(1.15);
            }
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(hier, "tile_0_1.edges"))))) {
                out.writeInt(1);
                out.writeInt(3); out.writeInt(4); out.writeFloat(1.0f); out.writeInt(0);
            }

            TileStore store = new TileStore(tmp);
            CompressedGraph g = store.assembleGraphForTiles(Set.of("tile_0_0", "tile_0_1"));

            // expect 4 global nodes assembled
            assertEquals(4, g.nodeCount());

            // find an edge that goes from node at (0.1,0.1) to node at (0.2,1.1)
            boolean found = false;
            for (int n = 0; n < g.nodeCount(); n++) {
                double lat = g.lat(n);
                double lon = g.lon(n);
                if (Math.abs(lat - 0.1) < 1e-6 && Math.abs(lon - 0.1) < 1e-6) {
                    int e = g.firstEdge(n);
                    while (e != -1) {
                        int to = g.edgeTo(e);
                        if (Math.abs(g.lat(to) - 0.2) < 1e-6 && Math.abs(g.lon(to) - 1.1) < 1e-6) { found = true; break; }
                        e = g.edgeNext(e);
                    }
                }
            }
            assertTrue(found, "Cross-tile edge should be present in assembled graph");

        } finally {
            // best-effort cleanup
            try { java.nio.file.Files.walk(tmp.toPath()).sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete); } catch (Exception ignored) {}
        }
    }
}
