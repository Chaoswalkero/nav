package com.patriot.nav.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Routing Response mit Route und Turn-by-Turn Anweisungen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    
    @JsonProperty("status")
    private String status = "ok"; // ok, error, no_route
    
    @JsonProperty("error")
    private String error;
    
    /**
     * GeoJSON FeatureCollection mit der Route
     */
    @JsonProperty("geometry")
    private Map<String, Object> geometry;
    
    @JsonProperty("points")
    private List<RoutePoint> points;
    
    @JsonProperty("distance")
    private double totalDistance; // Meter
    
    @JsonProperty("time")
    private long totalTime; // Sekunden
    
    @JsonProperty("turn_instructions")
    private List<TurnInstruction> turnInstructions;
    
    @JsonProperty("vehicle")
    private String vehicleType;
    
    /**
     * Erstellt GeoJSON FeatureCollection aus Punkten
     */
    public static Map<String, Object> createGeoJsonGeometry(List<RoutePoint> points) {
        var coordinates = points.stream()
            .map(p -> List.of(p.getLongitude(), p.getLatitude()))
            .toList();
        
        var lineString = Map.of(
            "type", "LineString",
            "coordinates", coordinates
        );
        
        return Map.of(
            "type", "FeatureCollection",
            "features", List.of(
                Map.of(
                    "type", "Feature",
                    "properties", Map.of(),
                    "geometry", lineString
                )
            )
        );
    }
}
