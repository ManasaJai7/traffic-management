import java.util.List;

/** Domain model classes. Each one mirrors a database table / concept. */

class Junction {
    final String id, name, location;
    final int x, y;
    Junction(String id, String name, String location, int x, int y) {
        this.id = id; this.name = name; this.location = location == null ? "" : location; this.x = x; this.y = y;
    }
    String label() { return id + " \u2013 " + name; }
}

class RoadSegment {
    final String from, to, name;
    int normalTime, travelTime;
    boolean closed;
    RoadSegment(String from, String to, String name, int normalTime, int travelTime, boolean closed) {
        this.from = from; this.to = to; this.name = name == null ? "" : name;
        this.normalTime = normalTime; this.travelTime = travelTime; this.closed = closed;
    }
    void closeRoad() { closed = true; }
    void reopenRoad() { closed = false; }
    boolean congested() { return !closed && travelTime > normalTime; }
    String status() { return closed ? "CLOSED" : (congested() ? "CONGESTED" : "OPEN"); }
}

class EmergencyVehicle {
    final String id, type;
    String currentJunction;
    EmergencyVehicle(String id, String type, String currentJunction) {
        this.id = id; this.type = type; this.currentJunction = currentJunction;
    }
    void updatePosition(String junction) { currentJunction = junction; }
}

class RoutingRequest {
    final long requestId;               // monotonically increasing = arrival order
    final String vehicleId, destination, severity, createdAt;
    RoutingRequest(long requestId, String vehicleId, String destination, String severity, String createdAt) {
        this.requestId = requestId; this.vehicleId = vehicleId; this.destination = destination;
        this.severity = severity; this.createdAt = createdAt;
    }
    static int rank(String severity) {
        if ("CRITICAL".equals(severity)) return 0;
        if ("HIGH".equals(severity)) return 1;
        return 2;
    }
}

class RouteResult {
    static final String FOUND = "ROUTE FOUND";
    static final String UNREACHABLE = "UNREACHABLE";
    final String vehicleId, start, destination, severity, status;
    final List<String> path;
    final int totalTime;
    RouteResult(String vehicleId, String start, String destination, String severity, String status, List<String> path, int totalTime) {
        this.vehicleId = vehicleId; this.start = start; this.destination = destination; this.severity = severity;
        this.status = status; this.path = path; this.totalTime = totalTime;
    }
    boolean found() { return FOUND.equals(status); }
    String pathText() {
        StringBuilder b = new StringBuilder();
        for (String s : path) { if (b.length() > 0) b.append(" \u2192 "); b.append(s); }
        return b.toString();
    }
    String compact() {
        return vehicleId + " | " + severity + " | " + status + " | "
            + (path.isEmpty() ? "No path" : pathText() + " | " + totalTime + " min");
    }
    public String toString() { return compact(); }
}
