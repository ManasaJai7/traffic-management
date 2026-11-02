import java.util.*;
import java.util.List;

/** Dijkstra shortest-time routing with a min-heap (PriorityQueue). Closed roads are skipped. */
class RoutePlanner {

    RouteResult findFastestRoute(TrafficController c, RoutingRequest request) {
        EmergencyVehicle v = c.vehicles.get(request.vehicleId);
        if (v == null || !c.junctions.containsKey(request.destination))
            return unreachable(request, v == null ? "?" : v.currentJunction);
        String start = v.currentJunction;

        Map<String, Integer> dist = new HashMap<String, Integer>();
        Map<String, String> prev = new HashMap<String, String>();
        PriorityQueue<NodeDistance> heap = new PriorityQueue<NodeDistance>(
            Comparator.comparingInt((NodeDistance n) -> n.distance).thenComparing(n -> n.node));
        for (String j : c.junctions.keySet()) dist.put(j, Integer.MAX_VALUE);
        dist.put(start, 0);
        heap.add(new NodeDistance(start, 0));

        while (!heap.isEmpty()) {
            NodeDistance cur = heap.poll();
            if (cur.distance != dist.get(cur.node)) continue;          // stale heap entry
            if (cur.node.equals(request.destination)) break;
            for (RoadSegment r : c.outgoing(cur.node)) {
                if (r.closed) continue;
                int alt = cur.distance + r.travelTime;
                if (alt < dist.get(r.to)) {
                    dist.put(r.to, alt);
                    prev.put(r.to, cur.node);
                    heap.add(new NodeDistance(r.to, alt));
                }
            }
        }
        Integer total = dist.get(request.destination);
        if (total == null || total == Integer.MAX_VALUE) return unreachable(request, start);

        LinkedList<String> path = new LinkedList<String>();
        String at = request.destination;
        path.addFirst(at);
        while (!at.equals(start)) {
            at = prev.get(at);
            if (at == null) return unreachable(request, start);
            path.addFirst(at);
        }
        return new RouteResult(request.vehicleId, start, request.destination, request.severity, RouteResult.FOUND, path, total);
    }

    private RouteResult unreachable(RoutingRequest r, String start) {
        return new RouteResult(r.vehicleId, start, r.destination, r.severity, RouteResult.UNREACHABLE, Collections.<String>emptyList(), 0);
    }

    private static class NodeDistance {
        final String node; final int distance;
        NodeDistance(String node, int distance) { this.node = node; this.distance = distance; }
    }
}
