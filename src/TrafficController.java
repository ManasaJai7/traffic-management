import java.util.*;
import java.util.List;

/**
 * Application brain. The MySQL database is the source of truth; this class keeps an in-memory copy of the
 * road graph (for fast routing and map drawing) and writes every change back to the database.
 */
class TrafficController {
    static final String[] SEVERITIES = {"CRITICAL", "HIGH", "NORMAL"};
    static final String[] EVENT_TYPES = {"Road Closure", "Congestion Update", "Reopen Road"};
    static final String[] VEHICLE_TYPES = {"Ambulance", "Fire Engine", "Rescue", "Police"};

    final Map<String, Junction> junctions = new LinkedHashMap<String, Junction>();
    final Map<String, RoadSegment> roads = new LinkedHashMap<String, RoadSegment>();
    final Map<String, EmergencyVehicle> vehicles = new LinkedHashMap<String, EmergencyVehicle>();
    final Map<String, String> signalStatus = new HashMap<String, String>();   // junction id -> first signal's status
    private final Map<String, List<RoadSegment>> adjacency = new HashMap<String, List<RoadSegment>>();
    private final RoutePlanner planner = new RoutePlanner();
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private List<RouteResult> lastResults = new ArrayList<RouteResult>();

    TrafficController() { reload(); }

    // ---------------------------------------------------------------- loading / change notification

