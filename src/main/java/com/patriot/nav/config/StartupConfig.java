package com.patriot.nav.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Initialisierung beim Start
 */
@Slf4j
@Configuration
public class StartupConfig {

    @Value("${graph.chunks.path:}")
    private String chunksPath;

    @Value("${osm.cache.dir:target/graphhopper-cache}")
    private String osmCacheDir;

    @Value("${osm.vehicles.dir:classpath:osm/vehicles}")
    private String osmVehiclesDir;

    @PostConstruct
    public void initialize() {
        try {
            // ensure osm cache dir exists
            File cacheDir = new File(osmCacheDir);
            if (!cacheDir.exists()) {
                Files.createDirectories(cacheDir.toPath());
                log.info("Created OSM cache directory: {}", cacheDir.getAbsolutePath());
            }
            // If a chunks path is configured, prepare runtime dirs and copy vehicle resources there.
            if (chunksPath != null && !chunksPath.isBlank()) {
                File chunksDir = resolveChunksDir(chunksPath);
                if (!chunksDir.exists()) {
                    Files.createDirectories(chunksDir.toPath());
                    log.info("Created chunks directory: {}", chunksDir.getAbsolutePath());
                }

                // prepare runtime vehicles directory under chunksDir so API can write there
                File vehiclesRuntimeDir = new File(chunksDir, "vehicles");
                if (!vehiclesRuntimeDir.exists()) {
                    Files.createDirectories(vehiclesRuntimeDir.toPath());
                    log.info("Created runtime vehicles directory: {}", vehiclesRuntimeDir.getAbsolutePath());
                }

                // dynamically list vehicle resource files from classpath and copy them
                List<String> vehicleResources = listClasspathResourceFiles("osm/vehicles");
                for (String name : vehicleResources) {
                    File dest = new File(vehiclesRuntimeDir, name);
                    if (dest.exists()) continue; // don't overwrite
                    try {
                        Resource r = new ClassPathResource("osm/vehicles/" + name);
                        if (r.exists()) {
                            try (InputStream in = r.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
                                in.transferTo(out);
                            }
                            log.info("Copied default vehicle {} to {}", name, dest.getAbsolutePath());
                        }
                    } catch (Exception ex) {
                        log.warn("Could not copy default vehicle {}: {}", name, ex.getMessage());
                    }
                }

                // create additional runtime dirs under chunksDir
                String[] extraDirs = new String[]{"hierarchy","tiles","cache","tmp","logs"};
                for (String d : extraDirs) {
                    File f = new File(chunksDir, d);
                    if (!f.exists()) {
                        Files.createDirectories(f.toPath());
                        log.info("Created directory: {}", f.getAbsolutePath());
                    }
                }
            } else {
                log.info("Chunking disabled (graph.chunks.path not set); skipping chunks directory setup");
            }

            log.info("Application startup configuration completed");
        } catch (Exception e) {
            log.warn("Startup initialization failed: {}", e.getMessage());
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

    private List<String> listClasspathResourceFiles(String path) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            java.util.Enumeration<java.net.URL> resources = cl.getResources(path);
            java.util.Set<String> names = new java.util.HashSet<>();
            while (resources.hasMoreElements()) {
                java.net.URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    File dir = new File(url.toURI());
                    File[] files = dir.listFiles();
                    if (files != null) for (File f : files) if (f.isFile()) names.add(f.getName());
                } else if ("jar".equals(protocol)) {
                    String spec = url.getPath();
                    String jarPath = spec.substring(5, spec.indexOf('!'));
                    try (java.util.jar.JarFile jf = new java.util.jar.JarFile(java.nio.file.Paths.get(java.net.URI.create("file:" + jarPath)).toFile())) {
                        var entries = jf.entries();
                        String prefix = path.endsWith("/") ? path : path + "/";
                        while (entries.hasMoreElements()) {
                            var e = entries.nextElement();
                            String name = e.getName();
                            if (name.startsWith(prefix) && !name.equals(prefix)) {
                                String rel = name.substring(prefix.length());
                                if (!rel.isEmpty() && !rel.endsWith("/")) names.add(rel);
                            }
                        }
                    }
                }
            }
            return new java.util.ArrayList<>(names);
        } catch (Exception e) {
            log.warn("Could not list classpath resources for {}: {}", path, e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
