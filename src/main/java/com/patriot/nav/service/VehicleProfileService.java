package com.patriot.nav.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patriot.nav.model.VehicleProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
public class VehicleProfileService {

    @Value("${osm.vehicles.dir:vehicles}")
    private String vehicleDir;

    private final ObjectMapper objectMapper;
    private final Map<String, VehicleProfile> profileCache = new HashMap<>();

    public VehicleProfileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VehicleProfile getProfile(String name) {
        if (profileCache.containsKey(name)) {
            return profileCache.get(name);
        }

        try {
            if (vehicleDir.startsWith("classpath:")) {
                String path = vehicleDir.replace("classpath:", "") + "/" + name + ".json";

                try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                    if (is == null) {
                        log.warn("Profile {} not found in classpath, falling back to car", name);
                        VehicleProfile fallback = getProfile("car");
                        fallback.setName("car");
                        return fallback;
                    }

                    VehicleProfile profile = objectMapper.readValue(is, VehicleProfile.class);
                    profileCache.put(name, profile);
                    return profile;
                }
            }

            Path filePath = Paths.get(vehicleDir, name + ".json");

            if (!Files.exists(filePath)) {
                log.warn("Profile {} not found on filesystem, falling back to car", name);
                VehicleProfile fallback = getProfile("car");
                fallback.setName("car");
                return fallback;
            }

            VehicleProfile profile = objectMapper.readValue(Files.readAllBytes(filePath), VehicleProfile.class);
            profileCache.put(name, profile);
            return profile;

        } catch (Exception e) {
            log.error("Error loading profile {}", name, e);
            VehicleProfile fallback = getProfile("car");
            fallback.setName("car");
            return fallback;
        }
    }



    public void saveProfile(String name, VehicleProfile profile) throws IOException {
        Files.createDirectories(Paths.get(vehicleDir));
        Path filePath = Paths.get(vehicleDir, name + ".json");
        Files.write(filePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(profile));
        profileCache.put(name, profile);
    }

    
    public List<String> listAvailableProfiles() {
        List<String> profiles = new ArrayList<>(profileCache.keySet());

        try {
            if (vehicleDir.startsWith("classpath:")) {
                String path = vehicleDir.replace("classpath:", "");
                var resources = getClass().getClassLoader().getResources(path);

                while (resources.hasMoreElements()) {
                    var url = resources.nextElement();
                    var dir = new File(url.toURI());

                    if (dir.exists() && dir.isDirectory()) {
                        for (File f : Objects.requireNonNull(dir.listFiles())) {
                            if (f.getName().endsWith(".json")) {
                                String name = f.getName().replace(".json", "");
                                if (!profiles.contains(name)) profiles.add(name);
                            }
                        }
                    }
                }

                return profiles;
            }

            // Filesystem
            Path dir = Paths.get(vehicleDir);
            if (Files.exists(dir)) {
                Files.list(dir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String name = p.getFileName().toString().replace(".json", "");
                        if (!profiles.contains(name)) profiles.add(name);
                    });
            }

        } catch (Exception e) {
            log.warn("Could not list vehicle profiles", e);
        }

        return profiles;
    }


}
