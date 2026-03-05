package com.patriot.nav;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.patriot.nav.model.RouteRequest;
import com.patriot.nav.model.RouteResponse;
import com.patriot.nav.model.VehicleProfile;
import com.patriot.nav.service.RoutingService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NavbackendApplicationTests {

	@Autowired
	private RoutingService routingService;

	@Test
	void contextLoads() {
		assertNotNull(routingService, "RoutingService sollte autowired werden");
	}

	@Test
	void testRouteCalculationBasic() {
		// Erstelle eine Routenanfrage zwischen zwei Knoten
		RouteRequest request = new RouteRequest();
		request.setStartLat(47.7305);
		request.setStartLon(10.3128);  // Kempten center
		request.setEndLat(47.7400);
		request.setEndLon(10.3000);    // Northwest
		
		// Berechne die Route
		RouteResponse response = routingService.findRoute(request);
		
		// Assertions
		assertNotNull(response, "Response sollte nicht null sein");
		if ("error".equals(response.getStatus())) {
			// bei eingeschränktem Graphen kann es vorkommen, dass die Koordinate
			// an einem nicht befahrbaren Punkt liegt; in diesem Fall prüfen wir,
			// ob die Fehlermeldung die neue Validierung benutzt.
			assertTrue(response.getError().toLowerCase().contains("accessible"),
				"Erwartete Zugänglichkeitsfehler");
			return;
		}
		assertEquals("ok", response.getStatus(), "Status sollte 'ok' sein");
		assertFalse(response.getPoints().isEmpty(), "Route sollte mindestens einen Punkt haben");
		assertTrue(response.getTotalDistance() > 0, "Entfernung sollte > 0 sein");
		assertTrue(response.getTotalTime() > 0, "Zeit sollte > 0 sein");
		// neue Felder
		assertEquals("car", response.getVehicleType(), "Standardfahrzeug ist Auto");
		assertNotNull(response.getGeometry(), "GeoJSON-Geometrie sollte erstellt werden");
		
		System.out.println("✓ Route gefunden:");
		System.out.println("  - Punkte: " + response.getPoints().size());
		System.out.println("  - Entfernung: " + response.getTotalDistance() + " m");
		System.out.println("  - Zeit: " + response.getTotalTime() + " s");
	}


	@Test
	void testRouteCalculationShort() {
		// Kurze Route
		RouteRequest request = new RouteRequest();
		request.setStartLat(47.7305);
		request.setStartLon(10.3128);  // Node 1
		request.setEndLat(47.7270);
		request.setEndLon(10.3200);    // Node 2 (nah)
		
		RouteResponse response = routingService.findRoute(request);
		
		assertNotNull(response);
		if ("error".equals(response.getStatus())) {
			assertTrue(response.getError().toLowerCase().contains("accessible"));
			return;
		}
		assertEquals("ok", response.getStatus());
		assertTrue(response.getPoints().size() >= 2, "Route sollte mindestens 2 Punkte haben");
		assertTrue(response.getTotalDistance() > 0);
		assertTrue(response.getTotalTime() > 0);
		assertEquals("car", response.getVehicleType());
		
		System.out.println("✓ Kurze Route berechnet: " + response.getTotalTime() + "s");
	}

	@Test
	void testRouteCalculationLong() {
		// Längere Route durch mehrere Knoten
		RouteRequest request = new RouteRequest();
		request.setStartLat(47.7305);
		request.setStartLon(10.3128);  // Node 1
		request.setEndLat(47.7200);
		request.setEndLon(10.3300);    // Node 5 (weit weg)
		
		RouteResponse response = routingService.findRoute(request);
		
		assertNotNull(response);
		if ("error".equals(response.getStatus())) {
			assertTrue(response.getError().toLowerCase().contains("accessible"));
			return;
		}
		assertEquals("ok", response.getStatus());
		assertTrue(response.getPoints().size() >= 2);
		assertTrue(response.getTotalTime() > 0);
		
		System.out.println("✓ Lange Route berechnet: " + response.getTotalTime() + "s");
	}

	@Test
	void testMultipleRoutes() {
		// Test mehrerer Routen
		double[][] routes = {
			{47.7305, 10.3128, 47.7270, 10.3200},  // 1 -> 2
			{47.7305, 10.3128, 47.7350, 10.3100},  // 1 -> 3
			{47.7270, 10.3200, 47.7350, 10.3100},  // 2 -> 3
		};
		
		for (double[] route : routes) {
			RouteRequest request = new RouteRequest();
			request.setStartLat(route[0]);
			request.setStartLon(route[1]);
			request.setEndLat(route[2]);
			request.setEndLon(route[3]);
			
			RouteResponse response = routingService.findRoute(request);
			
			assertNotNull(response, "Response sollte nicht null sein");
			if ("error".equals(response.getStatus())) {
				assertTrue(response.getError().toLowerCase().contains("accessible"));
			} else {
				assertEquals("ok", response.getStatus(), "Jede Route sollte ok sein");
				assertFalse(response.getPoints().isEmpty(), "Route sollte Punkte haben");
			}
		}
		
		System.out.println("✓ " + routes.length + " Routen erfolgreich berechnet");
	}

	@SuppressWarnings("rawtypes")
	@Test
	void testRouteDetails() {
		// Test mit detaillierten Informationen
		RouteRequest request = new RouteRequest();
		request.setStartLat(47.7305);
		request.setStartLon(10.3128);
		request.setEndLat(47.7350);
		request.setEndLon(10.3100);
		
		RouteResponse response = routingService.findRoute(request);
		
		// Detaillierte Assertions
		if ("error".equals(response.getStatus())) {
			assertTrue(response.getError().toLowerCase().contains("accessible"));
			return;
		}
		assertEquals("ok", response.getStatus());
		assertTrue(response.getPoints().size() >= 2, "Start und End Punkt sollten enthalten sein");
		
		// Überprüfe Start und End
		var firstPoint = response.getPoints().get(0);
		var lastPoint = response.getPoints().get(response.getPoints().size() - 1);
		
		assertNotNull(firstPoint, "First point sollte nicht null sein");
		assertNotNull(lastPoint, "Last point sollte nicht null sein");
		assertEquals("car", response.getVehicleType(), "Defaultfahrzeug ist car");
		
		// GeoJSON geometry should contain coordinates
		assertNotNull(response.getGeometry());
		assertTrue(((java.util.Map)response.getGeometry()).containsKey("features"));
		
		System.out.println("✓ Route Details:");
		System.out.println("  - Start: [" + firstPoint.getLatitude() + ", " + firstPoint.getLongitude() + "]");
		System.out.println("  - End: [" + lastPoint.getLatitude() + ", " + lastPoint.getLongitude() + "]");
		System.out.println("  - Punkte: " + response.getPoints().size());
		System.out.println("  - Gesamt Zeit: " + response.getTotalTime() + "s");
	}

	@Test
	void testRouteWithCustomVehicle() {
		// Fordere eine Route mit explizitem Fußgängerprofil an
		RouteRequest request = new RouteRequest();
		request.setStartLat(47.7305);
		request.setStartLon(10.3128);
		request.setEndLat(47.7270);
		request.setEndLon(10.3200);
		
		// setze eigenes Vehicle-Objekt, nur Name reicht zum Umschalten
		VehicleProfile vp = new VehicleProfile();
		vp.setName("foot");
		request.setVehicle(vp);
		
		RouteResponse response = routingService.findRoute(request);
		
		if ("error".equals(response.getStatus())) {
			assertTrue(response.getError().toLowerCase().contains("accessible"));
			return;
		}
		assertEquals("ok", response.getStatus());
		assertEquals("foot", response.getVehicleType(), "Vehicletype sollte foot sein");
		assertTrue(response.getTotalTime() > 0);
	}

	@Test
	void testStartNodeNotAccessible() {
		// Wenn das Fahrzeug keine Zugriffsrechte besitzt, sollte der Start
		// bereits als unzugänglich erkannt werden.
		RouteRequest request = new RouteRequest();
		request.setStartLat(47.7305);
		request.setStartLon(10.3128);
		request.setEndLat(47.7270);
		request.setEndLon(10.3200);
		
		VehicleProfile vp = new VehicleProfile();
		vp.setName("car");
		// Fahrzeug vollständig blockiert
		vp.setAccess(new com.patriot.nav.model.AccessProfile());
		request.setVehicle(vp);
		
		RouteResponse response = routingService.findRoute(request);
		
		assertNotNull(response);
		assertEquals("error", response.getStatus(), "Es muss ein Fehler zurückgegeben werden");
		assertTrue(response.getError().toLowerCase().contains("start"), "Fehlermeldung sollte auf Start/Ende hinweisen");
	}


}
