package com.patriot.nav.service;

import com.patriot.nav.model.*;
import com.patriot.nav.routing.CompressedDijkstraRouter;
import com.patriot.nav.routing.CompressedGraph;
import com.patriot.nav.routing.CompressedNearestNodeFinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.io.File;
import com.patriot.nav.routing.TileStore;

@Slf4j
@Service
public class RoutingService {

	private final OSMGraphService osmGraphService;
	private final VehicleProfileService vehicleProfileService;
	private final CompressedDijkstraRouter router = new CompressedDijkstraRouter();
	private final NavigationService navigationService;

	public RoutingService(OSMGraphService osmGraphService, VehicleProfileService vehicleProfileService,
			NavigationService navigationService) {
		this.osmGraphService = osmGraphService;
		this.vehicleProfileService = vehicleProfileService;
		this.navigationService = navigationService;
	}

	public RouteResponse findRoute(RouteRequest request) {
		try {
			if (request.getStartLat() == null || request.getStartLon() == null || request.getEndLat() == null
					|| request.getEndLon() == null) {
				return createErrorResponse("Start and end coordinates are required");
			}

			CompressedGraph graph = osmGraphService.getGraph();
			if (graph == null) {
				return createErrorResponse("Graph not initialized");
			}

			// If tiles are available, assemble a subgraph for the source/target tiles to avoid loading full graph
			try {
				File chunksDir = resolveChunksDir();
				File meta = new File(chunksDir, "metadata.properties");
				if (meta.exists()) {
					TileStore store = new TileStore(chunksDir);
					int[] sTile = store.tileFor(request.getStartLat(), request.getStartLon());
					int[] tTile = store.tileFor(request.getEndLat(), request.getEndLon());
					int gs = store.getGridSize();
					int r0 = Math.max(0, Math.min(sTile[0], tTile[0]) - 1);
					int r1 = Math.min(Math.max(sTile[0], tTile[0]) + 1, gs - 1);
					int c0 = Math.max(0, Math.min(sTile[1], tTile[1]) - 1);
					int c1 = Math.min(Math.max(sTile[1], tTile[1]) + 1, gs - 1);
					Set<String> tiles = new HashSet<>();
					for (int rr = r0; rr <= r1; rr++) for (int cc = c0; cc <= c1; cc++) tiles.add(String.format("tile_%d_%d", rr, cc));
					CompressedGraph sub = store.assembleGraphForTiles(tiles, graph);
					if (sub.nodeCount() > 0) graph = sub; // use assembled subgraph for routing
				}
			} catch (Exception e) {
				log.debug("Tile-based assembly failed, falling back to full graph: {}", e.getMessage(), e);
			}

			// helper: resolve chunks dir similar to GraphPreprocessor/StartupConfig


			// resolve the vehicle profile before snapping to nearest nodes so that
			// the finder can ignore nodes that are unusable for this profile.
			VehicleProfile vehicle = resolveVehicleProfile(request.getVehicle());

			int sourceIdx = CompressedNearestNodeFinder.findNearest(graph, request.getStartLat(),
					request.getStartLon(), vehicle);
			int targetIdx = CompressedNearestNodeFinder.findNearest(graph, request.getEndLat(),
					request.getEndLon(), vehicle);

			if (sourceIdx == -1 || targetIdx == -1) {
				return createErrorResponse("Start or end node not found or not accessible for vehicle type");
			}

			log.info("Routing from node {} to node {}", sourceIdx, targetIdx);

			CompressedDijkstraRouter.Result result = router.shortestPath(graph, sourceIdx, targetIdx, vehicle);

			if (result == null || result.path() == null || result.path().length == 0) {
				return createErrorResponse("No route found");
			}

			int[] path = result.path();
			double cost = result.cost();

			// RoutePoints mit Distanz & Zeit vom Start
			List<RoutePoint> points = buildRoutePoints(path, graph);

			double totalDistance = points.isEmpty() ? 0 : points.get(points.size() - 1).getDistanceFromStart();
			long totalTime = points.isEmpty() ? 0 : points.get(points.size() - 1).getTimeFromStart();

			// Turn-by-Turn-Anweisungen
			NavigationService.Result navResult = new NavigationService.Result(path, cost);

			List<TurnInstruction> instructions = navigationService.buildTurnInstructions(navResult, graph);

			// Response bauen
			RouteResponse response = new RouteResponse();
			response.setStatus("ok");
			response.setError(null);
			response.setPoints(points);
			response.setTotalDistance(totalDistance);
			response.setTotalTime(totalTime);
			response.setVehicleType(vehicle.getName());
			response.setTurnInstructions(instructions);
			response.setGeometry(RouteResponse.createGeoJsonGeometry(points));

			return response;

		} catch (Exception e) {
			log.error("Routing error", e);
			return createErrorResponse("Internal error: " + e.getMessage());
		}
	}

