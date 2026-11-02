import java.util.List;

/** Back-end tests (no GUI): run with  java -cp "out;lib\*" CoreSmokeTest  - uses a throw-away database file. */
public class CoreSmokeTest {
    static int passed = 0;

    static void check(boolean ok, String name) {
        if (!ok) throw new RuntimeException("FAILED: " + name);
        passed++;
        System.out.println("  ok  " + name);
    }

    public static void main(String[] args) throws Exception {
        DatabaseSetup.init();
        DatabaseSetup.resetDemoData();

        TrafficController c = new TrafficController();
        check(c.junctions.size() == 12 && c.roads.size() == 34 && c.vehicles.size() == 4, "demo data seeded from database");

        // 1. priority queue: CRITICAL before NORMAL even though it arrived later; ties keep arrival order
        c.addRequest("AMB-02", "J5", "NORMAL");
        c.addRequest("FIRE-01", "J1", "CRITICAL");
        c.addRequest("RES-01", "J12", "NORMAL");
        List<RoutingRequest> order = c.pendingRequests();
        check(order.get(0).severity.equals("CRITICAL") && order.get(1).vehicleId.equals("AMB-02") && order.get(2).vehicleId.equals("RES-01"),
              "severity ordering, then arrival order");

        // 2. FIFO events: close then update congestion, applied in queue order
        c.queueEvent("J1", "J2", "Road Closure", 0);
        c.queueEvent("J5", "J6", "Congestion Update", 20);
        List<TrafficEvent> ev = c.pendingEvents();
        check(ev.size() == 2 && ev.get(0).id < ev.get(1).id && ev.get(0) instanceof ClosureEvent, "events are FIFO");

        List<RouteResult> res = c.processAll();
        check(res.size() == 3 && res.get(0).vehicleId.equals("FIRE-01"), "requests processed in priority order");
        check(c.road("J1", "J2").closed && c.road("J5", "J6").travelTime == 20, "events applied to the road network");
        check(res.get(0).found() && res.get(0).path.get(0).equals("J4") && res.get(0).path.get(res.get(0).path.size() - 1).equals("J1"),
              "FIRE-01 routed J4 -> J1 avoiding the closed road");

        // 3. persistence: a brand-new controller sees the saved state
        TrafficController fresh = new TrafficController();
        check(fresh.road("J1", "J2").closed && fresh.road("J5", "J6").travelTime == 20, "state survives a restart (read back from MySQL)");
        check(Db.scalar("SELECT COUNT(*) FROM RouteHistory") == 3, "route history saved");
        check(Db.scalar("SELECT COUNT(*) FROM TrafficEvent WHERE status = 'APPLIED'") == 2, "events marked APPLIED");
        check(c.pendingRequests().isEmpty() && c.pendingEvents().isEmpty(), "queues empty after processing");

        // 4. unreachable: cut every road out of J9's region
        c.queueEvent("J9", "J5", "Road Closure", 0);
        c.queueEvent("J9", "J10", "Road Closure", 0);
        c.addRequest("RES-01", "J12", "HIGH");
        RouteResult r = c.processAll().get(0);
        check(r.status.equals(RouteResult.UNREACHABLE) && r.path.isEmpty(), "UNREACHABLE reported when every exit is closed");

        // 5. reopen
        c.queueEvent("J9", "J10", "Reopen Road", 0);
        c.addRequest("RES-01", "J12", "HIGH");
        check(c.processAll().get(0).found(), "reopened road is usable again");

        // 6. validation and constraints
        try { c.addRequest("NOPE", "J1", "HIGH"); check(false, "invalid vehicle rejected"); }
        catch (IllegalArgumentException e) { check(true, "invalid vehicle rejected"); }
        try { c.queueEvent("J1", "J12", "Road Closure", 0); check(false, "non-existent road rejected"); }
        catch (IllegalArgumentException e) { check(true, "non-existent road rejected"); }
        try { c.queueEvent("J1", "J2", "Congestion Update", 0); check(false, "zero travel time rejected"); }
        catch (IllegalArgumentException e) { check(true, "zero travel time rejected"); }
        try { Db.update("DELETE FROM Junction WHERE junction_id = 'J1'"); check(false, "FK blocks deleting a junction with vehicles"); }
        catch (Db.DataAccessException e) { check(true, "foreign key blocks deleting a junction that still has a vehicle"); }
        try { Db.update("INSERT INTO Road(from_junction,to_junction,normal_time,travel_time) VALUES ('J1','J2',0,0)"); check(false, "CHECK"); }
        catch (Db.DataAccessException e) { check(true, "CHECK constraint blocks a road that already exists / zero time"); }

        // 7. cascade
        Db.update("DELETE FROM Vehicle WHERE vehicle_id = 'AMB-01'");
        Db.update("DELETE FROM Junction WHERE junction_id = 'J1'");
        c.dataChanged();
        check(c.junctions.size() == 11 && c.roads.size() == 30, "deleting a junction cascades to its roads");

        // 8. reset
        DatabaseSetup.resetDemoData();
        c.dataChanged();
        check(c.junctions.size() == 12 && Db.scalar("SELECT COUNT(*) FROM RouteHistory") == 0, "reset restores the demo city");

        System.out.println("PASS: " + passed + " checks");
    }
}
