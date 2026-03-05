package com.patriot.nav.routing;

import java.util.Arrays;
import java.util.Map;
import java.util.PriorityQueue;

import com.patriot.nav.model.VehicleProfile;
import com.patriot.nav.model.AccessProfile;

public class CompressedDijkstraRouter {

	public record Result(int[] path, double cost) {
	}

	private static class QueueNode implements Comparable<QueueNode> {
		final int node;
		final double dist;

		QueueNode(int node, double dist) {
			this.node = node;
			this.dist = dist;
		}

		@Override
		public int compareTo(QueueNode o) {
			return Double.compare(this.dist, o.dist);
		}
	}

	/**
	 * Determines whether the given edge in the graph may be traversed by the
	 * specified vehicle profile.  This mirrors the checks performed during
	 * routing and can be reused by callers that need to validate node access.
	 */
	public boolean isEdgeAllowed(CompressedGraph g, int edge, VehicleProfile vehicle) {

		String highway = g.wayType(edge); // z.B. "motorway", "residential", "footway"
		Map<String, String> tags = g.tags(edge);
		int flags = g.flags(edge);

		AccessProfile ap = vehicle.getAccess();
		// if the profile disallows every mode there is nothing we can
		// possibly traverse.  this mirrors earlier behaviour where a
		// completely empty profile caused rejection.
		if (!ap.isMotorVehicle() && !ap.isBicycle() && !ap.isFoot()
		        && !ap.isHgv() && !ap.isBus() && !ap.isTaxi()
		        && !ap.isEmergency() && !ap.isDelivery() && !ap.isAgricultural()) {
			return false;
		}

		// 1. Highway-Typ erlaubt? (way multipliers define which types the profile
		// is willing to consider at all)
		if (!vehicle.getWayMultipliers().containsKey(highway)) {
			return false;
		}

		// 2. Wenn der Graph bereits per Flags bestimmte Modi verbietet, respektiere
		// das.  Ein Flag-Wert von 0 heißt "unbekannt", dann erlauben wir alles und
		// lassen das Profil entscheiden weiter unten.
		if (flags != 0) {
			if (ap.isMotorVehicle() && (flags & EdgeFlags.ACCESS_MOTOR_VEHICLE) == 0)
				return false;
			if (ap.isBicycle() && (flags & EdgeFlags.ACCESS_BICYCLE) == 0)
				return false;
			if (ap.isFoot() && (flags & EdgeFlags.ACCESS_FOOT) == 0)
				return false;
			if (ap.isHgv() && (flags & EdgeFlags.ACCESS_HGV) == 0)
				return false;
			if (ap.isBus() && (flags & EdgeFlags.ACCESS_BUS) == 0)
				return false;
			if (ap.isTaxi() && (flags & EdgeFlags.ACCESS_TAXI) == 0)
				return false;
			if (ap.isEmergency() && (flags & EdgeFlags.ACCESS_EMERGENCY) == 0)
				return false;
			if (ap.isDelivery() && (flags & EdgeFlags.ACCESS_DELIVERY) == 0)
				return false;
			if (ap.isAgricultural() && (flags & EdgeFlags.ACCESS_AGRICULTURAL) == 0)
				return false;
		}

		// 3. Blocked Tags prüfen (z.B. surface=gravel, access=private aus vehicle.json)
		if (vehicle.getBlockedTags() != null) {
			for (String blocked : vehicle.getBlockedTags()) {
				String[] kv = blocked.split("=");
				if (kv.length == 2) {
					String key = kv[0];
					String val = kv[1];
					if (val.equals(tags.get(key))) {
						return false;
					}
				}
			}
		}

		// 4. Zusätzliche access-restringierende Tags im OSM selbst
		String accessTag = tags.get("access");
		if ("no".equals(accessTag) || "private".equals(accessTag) || "restricted".equals(accessTag))
			return false;
		String motTag = tags.get("motor_vehicle");
		if ("no".equals(motTag) && ap.isMotorVehicle())
			return false;
		String bicTag = tags.get("bicycle");
		if ("no".equals(bicTag) && ap.isBicycle())
			return false;
		String footTag = tags.get("foot");
		if ("no".equals(footTag) && ap.isFoot())
			return false;

		return true;
	}

