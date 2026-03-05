package com.patriot.nav.service;

import com.patriot.nav.model.TurnInstruction.TurnType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LaneGuidanceServiceTests {

    private final LaneGuidanceService service = new LaneGuidanceService();

    @Test
    void testParseTurnLanesRight() {
        Map<String,String> tags = new HashMap<>();
        tags.put("turn:lanes", "through|through;right|right");

        var res = service.analyze(tags, TurnType.RIGHT);

        assertNotNull(res.laneTokens());
        assertEquals(3, res.totalLanes());
        assertEquals(2, res.recommended().size());
        assertTrue(res.recommended().contains(1));
        assertTrue(res.recommended().contains(2));
        assertNotNull(res.message());
        assertTrue(res.message().toLowerCase().contains("recht"));
    }

    @Test
    void testParseTurnLanesLeft() {
        Map<String,String> tags = new HashMap<>();
        tags.put("turn:lanes", "left|through|through");

        var res = service.analyze(tags, TurnType.LEFT);

        assertNotNull(res.laneTokens());
        assertEquals(3, res.totalLanes());
        assertEquals(1, res.recommended().size());
        assertTrue(res.recommended().contains(0));
        assertNotNull(res.message());
        assertTrue(res.message().toLowerCase().contains("link"));
    }

    @Test
    void testLanesCountOnly() {
        Map<String,String> tags = new HashMap<>();
        tags.put("lanes", "4");

        var res = service.analyze(tags, TurnType.STRAIGHT);

        assertNull(res.laneTokens());
        assertEquals(4, res.totalLanes());
        assertTrue(res.recommended().isEmpty());
        assertNull(res.message());
    }
}
