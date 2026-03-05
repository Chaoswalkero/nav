package com.patriot.nav.controller;

import com.patriot.nav.model.RouteRequest;
import com.patriot.nav.model.RouteResponse;
import com.patriot.nav.model.VehicleProfile;
import com.patriot.nav.service.RoutingService;
import com.patriot.nav.service.VehicleOptionsService;
import com.patriot.nav.service.VehicleProfileService;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Routing API
 */
@Slf4j
@RestController
@RequestMapping("/api/routing")
@CrossOrigin(origins = "*")
public class RoutingController {
    
    private final RoutingService routingService;
    private final VehicleProfileService vehicleProfileService;
    private final VehicleOptionsService vehicleOptionsService;
    private final RequestMappingHandlerMapping handlerMapping;
    
    public RoutingController(RoutingService routingService, VehicleProfileService vehicleProfileService, VehicleOptionsService vehicleOptionsService, RequestMappingHandlerMapping handlerMapping) {
        this.routingService = routingService;
        this.vehicleProfileService = vehicleProfileService;
        this.vehicleOptionsService = vehicleOptionsService;
        this.handlerMapping = handlerMapping;
    }
    
    /**
     * POST /api/routing/route
     * 
     * Findet Route zwischen zwei Punkten
     * 
     * Request-Body Beispiel:
     * {
     *   "start_lat": 48.123,
     *   "start_lon": 11.456,
     *   "end_lat": 48.789,
     *   "end_lon": 11.999,
     *   "vehicle": {
     *     "name": "car",
     *     "max_speed": 130,
     *     "way_multipliers": {"motorway": 0.6, "residential": 1.2},
     *     "blocked_tags": ["foot=only"]
     *   }
     * }
     */
    @PostMapping("/route")
    public ResponseEntity<RouteResponse> getRoute(@RequestBody RouteRequest request) {
        log.info("Route request: {} -> {}", 
            new double[]{request.getStartLat(), request.getStartLon()},
            new double[]{request.getEndLat(), request.getEndLon()}
        );
        
		RouteResponse response = routingService.findRoute(request);
        
        if ("error".equals(response.getStatus())) {
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /api/routing/vehicles
     * 
     * Gibt Liste aller verfügbaren Fahrzeug-Profile
     */
    @GetMapping("/vehicles")
    public ResponseEntity<List<String>> listVehicles() {
        List<String> vehicles = vehicleProfileService.listAvailableProfiles();
        return ResponseEntity.ok(vehicles);
    }
    
    /**
     * GET /api/routing/vehicle/{name}
     * 
     * Gibt ein spezifisches Fahrzeug-Profil
     */
    @GetMapping("/vehicle/{name}")
    public ResponseEntity<VehicleProfile> getVehicleProfile(@PathVariable String name) {
        VehicleProfile profile = vehicleProfileService.getProfile(name);
        return ResponseEntity.ok(profile);
    }
    
    /**
     * POST /api/routing/vehicle/{name}
     * 
     * Speichert ein Custom Fahrzeug-Profil
     */
    @PostMapping("/vehicle/{name}")
    public ResponseEntity<String> saveVehicleProfile(
            @PathVariable String name,
            @RequestBody VehicleProfile profile) {
        
        try {
            profile.setName(name);
            vehicleProfileService.saveProfile(name, profile);
            return ResponseEntity.ok("Profile '" + name + "' saved successfully");
        } catch (IOException e) {
            log.error("Error saving profile", e);
            return ResponseEntity.badRequest().body("Error saving profile: " + e.getMessage());
        }
    }
    
    /**
     * OPTIONS /api/routing/vehicles
     * 
     * Gibt die verfügbaren Fahrzeug-Profile und deren Schema mit detaillierten Typ-Informationen zurück.
     * 
     * Response-Struktur:
     * {
     *   "vehicle_schema": {
     *     "name": { 
     *       "type": "String" 
     *     },
     *     "maxSpeed": { 
     *       "type": "Int" 
     *     },
     *     "wayMultipliers": { 
     *       "type": "Map", 
     *       "mapType": "Map<String, Double>" 
     *     },
     *     "tagWeights": { 
     *       "type": "Map", 
     *       "mapType": "Map<String, Double>" 
     *     },
     *     "blockedTags": { 
     *       "type": "List", 
     *       "elementType": "String" 
     *     },
     *     "access": { 
     *       "type": "Object",
     *       "fields": { "motorVehicle": {...}, "foot": {...}, ... }
     *     }
     *   },
     *   "access_schema": {
     *     "motorVehicle": { "type": "Boolean" },
     *     "foot": { "type": "Boolean" },
     *     "bicycle": { "type": "Boolean" }
     *   },
     *   "profile_values": {
     *     "way_multipliers_keys": ["motorway", "trunk", "primary", ...],
     *     "tag_weights_keys": ["surface=paved", "surface=unpaved", ...],
     *     "blocked_tags_values": ["foot=only", "bicycle=only", ...]
     *   }
     * }
     */
    @RequestMapping(value = "/vehicles", method = RequestMethod.OPTIONS)
    public ResponseEntity<Map<String, Object>> getVehicleOptions(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String path = requestUri.startsWith(context) ? requestUri.substring(context.length()) : requestUri;

        java.util.Set<String> methods = new java.util.LinkedHashSet<>();

        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            java.util.Set<String> patterns = new java.util.LinkedHashSet<>();

            if (info.getPathPatternsCondition() != null && !info.getPathPatternsCondition().getPatterns().isEmpty()) {
                for (Object p : info.getPathPatternsCondition().getPatterns()) {
                    patterns.add(p.toString());
                }
            }

            for (String pattern : patterns) {
                String normalizedPattern = pattern.startsWith("/") ? pattern : ("/" + pattern);
                if (normalizedPattern.equals(path) || normalizedPattern.equals(path + "/") || (path + "/").equals(normalizedPattern)) {
                    java.util.Set<org.springframework.web.bind.annotation.RequestMethod> reqMethods = info.getMethodsCondition().getMethods();
                    if (reqMethods == null || reqMethods.isEmpty()) {
                        methods.add("GET");
                        methods.add("POST");
                        methods.add("PUT");
                        methods.add("DELETE");
                        methods.add("PATCH");
                    } else {
                        for (org.springframework.web.bind.annotation.RequestMethod rm : reqMethods) {
                            methods.add(rm.name());
                        }
                    }
                }
            }
        }

        methods.add("OPTIONS");

        String allow = String.join(", ", methods);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Allow", allow);
        headers.add("Access-Control-Allow-Methods", allow);

        return ResponseEntity.ok().headers(headers).body(vehicleOptionsService.getDynamicOptions());
    }


    
    /**
     * GET /api/routing/health
     * 
     * Health Check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Routing service is running");
    }
}
