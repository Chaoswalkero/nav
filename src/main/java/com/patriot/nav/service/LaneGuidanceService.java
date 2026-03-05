package com.patriot.nav.service;

import com.patriot.nav.model.TurnInstruction.TurnType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LaneGuidanceService {

    public record LaneResult(Integer totalLanes, List<String> laneTokens, List<Integer> recommended, String message) {}

    public LaneResult analyze(Map<String,String> tags, TurnType turnType) {
        if (tags == null) return new LaneResult(null, null, List.of(), null);

        // Prefer generic turn:lanes, then directional alternatives
        String raw = tags.get("turn:lanes");
        if (raw == null) raw = tags.get("turn:lanes:forward");
        if (raw == null) raw = tags.get("turn:lanes:backward");

        List<String> tokens = null;
        if (raw != null && !raw.isBlank()) {
            tokens = parseRawTokens(raw);
        }

        Integer total = null;
        String lanesTag = tags.get("lanes");
        if (lanesTag != null) {
            try { total = Integer.parseInt(lanesTag.trim()); } catch (NumberFormatException ignored) {}
        }
        if (total == null && tokens != null) total = tokens.size();

        // Determine required maneuver tokens
        Set<String> required = requiredTokensFor(turnType);

        List<Integer> recommended = new ArrayList<>();
        if (tokens != null && !tokens.isEmpty()) {
            for (int i = 0; i < tokens.size(); i++) {
                String lane = tokens.get(i);
                String[] parts = lane.split(";");
                for (String p : parts) {
                    String kk = p.trim().toLowerCase();
                    for (String rq : required) {
                        if (kk.contains(rq)) {
                            recommended.add(i);
                            break;
                        }
                    }
                    if (recommended.contains(i)) break;
                }
            }
        }

        String message = buildMessage(recommended, total, turnType);

        return new LaneResult(total, tokens == null ? null : List.copyOf(tokens), List.copyOf(recommended), message);
    }

    private List<String> parseRawTokens(String raw) {
        String[] lanes = raw.split("\\|");
        List<String> out = new ArrayList<>();
        for (String l : lanes) out.add(l.trim());
        return out;
    }

    private Set<String> requiredTokensFor(TurnType t) {
        return switch (t) {
            case LEFT, SLIGHT_LEFT, SHARP_LEFT -> Set.of("left");
            case RIGHT, SLIGHT_RIGHT, SHARP_RIGHT -> Set.of("right");
            case STRAIGHT -> Set.of("through", "straight");
            case U_TURN -> Set.of("reverse", "uturn", "sharp_left", "sharp_right");
            default -> Set.of();
        };
    }

    private String buildMessage(List<Integer> recommended, Integer total, TurnType t) {
        if (recommended == null || recommended.isEmpty()) return null;
        if (total == null) {
            // generic message
            return recommended.size() == 1 ? "Nutzen Sie die angezeigte Spur" : "Nutzen Sie eine der angezeigten Spuren";
        }

        // Determine left vs right
        int leftmost = recommended.stream().min(Integer::compareTo).orElse(0);
        int rightmost = recommended.stream().max(Integer::compareTo).orElse(0);

        boolean allRight = leftmost >= Math.max(0, total - recommended.size());
        boolean allLeft = rightmost <= recommended.size() - 1;

        if (recommended.size() == 1) {
            int idx = recommended.get(0);
            if (idx == 0) return "Nutzen Sie die linke Spur";
            if (idx == total - 1) return "Nutzen Sie die rechte Spur";
            return "Nutzen Sie die markierte Spur";
        }

        if (allRight) return "Nutzen Sie eine der rechten Spuren";
        if (allLeft) return "Nutzen Sie eine der linken Spuren";
        return "Nutzen Sie eine der markierten Spuren";
    }
}
