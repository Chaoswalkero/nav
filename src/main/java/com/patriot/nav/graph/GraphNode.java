package com.patriot.nav.graph;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * Graph-Knoten für Routing
 */
@Data
@RequiredArgsConstructor
public class GraphNode {
    
    private final long osmNodeId;
    private final double latitude;
    private final double longitude;
    
    /**
     * Ausgehende Kanten zu anderen Knoten
     * Key: Ziel-Knoten-ID, Value: Edge Objekt
     */
    private final Map<Long, Edge> edges = new HashMap<>();
    
    /**
     * Heuristischer Abstand zum Ziel (für A*)
     */
    private double heuristic = 0;
    
    /**
     * Für A*: Kostensum vom Start zum aktuellen Knoten
     */
    private double gCost = Double.POSITIVE_INFINITY;
    
    /**
     * Für A*: f(n) = g(n) + h(n)
     */
    private double fCost = Double.POSITIVE_INFINITY;
    
    /**
     * Parent für Pfad-Rekonstruktion
     */
    private GraphNode parent = null;
    
    /**
     * Haversine-Distanz zu anderem Punkt in Metern
     */
    public double distanceTo(double lat, double lon) {
        final int EARTH_RADIUS = 6371000; // meters
        
        double dLat = Math.toRadians(lat - this.latitude);
        double dLon = Math.toRadians(lon - this.longitude);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(lat)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.asin(Math.sqrt(a));
        
        return EARTH_RADIUS * c;
    }
    
    public void reset() {
        gCost = Double.POSITIVE_INFINITY;
        fCost = Double.POSITIVE_INFINITY;
        parent = null;
        heuristic = 0;
    }
}
