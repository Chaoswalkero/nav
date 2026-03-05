package com.patriot.nav.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleProfile {

    @JsonProperty("name")
    private String name;

    @JsonProperty("access")
    private AccessProfile access;

    @JsonProperty("max_speed")
    private Double maxSpeed;

    @JsonProperty("way_multipliers")
    private Map<String, Double> wayMultipliers;

    @JsonProperty("tag_weights")
    private Map<String, Double> tagWeights;

    @JsonProperty("blocked_tags")
    private List<String> blockedTags;

    @JsonProperty("heuristic_weight")
    private Double heuristicWeight;

    @JsonProperty("ignore_one_way")
    private Boolean ignoreOneWay;

    public double calculateWeight(String wayType, Map<String, String> tags) {
        double weight = 1.0;

        if (wayMultipliers != null && wayMultipliers.containsKey(wayType)) {
            weight *= wayMultipliers.get(wayType);
        }

        if (tagWeights != null) {
            for (Map.Entry<String, Double> tagWeight : tagWeights.entrySet()) {
                String[] parts = tagWeight.getKey().split("=");
                if (parts.length == 2) {
                    String tagValue = tags.get(parts[0]);
                    if (tagValue != null && tagValue.equals(parts[1])) {
                        weight *= tagWeight.getValue();
                    }
                }
            }
        }

        return weight;
    }

    public boolean isPassable(Map<String, String> tags) {
        if (blockedTags == null) return true;

        for (String blockedTag : blockedTags) {
            String[] parts = blockedTag.split("=");
            if (parts.length == 2) {
                String tagValue = tags.get(parts[0]);
                if (tagValue != null && tagValue.equals(parts[1])) {
                    return false;
                }
            }
        }
        return true;
    }
}
