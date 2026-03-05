package com.patriot.nav.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request object für Routing Anfragen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {
    
    @JsonProperty("start_lat")
    private Double startLat;
    
    @JsonProperty("start_lon")
    private Double startLon;
    
    @JsonProperty("end_lat")
    private Double endLat;
    
    @JsonProperty("end_lon")
    private Double endLon;
    
    /**
     * Custom Vehicle Definition als JSON
     * Falls null: default car wird verwendet
     */
    @JsonProperty("vehicle")
    private VehicleProfile vehicle;
}
