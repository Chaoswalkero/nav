package com.patriot.nav.util;

/**
 * Utility für Koordinaten-Berechnungen
 */
public class CoordinateUtils {
    
    private static final int EARTH_RADIUS = 6371000; // meters
    
    /**
     * Berechnet Haversine-Distanz zwischen zwei Koordinaten
     */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.asin(Math.sqrt(a));
        
        return EARTH_RADIUS * c;
    }
    
    /**
     * Berechnet Bearing (Kompassrichtung) zwischen zwei Punkten
     */
    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                   - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
    
    /**
     * Berechnet Drehwinkel zwischen zwei Bearings
     */
    public static double turnAngle(double fromBearing, double toBearing) {
        double angle = toBearing - fromBearing;
        
        // Normalisiere auf -180 bis 180
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        
        return angle;
    }
    
    /**
     * Bestimmt die Richtung einer Kurve
     */
    public static String getTurnDirection(double angle) {
        if (Math.abs(angle) < 10) {
            return "straight";
        } else if (angle > 0) {
            return "right";
        } else {
            return "left";
        }
    }
    
    /**
     * Formatiert Distanz lesbar
     */
    public static String formatDistance(double meters) {
        if (meters < 1000) {
            return String.format("%.0f m", meters);
        } else {
            return String.format("%.2f km", meters / 1000.0);
        }
    }
    
    /**
     * Formatiert Zeit lesbar
     */
    public static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        
        if (hours > 0) {
            return String.format("%d h %d min", hours, minutes);
        } else {
            return String.format("%d min", minutes);
        }
    }
}