    /** Re-reads junctions, roads, vehicles and signals from the database. */
    void reload() {
        junctions.clear(); roads.clear(); vehicles.clear(); signalStatus.clear(); adjacency.clear();
        for (Junction j : Db.query("SELECT junction_id, junction_name, location, x, y FROM Junction ORDER BY junction_id",
                rs -> new Junction(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5))))
            junctions.put(j.id, j);
        for (RoadSegment r : Db.query("SELECT from_junction, to_junction, road_name, normal_time, travel_time, closed FROM Road ORDER BY from_junction, to_junction",
                rs -> new RoadSegment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6) == 1))) {
            roads.put(key(r.from, r.to), r);
            List<RoadSegment> out = adjacency.get(r.from);
            if (out == null) { out = new ArrayList<RoadSegment>(); adjacency.put(r.from, out); }
            out.add(r);
        }
        for (EmergencyVehicle v : Db.query("SELECT vehicle_id, vehicle_type, current_junction FROM Vehicle ORDER BY vehicle_id",
                rs -> new EmergencyVehicle(rs.getString(1), rs.getString(2), rs.getString(3))))
            vehicles.put(v.id, v);
        for (Object[] s : Db.rows("SELECT junction_id, signal_status FROM TrafficSignal ORDER BY signal_id"))
            if (!signalStatus.containsKey((String) s[0])) signalStatus.put((String) s[0], (String) s[1]);
    }

    void addChangeListener(Runnable r) { listeners.add(r); }

    private void notifyChanged() { for (Runnable r : new ArrayList<Runnable>(listeners)) r.run(); }

    /** Call after any database change: reloads memory and tells every screen to redraw. */
    void dataChanged() { reload(); notifyChanged(); }

    // ---------------------------------------------------------------- graph helpers

    static String key(String from, String to) { return from + "->" + to; }

    RoadSegment road(String from, String to) {
        RoadSegment r = roads.get(key(from, to));
        if (r == null) throw new IllegalArgumentException("No directed road from " + from + " to " + to + ". Select a valid direction.");
        return r;
    }

    List<RoadSegment> outgoing(String from) {
        List<RoadSegment> l = adjacency.get(from);
        return l == null ? Collections.<RoadSegment>emptyList() : l;
    }

    List<String> junctionIds() { return new ArrayList<String>(junctions.keySet()); }
    List<String> vehicleIds() { return new ArrayList<String>(vehicles.keySet()); }
    int vehicleIndex(String id) { return new ArrayList<String>(vehicles.keySet()).indexOf(id); }
    List<RouteResult> lastResults() { return lastResults; }

    // ---------------------------------------------------------------- routing requests (priority queue)

    RoutingRequest addRequest(String vehicleId, String destination, String severity) {
        EmergencyVehicle v = vehicles.get(vehicleId);
        if (v == null) throw new IllegalArgumentException("Select a valid emergency vehicle.");
        if (!junctions.containsKey(destination)) throw new IllegalArgumentException("Select a valid destination junction.");
        if (v.currentJunction.equals(destination)) throw new IllegalArgumentException(vehicleId + " is already at " + destination + ".");
        long id = Db.insert("INSERT INTO RoutingRequest(vehicle_id, destination, severity) VALUES (?,?,?)", vehicleId, destination, severity);
        notifyChanged();
        return new RoutingRequest(id, vehicleId, destination, severity, "");
    }

    Comparator<RoutingRequest> requestComparator() {
        return Comparator.comparingInt((RoutingRequest r) -> RoutingRequest.rank(r.severity))
                         .thenComparingLong(r -> r.requestId);
    }

    private List<RoutingRequest> loadPending() {
        return Db.query("SELECT request_id, vehicle_id, destination, severity, created_at FROM RoutingRequest WHERE status = 'PENDING'",
            rs -> new RoutingRequest(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    /** Pending requests in processing order: severity first, then arrival order. */
    List<RoutingRequest> pendingRequests() {
        PriorityQueue<RoutingRequest> pq = new PriorityQueue<RoutingRequest>(11, requestComparator());
        pq.addAll(loadPending());
        List<RoutingRequest> ordered = new ArrayList<RoutingRequest>();
        while (!pq.isEmpty()) ordered.add(pq.poll());
        return ordered;
    }

    // ---------------------------------------------------------------- traffic events (FIFO queue)

    TrafficEvent queueEvent(String from, String to, String typeLabel, int newTime) {
        road(from, to);   // validates direction
        String code = TrafficEvent.codeForLabel(typeLabel);
        if ("CONGESTION".equals(code) && newTime <= 0) throw new IllegalArgumentException("Travel time must be greater than zero.");
        long id = Db.insert("INSERT INTO TrafficEvent(event_type, from_junction, to_junction, new_time) VALUES (?,?,?,?)",
                            code, from, to, "CONGESTION".equals(code) ? (Object) Integer.valueOf(newTime) : null);
        notifyChanged();
        return TrafficEvent.create(id, code, from, to, newTime, "");
    }

    /** Pending events oldest-first (FIFO). */
    List<TrafficEvent> pendingEvents() {
        return Db.query("SELECT event_id, event_type, from_junction, to_junction, COALESCE(new_time, 0), created_at "
                      + "FROM TrafficEvent WHERE status = 'PENDING' ORDER BY event_id",
            rs -> TrafficEvent.create(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6)));
    }

    // ---------------------------------------------------------------- the main workflow

    /** 1) apply queued events FIFO  2) route pending requests by priority  3) save everything in one transaction. */
    List<RouteResult> processAll() {
        ArrayDeque<TrafficEvent> queue = new ArrayDeque<TrafficEvent>(pendingEvents());
        final List<TrafficEvent> applied = new ArrayList<TrafficEvent>();
        while (!queue.isEmpty()) {
            TrafficEvent e = queue.pollFirst();
            e.applyToNetwork(this);
            applied.add(e);
        }

        PriorityQueue<RoutingRequest> pq = new PriorityQueue<RoutingRequest>(11, requestComparator());
        pq.addAll(loadPending());
        final List<RoutingRequest> done = new ArrayList<RoutingRequest>();
        final List<RouteResult> results = new ArrayList<RouteResult>();
        while (!pq.isEmpty()) {
            RoutingRequest r = pq.poll();
            results.add(planner.findFastestRoute(this, r));
            done.add(r);
        }

        try {
            Db.transaction(c -> {
                for (TrafficEvent e : applied) {
                    RoadSegment r = road(e.from, e.to);
                    Db.update(c, "UPDATE Road SET travel_time = ?, closed = ? WHERE from_junction = ? AND to_junction = ?",
                              r.travelTime, r.closed ? 1 : 0, e.from, e.to);
                    Db.update(c, "UPDATE TrafficEvent SET status = 'APPLIED', applied_at = CURRENT_TIMESTAMP WHERE event_id = ?", e.id);
                }
                for (RouteResult rr : results)
                    Db.update(c, "INSERT INTO RouteHistory(vehicle_id, start_junction, destination, severity, status, path, total_time) VALUES (?,?,?,?,?,?,?)",
                              rr.vehicleId, rr.start, rr.destination, rr.severity, rr.status, rr.pathText(), rr.totalTime);
                for (RoutingRequest r : done)
                    Db.update(c, "UPDATE RoutingRequest SET status = 'DONE' WHERE request_id = ?", r.requestId);
            });
        } catch (RuntimeException ex) {
            reload();            // memory and database must agree again
            notifyChanged();
            throw ex;
        }
        lastResults = results;
        notifyChanged();
        return results;
    }

    void moveVehicle(String vehicleId, String junctionId) {
        if (!vehicles.containsKey(vehicleId) || !junctions.containsKey(junctionId))
            throw new IllegalArgumentException("Invalid vehicle or junction.");
        Db.update("UPDATE Vehicle SET current_junction = ? WHERE vehicle_id = ?", junctionId, vehicleId);
        dataChanged();
    }
}
