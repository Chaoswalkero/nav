package com.patriot.nav.service;

import com.patriot.nav.model.TurnInstruction;
import com.patriot.nav.model.TurnInstruction.TurnType;
import com.patriot.nav.routing.CompressedGraph;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class NavigationService {

    private final LaneGuidanceService laneGuidanceService;

    public NavigationService(LaneGuidanceService laneGuidanceService) {
        this.laneGuidanceService = laneGuidanceService;
    }

    public record Result(int[] path, double cost) {}

    public List<TurnInstruction> buildTurnInstructions(Result result, CompressedGraph g) {
        int[] path = result.path();
        List<TurnInstruction> instructions = new ArrayList<>();

        if (path.length == 0) return instructions;

        // START
        TurnInstruction start = new TurnInstruction();
        start.setSequence(0);
        start.setType(TurnType.START);
        start.setInstruction("Starten");
        start.setWayName("");
        start.setDistance(0);
        start.setTime(0);
        start.setLatitude(g.lat(path[0]));
        start.setLongitude(g.lon(path[0]));
        start.setBearing(0);
        instructions.add(start);

        if (path.length == 1) {
                TurnInstruction finishOne = new TurnInstruction();
                finishOne.setSequence(1);
                finishOne.setType(TurnType.FINISH);
                finishOne.setInstruction("Ziel erreicht");
                finishOne.setWayName("");
                finishOne.setDistance(0);
                finishOne.setTime(0);
                finishOne.setLatitude(g.lat(path[0]));
                finishOne.setLongitude(g.lon(path[0]));
                finishOne.setBearing(0);
                instructions.add(finishOne);
            return instructions;
        }

        double accDist = 0;
        long accTime = 0;
        int seq = 1;

        for (int i = 1; i < path.length - 1; i++) {
            int a = path[i - 1];
            int b = path[i];
            int c = path[i + 1];

            int edgeAB = findEdge(g, a, b);
            int edgeBC = findEdge(g, b, c);

            if (edgeAB == -1 || edgeBC == -1) continue;

            double bearingAB = bearing(g.lat(a), g.lon(a), g.lat(b), g.lon(b));
            double bearingBC = bearing(g.lat(b), g.lon(b), g.lat(c), g.lon(c));

            double angle = normalizeAngle(bearingBC - bearingAB);
            TurnType type = classifyTurn(angle);

            accDist += g.distance(edgeAB);
            accTime += Math.round(g.weight(edgeAB));

                if (type != TurnType.STRAIGHT) {
                String wayName = Optional.ofNullable(g.tags(edgeBC))
                    .map(m -> m.get("name"))
                    .orElse(g.wayType(edgeBC));

                TurnInstruction ti = new TurnInstruction();
                ti.setSequence(seq++);
                ti.setType(type);
                ti.setInstruction(typeToHuman(type, wayName));
                ti.setWayName(wayName);
                ti.setDistance(accDist);
                ti.setTime(accTime);
                ti.setLatitude(g.lat(b));
                ti.setLongitude(g.lon(b));
                ti.setBearing(bearingBC);

                // Lane guidance: analyze tags on the incoming way (edgeAB)
                var tags = g.tags(edgeAB);
                var laneInfo = laneGuidanceService.analyze(tags, type);
                ti.setTotalLanes(laneInfo.totalLanes());
                ti.setLaneTokens(laneInfo.laneTokens());
                ti.setRecommendedLanes(laneInfo.recommended());
                ti.setLaneMessage(laneInfo.message());

                instructions.add(ti);

                accDist = 0;
                accTime = 0;
                }
        }

        // Letzte Kante
        int lastA = path[path.length - 2];
        int lastB = path[path.length - 1];
        int lastEdge = findEdge(g, lastA, lastB);
        if (lastEdge != -1) {
            accDist += g.distance(lastEdge);
            accTime += Math.round(g.weight(lastEdge));
        }

        TurnInstruction last = new TurnInstruction();
        last.setSequence(seq);
        last.setType(TurnType.FINISH);
        last.setInstruction("Ziel erreicht");
        last.setWayName("");
        last.setDistance(accDist);
        last.setTime(accTime);
        last.setLatitude(g.lat(lastB));
        last.setLongitude(g.lon(lastB));
        last.setBearing(0);
        instructions.add(last);

        return instructions;
    }

    private int findEdge(CompressedGraph g, int from, int to) {
        int edge = g.firstEdge(from);
        while (edge != -1) {
            if (g.edgeTo(edge) == to) return edge;
            edge = g.edgeNext(edge);
        }
        return -1;
    }

    private double bearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                   Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private double normalizeAngle(double angle) {
        return (angle + 540) % 360 - 180;
    }

    private TurnType classifyTurn(double angle) {
        if (angle > -15 && angle < 15) return TurnType.STRAIGHT;
        if (angle >= 15 && angle < 45) return TurnType.SLIGHT_RIGHT;
        if (angle >= 45 && angle < 135) return TurnType.RIGHT;
        if (angle >= 135 || angle <= -135) return TurnType.U_TURN;
        if (angle <= -45 && angle > -135) return TurnType.LEFT;
        if (angle <= -15 && angle > -45) return TurnType.SLIGHT_LEFT;
        return TurnType.STRAIGHT;
    }

    private String typeToHuman(TurnType type, String wayName) {
        String target = (wayName == null || wayName.isBlank()) ? "der Straße" : wayName;
        return switch (type) {
            case START -> "Starten";
            case STRAIGHT -> "Geradeaus weiterfahren";
            case SLIGHT_LEFT -> "Leicht links auf " + target;
            case LEFT -> "Links abbiegen auf " + target;
            case SHARP_LEFT -> "Scharf links abbiegen auf " + target;
            case SLIGHT_RIGHT -> "Leicht rechts auf " + target;
            case RIGHT -> "Rechts abbiegen auf " + target;
            case SHARP_RIGHT -> "Scharf rechts abbiegen auf " + target;
            case U_TURN -> "Wenden";
            case FINISH -> "Ziel erreicht";
        };
    }
}
