package com.patriot.nav;

import com.patriot.nav.model.VehicleProfile;
import com.patriot.nav.routing.CompressedDijkstraRouter;
import com.patriot.nav.routing.CompressedGraph;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompressedDijkstraRouterTests {

    private CompressedGraph buildSimpleGraph(double distance, float speedKmh, Map<String,String> tags, int flags) {
        int[] lat = new int[]{0,0};
        int[] lon = new int[]{0,0};
        int[] firstEdge = new int[]{0,-1};
        int[] edgeTo = new int[]{1};
        int[] edgeNext = new int[]{-1};
        float[] weight = new float[]{(float) (distance / (speedKmh/3.6))};
        int[] flagsArr = new int[]{flags};
        byte[] highwayType = new byte[]{0};

        float[] distanceArr = new float[]{(float)distance};
        float[] speed = new float[]{speedKmh};
        String[] wayType = new String[]{"residential"};
        @SuppressWarnings("unchecked")
        Map<String,String>[] tagsArr = (Map<String,String>[]) new Map[1];
        tagsArr[0] = tags;
        boolean[] oneWay = new boolean[]{false};

        return new CompressedGraph(lat, lon, firstEdge, edgeTo, edgeNext,
                weight, flagsArr, highwayType,
                distanceArr, speed, wayType, tagsArr, oneWay);
    }

    @Test
    void testMaxspeedInfluencesWeight() {
        Map<String,String> tags = new HashMap<>();
        tags.put("maxspeed", "30");
        CompressedGraph g = buildSimpleGraph(1000, 60, tags, 0);
        CompressedDijkstraRouter router = new CompressedDijkstraRouter();
        VehicleProfile car = new VehicleProfile();
        car.setName("car");
        car.setMaxSpeed(130.0);
        car.setWayMultipliers(Map.of("residential", 1.0));
        com.patriot.nav.model.AccessProfile ap = new com.patriot.nav.model.AccessProfile();
        ap.setMotorVehicle(true);
        ap.setFoot(true);
        ap.setBicycle(true);
        car.setAccess(ap);

        double w = router.computeWeight(g, 0, car);
        // distance 1km, maxspeed 30 kmh => time 120s
        assertEquals(120.0, w, 0.1);
    }

    @Test
    void testVehicleMaxSpeedCapsWeight() {
        Map<String,String> tags = new HashMap<>();
        // way speed is 100 kmh but vehicle max 50
        CompressedGraph g = buildSimpleGraph(1000, 100, tags, 0);
        CompressedDijkstraRouter router = new CompressedDijkstraRouter();
        VehicleProfile car = new VehicleProfile();
        car.setName("car");
        car.setMaxSpeed(50.0);
        car.setWayMultipliers(Map.of("residential", 1.0));
        com.patriot.nav.model.AccessProfile ap = new com.patriot.nav.model.AccessProfile();
        ap.setMotorVehicle(true);
        car.setAccess(ap);

        double w = router.computeWeight(g, 0, car);
        // 1km at 50 km/h = 72s
        assertEquals(72.0, w, 0.1);
    }

    @Test
    void testAccessTagBlocksEdge() {
        Map<String,String> tags = new HashMap<>();
        tags.put("motor_vehicle", "no");
        CompressedGraph g = buildSimpleGraph(100, 30, tags, 0);
        CompressedDijkstraRouter router = new CompressedDijkstraRouter();
        VehicleProfile car = new VehicleProfile();
        car.setName("car");
        car.setMaxSpeed(100.0);
        car.setWayMultipliers(Map.of("residential", 1.0));
        com.patriot.nav.model.AccessProfile ap = new com.patriot.nav.model.AccessProfile();
        ap.setMotorVehicle(true);
        car.setAccess(ap);

        assertFalse(router.isEdgeAllowed(g, 0, car), "motor_vehicle=no should deny car");
    }
}