	private File resolveChunksDir() {
		try {
			File code = new File(com.patriot.nav.routing.CompressedGraphBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			File dir = code.isDirectory() ? code : code.getParentFile();
			return new File(dir, "graph-chunks");
		} catch (Exception e) {
			return new File("graph-chunks");
		}
	}

	private List<RoutePoint> buildRoutePoints(int[] path, CompressedGraph graph) {
		List<RoutePoint> points = new ArrayList<>();
		double distFromStart = 0;
		long timeFromStart = 0;

		for (int i = 0; i < path.length; i++) {
			int nodeIdx = path[i];
			double lat = graph.lat(nodeIdx);
			double lon = graph.lon(nodeIdx);

			if (i > 0) {
				int prevIdx = path[i - 1];
				int edge = findEdge(graph, prevIdx, nodeIdx);
				if (edge != -1) {
					distFromStart += graph.distance(edge);
					timeFromStart += Math.round(graph.weight(edge));
				} else {
					// Fallback: Haversine, falls Edge nicht gefunden wird
					distFromStart += distanceMeters(graph.lat(prevIdx), graph.lon(prevIdx), lat, lon);
				}
			}

			points.add(new RoutePoint(lat, lon, distFromStart, timeFromStart));
		}

		return points;
	}

	private int findEdge(CompressedGraph g, int from, int to) {
		int edge = g.firstEdge(from);
		while (edge != -1) {
			if (g.edgeTo(edge) == to)
				return edge;
			edge = g.edgeNext(edge);
		}
		return -1;
	}

	private VehicleProfile resolveVehicleProfile(VehicleProfile req) {

		// 1. Default-Profil laden
		log.debug("Resolving vehicle profile, request name={}", req == null ? null : req.getName());
		VehicleProfile base = vehicleProfileService
			.getProfile(req == null || req.getName() == null ? "car" : req.getName());
		log.debug("Loaded base vehicle profile: {} (maxSpeed={}, ignoreOneWay={})",
			base.getName(), base.getMaxSpeed(), base.getIgnoreOneWay());

		if (req == null)
			return base;

		// 2. Primitive Werte überschreiben
		if (req.getMaxSpeed() != null) {
			log.debug("Overriding maxSpeed: {} -> {}", base.getMaxSpeed(), req.getMaxSpeed());
			base.setMaxSpeed(req.getMaxSpeed());
		}

		if (req.getHeuristicWeight() != null) {
			log.debug("Overriding heuristicWeight: {} -> {}", base.getHeuristicWeight(), req.getHeuristicWeight());
			base.setHeuristicWeight(req.getHeuristicWeight());
		}

		if (req.getIgnoreOneWay() != null) {
			log.debug("Overriding ignoreOneWay: {} -> {}", base.getIgnoreOneWay(), req.getIgnoreOneWay());
			base.setIgnoreOneWay(req.getIgnoreOneWay());
		}

		// 3. Access merge or override
		if (req.getAccess() != null) {
			AccessProfile def = base.getAccess();
			AccessProfile custom = req.getAccess();

			// if custom explicitly denies all modes we assume the caller wants to
			// override rather than augment the default profile.  the previous
			// implementation always OR'd the values which made it impossible to
			// block access completely in tests.
			boolean allFalse = !custom.isMotorVehicle() && !custom.isFoot() && !custom.isBicycle()
			        && !custom.isHgv() && !custom.isBus() && !custom.isTaxi()
			        && !custom.isEmergency() && !custom.isDelivery() && !custom.isAgricultural();
			if (allFalse) {
				log.debug("Replacing access profile with custom (all false)");
				base.setAccess(custom);
			} else {
				AccessProfile merged = new AccessProfile();
				merged.setMotorVehicle(custom.isMotorVehicle() || def.isMotorVehicle());
				merged.setFoot(custom.isFoot() || def.isFoot());
				merged.setBicycle(custom.isBicycle() || def.isBicycle());
				merged.setHgv(custom.isHgv() || def.isHgv());
				merged.setBus(custom.isBus() || def.isBus());
				merged.setTaxi(custom.isTaxi() || def.isTaxi());
				merged.setEmergency(custom.isEmergency() || def.isEmergency());
				merged.setDelivery(custom.isDelivery() || def.isDelivery());
				merged.setAgricultural(custom.isAgricultural() || def.isAgricultural());
				base.setAccess(merged);
			}
		}

		// 4. Way multipliers mergen
		if (req.getWayMultipliers() != null) {
			log.debug("Merging way multipliers: overrides={}", req.getWayMultipliers());
			Map<String, Double> merged = new HashMap<>(base.getWayMultipliers());
			merged.putAll(req.getWayMultipliers()); // nur überschreiben, was gesetzt wurde
			base.setWayMultipliers(merged);
		}

		// 5. Tag weights mergen
		if (req.getTagWeights() != null) {
			log.debug("Merging tag weights: overrides={}", req.getTagWeights());
			Map<String, Double> merged = new HashMap<>(base.getTagWeights());
			merged.putAll(req.getTagWeights());
			base.setTagWeights(merged);
		}

		// 6. Blocked tags mergen
		if (req.getBlockedTags() != null) {
			log.debug("Merging blocked tags: overrides={}", req.getBlockedTags());
			Set<String> merged = new HashSet<>(base.getBlockedTags());
			merged.addAll(req.getBlockedTags());
			base.setBlockedTags(new ArrayList<>(merged));
		}
		log.debug("Resolved vehicle profile: {} (maxSpeed={}, ignoreOneWay={})", base.getName(), base.getMaxSpeed(), base.getIgnoreOneWay());
		return base;
	}

	private RouteResponse createErrorResponse(String error) {
		RouteResponse response = new RouteResponse();
		response.setStatus("error");
		response.setError(error);
		return response;
	}

	private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
		double R = 6371000.0;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return R * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
	}
}