	/**
	 * Calculate the raw weight of a single edge for the given vehicle.  The
	 * method is exposed publicly to make it easy to write unit tests and also
	 * to allow callers to peek at the underlying cost for diagnostics.  In the
	 * normal routing logic this method is called internally; users normally
	 * interact with {@link #shortestPath}.
	 */
	public double computeWeight(CompressedGraph g, int edge, VehicleProfile vehicle) {

		String highway = g.wayType(edge);
		Map<String, String> tags = g.tags(edge);

		// 1. Basiszeit aus Graph (berechnet beim Import, kann aber noch
		// mit zusätzlichen Informationen überschrieben werden).
		double timeSec = g.weight(edge);
		double dist = g.distance(edge);

		// 2. Maxspeed-Beschränkung aus OSM-Tag
		String maxspeed = tags.get("maxspeed");
		if (maxspeed != null) {
			try {
				double parsed = Double.parseDouble(maxspeed.replaceAll("[^0-9.]", ""));
				// parsed ist in km/h
				double limitMs = parsed / 3.6;
				// lower speed limit means more time, never less
				timeSec = Math.max(timeSec, dist / limitMs);
			} catch (NumberFormatException ignored) {
			}
		}

		// 3. Fahrzeug-Maximum aus Profil (kann kleiner sein als die erlaubte
		// Geschwindigkeit).  Wir dürfen nicht schneller sein als das Profil erlaubt.
		double profLimitMs = vehicle.getMaxSpeed() / 3.6;
		timeSec = Math.max(timeSec, dist / profLimitMs);

		// 4. Multiplier aus vehicle.json
		Double mult = vehicle.getWayMultipliers().get(highway);
		if (mult == null)
			mult = 1.0;
		timeSec *= mult;

		// 5. Tag-Weights (z.B. surface=gravel, incline, tunnel etc.)
		if (vehicle.getTagWeights() != null) {
			for (var entry : vehicle.getTagWeights().entrySet()) {
				String[] kv = entry.getKey().split("=");
				if (kv.length == 2) {
					String key = kv[0];
					String val = kv[1];
					if (val.equals(tags.get(key))) {
						timeSec *= entry.getValue();
					}
				}
			}
		}

		return timeSec;
	}


	/**
	 * Returns true when the specified node has at least one outgoing edge that
	 * is permitted for the given vehicle profile.  Used for validating that a
	 * start/end node is actually usable before attempting to route.
	 */
	public boolean hasAccessibleEdge(CompressedGraph g, int node, VehicleProfile vehicle) {
		int edge = g.firstEdge(node);
		while (edge != -1) {
			if (isEdgeAllowed(g, edge, vehicle)) {
				return true;
			}
			edge = g.edgeNext(edge);
		}
		return false;
	}

	public Result shortestPath(CompressedGraph g, int source, int target, VehicleProfile vehicle) {
		int n = g.nodeCount();
		double[] dist = new double[n];
		int[] prev = new int[n];
		Arrays.fill(dist, Double.POSITIVE_INFINITY);
		Arrays.fill(prev, -1);

		PriorityQueue<QueueNode> pq = new PriorityQueue<>();
		dist[source] = 0.0;
		pq.add(new QueueNode(source, 0.0));

		while (!pq.isEmpty()) {
			QueueNode cur = pq.poll();
			if (cur.dist > dist[cur.node])
				continue;
			if (cur.node == target)
				break;

			int edge = g.firstEdge(cur.node);
			while (edge != -1) {

				// 1. One-way prüfen
				if (!vehicle.getIgnoreOneWay() && g.oneWay(edge)) {
					// forward/backward ist bereits im Graph korrekt angelegt
				}

				// 2. Fahrzeugprofil entscheidet, ob Kante erlaubt ist
				if (!isEdgeAllowed(g, edge, vehicle)) {
					edge = g.edgeNext(edge);
					continue;
				}

				// 3. Gewicht berechnen
				double w = computeWeight(g, edge, vehicle);
				if (w == Double.POSITIVE_INFINITY) {
					edge = g.edgeNext(edge);
					continue;
				}

				int to = g.edgeTo(edge);
				double nd = cur.dist + w;

				if (nd < dist[to]) {
					dist[to] = nd;
					prev[to] = cur.node;
					pq.add(new QueueNode(to, nd));
				}

				edge = g.edgeNext(edge);
			}
		}

		if (dist[target] == Double.POSITIVE_INFINITY) {
			return null;
		}

		int count = 0;
		for (int v = target; v != -1; v = prev[v])
			count++;
		int[] path = new int[count];
		int idx = count - 1;
		for (int v = target; v != -1; v = prev[v]) {
			path[idx--] = v;
		}

		return new Result(path, dist[target]);
	}

}
