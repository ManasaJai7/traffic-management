import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

/** Reports: every route that was calculated and every traffic event, read from the database. Export to CSV. */
class HistoryPanel extends JPanel implements Refreshable {
    private final TrafficController controller;
    private final DefaultTableModel historyModel = UI.readOnlyModel("#", "Time", "Vehicle", "From", "To", "Severity", "Status", "Route", "Min");
    private final DefaultTableModel eventModel = UI.readOnlyModel("#", "Created", "Type", "Road", "New time", "Status", "Applied at");
    private final TableRowSorter<DefaultTableModel> historySorter = new TableRowSorter<DefaultTableModel>(historyModel);
    private final TableRowSorter<DefaultTableModel> eventSorter = new TableRowSorter<DefaultTableModel>(eventModel);
    private final JTextField search = UI.field(16);
    private JTable historyTable, eventTable;
    private final JLabel counts = UI.label("", Font.PLAIN, 12, UI.MUTED);

    HistoryPanel(TrafficController controller) {
        super(new BorderLayout(0, 18));
        this.controller = controller;
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(UI.pageHeader("History & Reports", "Every calculated route and traffic event is saved in the database."), BorderLayout.WEST);
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        tools.setOpaque(false);
        tools.add(UI.label("Search", Font.BOLD, 12, UI.MUTED));
        tools.add(search);
        head.add(tools, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        historyTable = UI.table(historyModel);
        historyTable.setRowSorter(historySorter);
        eventTable = UI.table(eventModel);
        eventTable.setRowSorter(eventSorter);

        UI.Tabs tabs = new UI.Tabs();
        tabs.addTab("Route history", UI.scroll(historyTable));
        tabs.addTab("Traffic events", UI.scroll(eventTable));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton exportRoutes = UI.button("Export routes (CSV)", UI.BLUE);
        JButton exportEvents = UI.button("Export events (CSV)", UI.BLUE);
        JButton clear = UI.button("Clear route history", UI.RED);
        actions.add(exportRoutes);
        actions.add(exportEvents);
        actions.add(clear);
        exportRoutes.addActionListener(e -> export(historyModel, "route_history.csv"));
        exportEvents.addActionListener(e -> export(eventModel, "traffic_events.csv"));
        clear.addActionListener(e -> clearHistory());

        UI.Card card = new UI.Card(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(tabs, BorderLayout.WEST);
        top.add(counts, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        // the tab header sits in "top"; the tab body is added below it
        card.remove(top);
        card.add(tabs, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
    }

    private void filter() {
        String q = search.getText().trim();
        RowFilter<DefaultTableModel, Integer> f = q.isEmpty() ? null : RowFilter.<DefaultTableModel, Integer>regexFilter("(?i)" + Pattern.quote(q));
        historySorter.setRowFilter(f);
        eventSorter.setRowFilter(f);
    }

    private void clearHistory() {
        if (!UI.confirm(this, "Clear route history?", "All saved route results are deleted. Traffic events and network data are kept.")) return;
        try {
            Db.update("DELETE FROM RouteHistory");
            ActivityLog.add("Route history cleared.");
            controller.dataChanged();
        } catch (Db.DataAccessException ex) {
            UI.message(this, "Database error", ex.getMessage(), true);
        }
    }

    private void export(DefaultTableModel m, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) f = new File(f.getPath() + ".csv");
        StringBuilder sb = new StringBuilder("\uFEFF");          // BOM so Excel opens UTF-8 correctly
        for (int c = 0; c < m.getColumnCount(); c++) sb.append(c > 0 ? "," : "").append(csv(m.getColumnName(c)));
        sb.append("\r\n");
        for (int r = 0; r < m.getRowCount(); r++) {
            for (int c = 0; c < m.getColumnCount(); c++) sb.append(c > 0 ? "," : "").append(csv(String.valueOf(m.getValueAt(r, c))));
            sb.append("\r\n");
        }
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            ActivityLog.add("Exported " + m.getRowCount() + " rows to " + f.getName() + ".");
            UI.message(this, "Export complete", m.getRowCount() + " rows saved to\n" + f.getAbsolutePath(), false);
        } catch (IOException ex) {
            UI.message(this, "Export failed", ex.getMessage(), true);
        }
    }

    private static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    @Override public void refresh() {
        historyModel.setRowCount(0);
        for (Object[] r : Db.rows("SELECT history_id, created_at, vehicle_id, start_junction, destination, severity, status, path, total_time "
                                + "FROM RouteHistory ORDER BY history_id DESC"))
            historyModel.addRow(r);
        eventModel.setRowCount(0);
        for (Object[] r : Db.rows("SELECT event_id, created_at, event_type, from_junction || ' \u2192 ' || to_junction, "
                                + "CASE WHEN new_time IS NULL THEN '-' ELSE new_time || ' min' END, status, COALESCE(applied_at, '-') "
                                + "FROM TrafficEvent ORDER BY event_id DESC"))
            eventModel.addRow(r);
        UI.fitColumns(historyTable);
        UI.fitColumns(eventTable);
        counts.setText(historyModel.getRowCount() + " routes  \u2022  " + eventModel.getRowCount() + " events");
    }
}
