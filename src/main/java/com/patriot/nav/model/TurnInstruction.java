package com.patriot.nav.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Turn-by-Turn Navigation Anweisung
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TurnInstruction {
    
    public enum TurnType {
        START,
        STRAIGHT,
        SLIGHT_LEFT,
        LEFT,
        SHARP_LEFT,
        SLIGHT_RIGHT,
        RIGHT,
        SHARP_RIGHT,
        U_TURN,
        FINISH
    }
    
    @JsonProperty("sequence")
    private int sequence;
    
    @JsonProperty("type")
    private TurnType type;
    
    @JsonProperty("instruction")
    private String instruction; // Z.B. "Biegen Sie links ab auf Maximilianstraße"
    
    @JsonProperty("way_name")
    private String wayName; // Name der Straße
    
    @JsonProperty("distance")
    private double distance; // Meter bis zur nächsten Anweisung
    
    @JsonProperty("time")
    private long time; // Sekunden
    
    @JsonProperty("lat")
    private double latitude;
    
    @JsonProperty("lon")
    private double longitude;
    
    @JsonProperty("bearing")
    private double bearing; // Compass bearing in Grad

    // Lane guidance (optional)
    @JsonProperty("total_lanes")
    private Integer totalLanes;

    @JsonProperty("recommended_lanes")
    private List<Integer> recommendedLanes; // 0-based lane indices left->right

    @JsonProperty("lane_tokens")
    private List<String> laneTokens; // raw tokens from turn:lanes split by '|'

    @JsonProperty("lane_message")
    private String laneMessage; // human readable hint
}
