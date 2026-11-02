/** Abstract traffic event. Subclasses override applyToNetwork() - polymorphism. Events wait in a FIFO queue (stored in the TrafficEvent table). */
abstract class TrafficEvent {
    final long id;
    final String from, to, createdAt;

    TrafficEvent(long id, String from, String to, String createdAt) {
        this.id = id; this.from = from; this.to = to; this.createdAt = createdAt == null ? "" : createdAt;
    }

    abstract void applyToNetwork(TrafficController controller);
    abstract String description();
    abstract String typeCode();     // value stored in the database
    abstract String typeLabel();    // label shown in the UI
    Integer newTime() { return null; }

    static String codeForLabel(String label) {
        if ("Road Closure".equals(label)) return "CLOSURE";
        if ("Reopen Road".equals(label)) return "REOPEN";
        return "CONGESTION";
    }

    static TrafficEvent create(long id, String code, String from, String to, int newTime, String createdAt) {
        if ("CLOSURE".equals(code)) return new ClosureEvent(id, from, to, createdAt);
        if ("REOPEN".equals(code)) return new ReopenEvent(id, from, to, createdAt);
        return new CongestionEvent(id, from, to, newTime, createdAt);
    }
}

class ClosureEvent extends TrafficEvent {
    ClosureEvent(long id, String from, String to, String createdAt) { super(id, from, to, createdAt); }
    void applyToNetwork(TrafficController c) { c.road(from, to).closeRoad(); }
    String description() { return "CLOSE " + from + " \u2192 " + to; }
    String typeCode() { return "CLOSURE"; }
    String typeLabel() { return "Road Closure"; }
}

class ReopenEvent extends TrafficEvent {
    ReopenEvent(long id, String from, String to, String createdAt) { super(id, from, to, createdAt); }
    void applyToNetwork(TrafficController c) { c.road(from, to).reopenRoad(); }
    String description() { return "REOPEN " + from + " \u2192 " + to; }
    String typeCode() { return "REOPEN"; }
    String typeLabel() { return "Reopen Road"; }
}

class CongestionEvent extends TrafficEvent {
    final int newTime;
    CongestionEvent(long id, String from, String to, int newTime, String createdAt) { super(id, from, to, createdAt); this.newTime = newTime; }
    void applyToNetwork(TrafficController c) { c.road(from, to).travelTime = newTime; }
    String description() { return "CONGESTION " + from + " \u2192 " + to + " = " + newTime + " min"; }
    String typeCode() { return "CONGESTION"; }
    String typeLabel() { return "Congestion Update"; }
    @Override Integer newTime() { return newTime; }
}
