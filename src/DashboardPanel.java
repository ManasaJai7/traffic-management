import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.function.Consumer;

/** Overview page: live counts from the database, the city map and the latest activity. */
class DashboardPanel extends JPanel implements Refreshable {

    /** One statistic tile. */
    private static class StatCard extends UI.Card {
        private final JLabel value = UI.label("0", Font.BOLD, 30, UI.TEXT);
        private final JLabel sub = UI.label(" ", Font.PLAIN, 11, UI.MUTED);
        StatCard(String title, Color accent) {
            super(new BorderLayout(0, 2));
            stripe(accent);
            setBorder(new EmptyBorder(14, 20, 14, 14));
            add(UI.label(title.toUpperCase(), Font.BOLD, 11, UI.MUTED), BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(sub, BorderLayout.SOUTH);
        }
        void set(long v, String subtitle) { value.setText(String.valueOf(v)); sub.setText(subtitle); }
    }

    private final TrafficController controller;
    private final NetworkPanel map;
    private final StatCard junctions = new StatCard("Junctions", UI.ACCENT);
    private final StatCard roads = new StatCard("Roads", UI.BLUE);
    private final StatCard vehicles = new StatCard("Vehicles", new Color(211, 145, 255));
    private final StatCard signals = new StatCard("Signals", UI.ACCENT);
    private final StatCard requests = new StatCard("Pending requests", UI.AMBER);
    private final StatCard events = new StatCard("Queued events", UI.RED);
    private final DefaultTableModel routeModel = UI.readOnlyModel("Time", "Vehicle", "To", "Status", "Min");
    private final DefaultTableModel eventModel = UI.readOnlyModel("Time", "Type", "Road", "Status");

    DashboardPanel(TrafficController controller, Consumer<String> navigate) {
        super(new BorderLayout(0, 18));
        this.controller = controller;
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(UI.pageHeader("Control Room Dashboard", "Live overview of the road network, emergency vehicles and traffic queues."), BorderLayout.WEST);
        JButton go = UI.button("Open Routing Console", UI.ACCENT);
        JPanel goBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        goBox.setOpaque(false);
        goBox.add(go);
        go.addActionListener(e -> navigate.accept("Routing Console"));
        head.add(goBox, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 6, 14, 0));
        stats.setOpaque(false);
        for (StatCard s : new StatCard[]{junctions, roads, vehicles, signals, requests, events}) stats.add(s);

        map = new NetworkPanel(controller);
        UI.Card mapCard = new UI.Card(new BorderLayout(0, 8));
        mapCard.setBorder(new EmptyBorder(14, 14, 10, 14));
        JPanel mapHead = new JPanel(new BorderLayout());
        mapHead.setOpaque(false);
        mapHead.add(UI.label("City road network", Font.BOLD, 14, UI.TEXT), BorderLayout.WEST);
        mapHead.add(UI.label("hover a junction for details", Font.PLAIN, 11, UI.MUTED), BorderLayout.EAST);
        mapCard.add(mapHead, BorderLayout.NORTH);
        mapCard.add(map, BorderLayout.CENTER);
        mapCard.add(UI.legend(), BorderLayout.SOUTH);

        JPanel right = new JPanel(new GridLayout(2, 1, 0, 16));
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(440, 10));
        right.add(listCard("Recent route results", routeModel));
        right.add(listCard("Recent traffic events", eventModel));

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        body.add(mapCard, BorderLayout.CENTER);
        body.add(right, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(body, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private JComponent listCard(String title, DefaultTableModel model) {
        UI.Card c = new UI.Card(new BorderLayout(0, 10));
        c.setBorder(new EmptyBorder(14, 14, 14, 14));
        c.add(UI.label(title, Font.BOLD, 14, UI.TEXT), BorderLayout.NORTH);
        JTable t = UI.table(model);
        t.setRowHeight(30);
        t.setRowSelectionAllowed(false);
        c.add(UI.scroll(t), BorderLayout.CENTER);
        return c;
    }

    @Override public void refresh() {
        long closed = Db.scalar("SELECT COUNT(*) FROM Road WHERE closed = 1");
        long congested = Db.scalar("SELECT COUNT(*) FROM Road WHERE closed = 0 AND travel_time > normal_time");
        junctions.set(Db.scalar("SELECT COUNT(*) FROM Junction"), "intersections");
        roads.set(Db.scalar("SELECT COUNT(*) FROM Road"), closed + " closed, " + congested + " congested");
        vehicles.set(Db.scalar("SELECT COUNT(*) FROM Vehicle"), "ready to dispatch");
        signals.set(Db.scalar("SELECT COUNT(*) FROM TrafficSignal"), Db.scalar("SELECT COUNT(*) FROM TrafficSignal WHERE signal_status = 'RED'") + " showing red");
        requests.set(Db.scalar("SELECT COUNT(*) FROM RoutingRequest WHERE status = 'PENDING'"), "waiting for routing");
        events.set(Db.scalar("SELECT COUNT(*) FROM TrafficEvent WHERE status = 'PENDING'"), "waiting to apply");

        routeModel.setRowCount(0);
        for (Object[] r : Db.rows("SELECT substr(created_at, 12, 5), vehicle_id, destination, status, total_time FROM RouteHistory ORDER BY history_id DESC LIMIT 8"))
            routeModel.addRow(r);
        eventModel.setRowCount(0);
        for (Object[] r : Db.rows("SELECT substr(COALESCE(applied_at, created_at), 12, 5), event_type, from_junction || ' \u2192 ' || to_junction, status "
                                + "FROM TrafficEvent ORDER BY event_id DESC LIMIT 8"))
            eventModel.addRow(r);
        map.setRoutes(controller.lastResults());
    }
}
