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
		String resolvedPath = resolvePbfPath(pbfPath, pathInfo);
		File tempFile = pathInfo[1] != null ? new File(pathInfo[1]) : null;
		
		try {
			this.graph = com.patriot.nav.routing.GraphPreprocessor.loadOrPreprocess(resolvedPath, chunksPath);
			log.info("Compressed graph loaded with {} nodes", graph.nodeCount());
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
