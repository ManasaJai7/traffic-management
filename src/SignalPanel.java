import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Traffic signals: CRUD on the TrafficSignal table, plus a one-click status cycle. */
class SignalPanel extends CrudPanel {
    private static final String[] STATUSES = {"GREEN", "YELLOW", "RED"};
    private final JComboBox<String> junctionBox = UI.combo(), statusBox = UI.combo(STATUSES);
    private final JTextField greenF = UI.field(10);
    private final JButton cycleBtn = UI.button("Cycle status  (G \u2192 Y \u2192 R)", UI.AMBER);
    private long selectedId = -1;

    SignalPanel(TrafficController c) {
        super(c, "Traffic Signals", "Signal heads installed at junctions. Status colours also appear on the map.",
              "Signal #", "Junction", "Location name", "Status", "Green time (s)");
        JPanel f = formBox();
        UI.formRow(f, "JUNCTION", junctionBox);
        UI.formRow(f, "STATUS", statusBox);
        UI.formRow(f, "GREEN TIME (SECONDS, 5-180)", greenF);
        cycleBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        cycleBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        f.add(Box.createVerticalStrut(16));
        f.add(cycleBtn);
        cycleBtn.setEnabled(false);
        cycleBtn.addActionListener(e -> guarded(this::cycle));
        installForm(f);
    }

    @Override protected void refreshLookups() { UI.setItems(junctionBox, controller.junctionIds()); }

    @Override protected List<Object[]> loadRows() {
        return Db.rows("SELECT s.signal_id, s.junction_id, j.junction_name, s.signal_status, s.green_seconds "
            + "FROM TrafficSignal s JOIN Junction j ON j.junction_id = s.junction_id ORDER BY s.signal_id");
    }

    @Override protected void onSelect(int r) {
        selectedId = Long.parseLong(text(r, 0));
        junctionBox.setSelectedItem(text(r, 1));
        statusBox.setSelectedItem(text(r, 3));
        greenF.setText(text(r, 4));
        cycleBtn.setEnabled(true);
    }

    @Override protected void onClear() {
        selectedId = -1;
        statusBox.setSelectedIndex(0);
        greenF.setText("30");
        cycleBtn.setEnabled(false);
    }

    @Override protected void onAdd() {
        String j = (String) junctionBox.getSelectedItem();
        if (j == null) throw new IllegalArgumentException("Create a junction first.");
        int green = parseInt(greenF, "Green time", 5, 180);
        Db.update("INSERT INTO TrafficSignal(junction_id, signal_status, green_seconds) VALUES (?,?,?)", j, statusBox.getSelectedItem(), green);
        ActivityLog.add("Signal added at " + j + ".");
        finishAction();
    }

    @Override protected void onUpdate() {
        int green = parseInt(greenF, "Green time", 5, 180);
        Db.update("UPDATE TrafficSignal SET junction_id = ?, signal_status = ?, green_seconds = ? WHERE signal_id = ?",
                  junctionBox.getSelectedItem(), statusBox.getSelectedItem(), green, selectedId);
        ActivityLog.add("Signal #" + selectedId + " updated.");
        finishAction();
    }

    @Override protected void onDelete() {
        if (!UI.confirm(this, "Delete signal #" + selectedId + "?", "The signal head is removed from the junction.")) return;
        Db.update("DELETE FROM TrafficSignal WHERE signal_id = ?", selectedId);
        ActivityLog.add("Signal #" + selectedId + " deleted.");
        finishAction();
    }

    private void cycle() {
        if (selectedId < 0) return;
        String cur = (String) statusBox.getSelectedItem();
        String next = "GREEN".equals(cur) ? "YELLOW" : ("YELLOW".equals(cur) ? "RED" : "GREEN");
        Db.update("UPDATE TrafficSignal SET signal_status = ? WHERE signal_id = ?", next, selectedId);
        ActivityLog.add("Signal #" + selectedId + " changed to " + next + ".");
        finishAction();
    }
}
