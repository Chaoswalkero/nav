package com.patriot.nav.graph;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * Kante zwischen zwei Knoten im Routing-Graph
 */
@Data
@AllArgsConstructor
public class Edge {
    
    private long targetNodeId;
    private double distance; // Meter
    private double cost; // Gewichtete Kosten
    private String wayType; // Z.B. "motorway", "residential", etc.
    private Map<String, String> wayTags; // OSM Tags
    private String wayName; // Name der Straße
    private double maxSpeed; // km/h für diese Kante
    private boolean oneway; // Einbahnstraße?
    
    /**
     * Berechnet Zeit in Sekunden basierend auf Geschwindigkeit
     */
    public long getTimeSeconds() {
        if (maxSpeed <= 0) return Long.MAX_VALUE;
        double timeHours = (distance / 1000.0) / maxSpeed;
        return Math.round(timeHours * 3600);
    }
}
