package com.patriot.nav.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ein Punkt auf der Route (für GeoJSON Feature)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutePoint {
    
    @JsonProperty("lat")
    private double latitude;
    
    @JsonProperty("lon")
    private double longitude;
    
    @JsonProperty("distance")
    private double distanceFromStart; // Meter
    
    @JsonProperty("time")
    private long timeFromStart; // Sekunden
}
