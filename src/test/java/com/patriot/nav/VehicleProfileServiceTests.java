package com.patriot.nav;

import com.patriot.nav.model.VehicleProfile;
import com.patriot.nav.service.VehicleProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VehicleProfileServiceTests {
    
    @Autowired
    private VehicleProfileService vehicleProfileService;
    
    @Test
    void testVehicleProfileServiceInitialized() {
        assertNotNull(vehicleProfileService, "VehicleProfileService sollte autowired werden");
    }
    
    @Test
    void testGetCarProfile() {
        VehicleProfile car = vehicleProfileService.getProfile("car");
        
        assertNotNull(car, "Car-Profile sollte nicht null sein");
        assertEquals("car", car.getName());
        assertEquals(130, car.getMaxSpeed());
        assertNotNull(car.getWayMultipliers());
        assertNotNull(car.getTagWeights());
        assertNotNull(car.getBlockedTags());
        
        System.out.println("✓ Car Profile geladen:");
        System.out.println("  - Max Speed: " + car.getMaxSpeed() + " km/h");
        System.out.println("  - Way Multipliers: " + car.getWayMultipliers().size());
        System.out.println("  - Blocked Tags: " + car.getBlockedTags().size());
    }
    
    @Test
    void testGetBicycleProfile() {
        VehicleProfile bike = vehicleProfileService.getProfile("bicycle");
        
        assertNotNull(bike, "Bicycle-Profile sollte nicht null sein");
        assertEquals("bicycle", bike.getName());
        assertEquals(25, bike.getMaxSpeed(), "Maximalgeschwindigkeit sollte 25 km/h betragen");
        // das Fahrradprofil enthält keinen Eintrag für "motorway" – es wird über andere Wege gesteuert
        assertFalse(bike.getWayMultipliers().containsKey("motorway"), "Fahrradprofil sollte keine Autobahn-Multiplikator haben");
        assertTrue(bike.getWayMultipliers().containsKey("cycleway"), "Mindestens cycleway-Multiplikator muss existieren");
        
        System.out.println("✓ Bicycle Profile geladen: max speed " + bike.getMaxSpeed() + " km/h");
    }
    
    @Test
    void testGetFootProfile() {
        VehicleProfile foot = vehicleProfileService.getProfile("foot");
        
        assertNotNull(foot, "Foot-Profile sollte nicht null sein");
        assertEquals("foot", foot.getName());
        assertEquals(6, foot.getMaxSpeed(), "Fußgängergeschwindigkeit hat sich auf 6 km/h geändert");
        
        System.out.println("✓ Foot Profile geladen: max speed " + foot.getMaxSpeed() + " km/h");
    }
    
    @Test
    void testListAvailableProfiles() {
        List<String> profiles = vehicleProfileService.listAvailableProfiles();
        
        assertNotNull(profiles);
        assertFalse(profiles.isEmpty(), "Es sollten mindestens die Default-Profile vorhanden sein");
        assertTrue(profiles.contains("car"), "Car-Profile sollte enthalten sein");
        assertTrue(profiles.contains("bicycle"), "Bicycle-Profile sollte enthalten sein");
        assertTrue(profiles.contains("foot"), "Foot-Profile sollte enthalten sein");
        
        System.out.println("✓ Verfügbare Profile: " + profiles);
    }
    
    @Test
    void testProfileCaching() {
        // Erstes Abrufen
        VehicleProfile profile1 = vehicleProfileService.getProfile("car");
        // Zweites Abrufen (sollte aus Cache kommen)
        VehicleProfile profile2 = vehicleProfileService.getProfile("car");
        
        assertNotNull(profile1);
        assertNotNull(profile2);
        // Da Caching aktiv ist, sollte die gleiche Instanz sein
        assertSame(profile1, profile2, "Beim wiederholten Laden sollte das selbe Objekt aus dem Cache zurückgegeben werden");
        
        System.out.println("✓ Profile Caching funktioniert");
    }
    
    @Test
    void testProfileProperties() {
        VehicleProfile car = vehicleProfileService.getProfile("car");
        
        // Überprüfe alle Required Properties
        assertNotNull(car.getName());
        assertTrue(car.getMaxSpeed() > 0);
        assertNotNull(car.getWayMultipliers());
        assertNotNull(car.getTagWeights());
        assertNotNull(car.getBlockedTags());
        assertTrue(car.getHeuristicWeight() > 0);
        
        System.out.println("✓ Alle Profile Properties valid");
        System.out.println("  - Name: " + car.getName());
        System.out.println("  - Max Speed: " + car.getMaxSpeed());
        System.out.println("  - Heuristic Weight: " + car.getHeuristicWeight());
    }
    
    @Test
    void testDefaultProfileMultipliers() {
        VehicleProfile car = vehicleProfileService.getProfile("car");
        Map<String, Double> multipliers = car.getWayMultipliers();
        
        // Überprüfe erwartete Werte (nur für das Auto‑Profil, andere Profile haben eigene Sätze)
        assertTrue(multipliers.containsKey("motorway"));
        assertTrue(multipliers.containsKey("residential"));
        assertTrue(multipliers.get("motorway") < 1.0, "Motorway sollte schneller sein");
        assertTrue(multipliers.get("residential") > 1.0, "Residential sollte langsamer sein");
        
        System.out.println("✓ Multipliers sind konsistent für car-Profil:");
        System.out.println("  - Motorway: " + multipliers.get("motorway"));
        System.out.println("  - Residential: " + multipliers.get("residential"));
    }
    
    @Test
    void testUnknownProfileFallsBackToCar() {
        VehicleProfile unknown = vehicleProfileService.getProfile("spaceship");
        assertNotNull(unknown, "Fallback-Profil sollte nicht null sein");
        assertEquals("car", unknown.getName(), "Unbekannte Profile fallen auf car zurück");
        assertEquals(130, unknown.getMaxSpeed(), "Fallback-profil hat car-Geschwindigkeit");
    }
    
}
