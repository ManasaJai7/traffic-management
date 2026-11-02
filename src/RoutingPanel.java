import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** The simulation screen: queue requests (priority queue), queue traffic events (FIFO), then process everything. */
class RoutingPanel extends JPanel implements Refreshable {
    private final TrafficController controller;
    private final NetworkPanel map;
    private final JComboBox<String> vehicleBox = UI.combo(), destBox = UI.combo(), severityBox = UI.combo(TrafficController.SEVERITIES);
    private final JComboBox<String> fromBox = UI.combo(), toBox = UI.combo(), typeBox = UI.combo(TrafficController.EVENT_TYPES), moveBox = UI.combo();
    private final JTextField timeF = UI.field(6);
    private final DefaultTableModel reqModel = UI.readOnlyModel("Order", "Request", "Severity", "Vehicle", "From", "To");
    private final DefaultTableModel evModel = UI.readOnlyModel("Order", "Event", "Type", "Road", "Detail");
    private final DefaultTableModel resModel = UI.readOnlyModel("Time", "Vehicle", "Severity", "Status", "Route", "Min");
    private final JTable resTable = UI.table(resModel);
    private final List<RouteResult> shown = new ArrayList<RouteResult>();
    private final JTextArea logArea = new JTextArea();
    private final UI.Tabs tabs = new UI.Tabs();

