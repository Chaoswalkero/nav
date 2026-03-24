package com.patriot.nav.service;

import com.patriot.nav.routing.CompressedGraph;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@Service
public class OSMGraphService {

	@Value("${osm.pbf.path}")
	private String pbfPath;

	@Value("${graph.chunks.path:}")
	private String chunksPath;

	private CompressedGraph graph;

	@PostConstruct
	public void initialize() throws Exception {
		log.info("Initializing OSM Graph Service");
		String[] pathInfo = new String[2];
		resolvePbfPath(pbfPath, pathInfo);
		File tempFile = pathInfo[1] != null ? new File(pathInfo[1]) : null;
		try {
			File chunksDir = resolveChunksDir(chunksPath);
			File cache = new File(chunksDir, "graph.bin");
			if (cache.exists()) {
				log.info("Loading compressed graph from {}", cache.getAbsolutePath());
				this.graph = com.patriot.nav.routing.CompressedGraph.loadFromFile(cache);
				log.info("Compressed graph loaded with {} nodes", graph.nodeCount());
			} else {
				// Fallback: if no prebuilt cache exists but a PBF path was resolved (e.g. tests using classpath resource),
				// build graph from PBF on-the-fly (do not persist cache here).
				String pbfResolved = pathInfo[0];
				if (pbfResolved != null) {
					File pbfFile = new File(pbfResolved);
					if (pbfFile.exists()) {
						log.info("No graph cache found; building graph from PBF: {}", pbfResolved);
						this.graph = com.patriot.nav.routing.CompressedGraphBuilder.buildFromPbf(pbfResolved);
						log.info("Compressed graph built with {} nodes", graph.nodeCount());
					} else {
						throw new IllegalStateException("Graph cache not found at: " + cache.getAbsolutePath()
								+ ". No PBF available at: " + pbfResolved + ". Provide a prebuilt graph.bin in the graph-chunks directory.");
					}
				} else {
					throw new IllegalStateException("Graph cache not found at: " + cache.getAbsolutePath()
							+ ". GraphPreprocessor class has been removed; provide a prebuilt graph.bin in the graph-chunks directory.");
				}
			}
		} finally {
			// Cleanup temporäre OSM-Dateien nach dem Graphbau
			if (tempFile != null && tempFile.exists()) {
				try {
					if (tempFile.delete()) {
						log.info("Temporäre OSM-Datei gelöscht: {}", tempFile.getAbsolutePath());
					} else {
						log.warn("Konnte temporäre OSM-Datei nicht löschen: {}", tempFile.getAbsolutePath());
						tempFile.deleteOnExit();
					}
				} catch (Exception e) {
					log.warn("Fehler beim Löschen der temporären OSM-Datei: {}", e.getMessage());
					tempFile.deleteOnExit();
				}
			}
		}
	}

	private File resolveChunksDir(String configured) {
		if (configured != null && !configured.isBlank()) return new File(configured);
		try {
			File code = new File(com.patriot.nav.routing.CompressedGraphBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			File dir = code.isDirectory() ? code : code.getParentFile();
			return new File(dir, "graph-chunks");
		} catch (Exception e) {
			return new File("graph-chunks");
		}
	}

	private String resolvePbfPath(String path, String[] pathInfo) throws IOException {
		if (!path.startsWith("classpath:")) {
			pathInfo[0] = path;
			pathInfo[1] = null; // kein temp file
			return path;
		}
		String resourcePath = path.substring("classpath:".length());
		Resource resource = new ClassPathResource(resourcePath);
		File temp = File.createTempFile("osm", ".pbf");
		try (InputStream in = resource.getInputStream(); OutputStream out = new FileOutputStream(temp)) {
			in.transferTo(out);
		}
		pathInfo[0] = temp.getAbsolutePath();
		pathInfo[1] = temp.getAbsolutePath(); // markiere als temp zur Löschung
		return temp.getAbsolutePath();
	}

	public CompressedGraph getGraph() {
		return graph;
	}
}