    RoutingPanel(TrafficController controller) {
        super(new BorderLayout(0, 16));
        this.controller = controller;
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 24, 28));
        add(UI.pageHeader("Routing Console", "Queue emergency requests and traffic events, then process them to get the fastest route."), BorderLayout.NORTH);

        // ---------------- left: controls
        JPanel f = new UI.ScrollForm();
        f.add(UI.sectionLabel("1   ROUTING REQUEST"));
        UI.formRow(f, "EMERGENCY VEHICLE", vehicleBox);
        UI.formPair(f, "DESTINATION", destBox, "SEVERITY", severityBox);
        f.add(Box.createVerticalStrut(10));
        f.add(wide(UI.button("Add request", UI.ACCENT), e -> addRequest()));
        f.add(UI.sectionLabel("2   TRAFFIC EVENT  (DIRECTED ROAD)"));
        UI.formPair(f, "FROM", fromBox, "TO", toBox);
        UI.formPair(f, "EVENT TYPE", typeBox, "NEW TIME (MIN)", timeF);
        f.add(Box.createVerticalStrut(10));
        f.add(wide(UI.button("Queue traffic event", UI.BLUE), e -> queueEvent()));
        f.add(UI.sectionLabel("3   RUN"));
        f.add(Box.createVerticalStrut(8));
        f.add(wide(UI.button("Process events & route requests", UI.AMBER), e -> process()));
        f.add(UI.sectionLabel("VEHICLE POSITION"));
        UI.formRow(f, "MOVE SELECTED VEHICLE TO", moveBox);
        f.add(Box.createVerticalStrut(10));
        f.add(wide(UI.button("Update position", UI.GRAY), e -> move()));
        JLabel hint = UI.label("<html><body style='width:270px'>Events apply first-in-first-out. Requests are routed CRITICAL, then HIGH, then NORMAL; ties by arrival.</body></html>", Font.PLAIN, 11, UI.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.add(Box.createVerticalStrut(14));
        f.add(hint);

        UI.lock(timeF, true);
        timeF.setText("12");
        typeBox.addActionListener(e -> UI.lock(timeF, !"Congestion Update".equals(typeBox.getSelectedItem())));

        UI.Card formCard = new UI.Card(new BorderLayout());
        formCard.setPreferredSize(new Dimension(350, 10));
        formCard.setBorder(new EmptyBorder(6, 18, 12, 18));
        JScrollPane fs = new JScrollPane(f);
        fs.setBorder(null);
        fs.setOpaque(false);
        fs.getViewport().setOpaque(false);
        fs.getVerticalScrollBar().setUI(new UI.DarkScrollBarUI());
        fs.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        fs.getVerticalScrollBar().setUnitIncrement(18);
        fs.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formCard.add(fs, BorderLayout.CENTER);

        // ---------------- centre: map + tables
        map = new NetworkPanel(controller);
        UI.Card mapCard = new UI.Card(new BorderLayout(0, 8));
        mapCard.setBorder(new EmptyBorder(14, 14, 10, 14));
        mapCard.add(UI.label("Network & fastest routes", Font.BOLD, 14, UI.TEXT), BorderLayout.NORTH);
        mapCard.add(map, BorderLayout.CENTER);
        mapCard.add(UI.legend(), BorderLayout.SOUTH);

        tabs.addTab("Pending Requests", tableScroll(reqModel));
        tabs.addTab("Queued Events", tableScroll(evModel));
        JScrollPane resScroll = UI.scroll(resTable);
        tabs.addTab("Latest Results", resScroll);
        logArea.setEditable(false);
        logArea.setBackground(UI.PANEL);
        logArea.setForeground(new Color(157, 191, 218));
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(8, 12, 8, 12));
        tabs.addTab("Activity Log", UI.scroll(logArea));
        for (String line : ActivityLog.entries()) logArea.append(line + "\n");
        ActivityLog.addListener(line -> { logArea.append(line + "\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); });

        resTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int vr = resTable.getSelectedRow();
            if (vr < 0) return;
            int mr = resTable.convertRowIndexToModel(vr);
            if (mr < shown.size()) map.setRoutes(Collections.singletonList(shown.get(mr)));
        });

        UI.Card tabCard = new UI.Card(new BorderLayout());
        tabCard.setPreferredSize(new Dimension(10, 260));
        tabCard.setBorder(new EmptyBorder(12, 14, 14, 14));
        tabCard.add(tabs, BorderLayout.CENTER);

        JPanel centre = new JPanel(new BorderLayout(0, 16));
        centre.setOpaque(false);
        centre.add(mapCard, BorderLayout.CENTER);
        centre.add(tabCard, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        body.add(formCard, BorderLayout.WEST);
        body.add(centre, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JButton wide(JButton b, java.awt.event.ActionListener l) {
        b.addActionListener(l);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return b;
    }

    private JComponent tableScroll(DefaultTableModel m) {
        JTable t = UI.table(m);
        t.setRowSelectionAllowed(false);
        return UI.scroll(t);
    }

    private static String sel(JComboBox<String> b) { return (String) b.getSelectedItem(); }

    private void guard(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException ex) {
            UI.message(this, "Please check your input", ex.getMessage(), true);
        } catch (Db.DataAccessException ex) {
            UI.message(this, "Database error", ex.getMessage(), true);
        }
    }

    private void addRequest() {
        guard(() -> {
            RoutingRequest r = controller.addRequest(sel(vehicleBox), sel(destBox), sel(severityBox));
            ActivityLog.add("Queued " + r.severity + " request #" + r.requestId + ": " + r.vehicleId + " \u2192 " + r.destination + ".");
            tabs.select("Pending Requests");
        });
    }

    private void queueEvent() {
        guard(() -> {
            String type = sel(typeBox);
            int t = 0;
            if ("Congestion Update".equals(type)) {
                try { t = Integer.parseInt(timeF.getText().trim()); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Travel time must be a positive whole number."); }
            }
            TrafficEvent ev = controller.queueEvent(sel(fromBox), sel(toBox), type, t);
            ActivityLog.add("Traffic event queued: " + ev.description() + " (FIFO #" + ev.id + ").");
            tabs.select("Queued Events");
        });
    }

    private void process() {
        guard(() -> {
            List<RouteResult> res = controller.processAll();
            if (res.isEmpty()) ActivityLog.add("No pending routing requests. Queued traffic events (if any) were applied.");
            String now = new SimpleDateFormat("HH:mm:ss").format(new Date());
            for (int i = res.size() - 1; i >= 0; i--) {
                RouteResult r = res.get(i);
                resModel.insertRow(0, new Object[]{now, r.vehicleId, r.severity, r.status, r.path.isEmpty() ? "No path" : r.pathText(), r.totalTime});
                shown.add(0, r);
            }
            while (shown.size() > 50) { shown.remove(shown.size() - 1); resModel.removeRow(resModel.getRowCount() - 1); }
            for (RouteResult r : res) ActivityLog.add(r.compact());
            resTable.clearSelection();
            map.setRoutes(res);
            if (!res.isEmpty()) tabs.select("Latest Results");
        });
    }

    private void move() {
        guard(() -> {
            controller.moveVehicle(sel(vehicleBox), sel(moveBox));
            ActivityLog.add(sel(vehicleBox) + " position updated to " + sel(moveBox) + ".");
        });
    }

    @Override public void refresh() {
        UI.setItems(vehicleBox, controller.vehicleIds());
        List<String> j = controller.junctionIds();
        UI.setItems(destBox, j);
        UI.setItems(fromBox, j);
        UI.setItems(toBox, j);
        UI.setItems(moveBox, j);

        reqModel.setRowCount(0);
        int n = 1;
        for (RoutingRequest r : controller.pendingRequests()) {
            EmergencyVehicle v = controller.vehicles.get(r.vehicleId);
            reqModel.addRow(new Object[]{n++, "#" + r.requestId, r.severity, r.vehicleId, v == null ? "?" : v.currentJunction, r.destination});
        }
        evModel.setRowCount(0);
        n = 1;
        for (TrafficEvent e : controller.pendingEvents())
            evModel.addRow(new Object[]{n++, "#" + e.id, e.typeCode(), e.from + " \u2192 " + e.to, e.newTime() == null ? "-" : e.newTime() + " min"});
        map.repaint();
    }
}
