import javax.swing.*;
import java.awt.*;
import java.util.List;

/** Roads: directed road segments between two junctions (CRUD on the Road table). */
class RoadPanel extends CrudPanel {
    private final JComboBox<String> fromBox = UI.combo(), toBox = UI.combo(), statusBox = UI.combo("OPEN", "CLOSED");
    private final JTextField nameF = UI.field(10), timeF = UI.field(10);
    private final JCheckBox reverse = new JCheckBox("Also create the opposite direction");

    RoadPanel(TrafficController c) {
        super(c, "Road Management", "Directed roads with travel times in minutes. Two-way streets are two rows.",
              "From", "To", "Road name", "Free-flow (min)", "Now (min)", "Status");
        JPanel f = formBox();
        UI.formPair(f, "FROM JUNCTION", fromBox, "TO JUNCTION", toBox);
        UI.formRow(f, "ROAD NAME", nameF);
        UI.formPair(f, "TRAVEL TIME (MIN)", timeF, "STATUS", statusBox);
        reverse.setOpaque(false);
        reverse.setForeground(UI.TEXT);
        reverse.setFont(UI.font(Font.PLAIN, 12));
        reverse.setSelected(true);
        reverse.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.add(Box.createVerticalStrut(12));
        f.add(reverse);
        JLabel hint = UI.label("<html><body style='width:250px'>Saving resets the current congestion to this free-flow time. Use the Routing Console to simulate live congestion.</body></html>",
                               Font.PLAIN, 11, UI.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.add(Box.createVerticalStrut(10));
        f.add(hint);
        installForm(f);
    }

    @Override protected void refreshLookups() {
        UI.setItems(fromBox, controller.junctionIds());
        UI.setItems(toBox, controller.junctionIds());
        if (toBox.getItemCount() > 1 && fromBox.getSelectedIndex() == toBox.getSelectedIndex() && table.getSelectedRow() < 0)
            toBox.setSelectedIndex(1);
    }

    @Override protected List<Object[]> loadRows() {
        return Db.rows("SELECT from_junction, to_junction, road_name, normal_time, travel_time, "
            + "CASE WHEN closed = 1 THEN 'CLOSED' WHEN travel_time > normal_time THEN 'CONGESTED' ELSE 'OPEN' END "
            + "FROM Road ORDER BY from_junction, to_junction");
    }

    @Override protected void onSelect(int r) {
        fromBox.setSelectedItem(text(r, 0));
        toBox.setSelectedItem(text(r, 1));
        nameF.setText(text(r, 2));
        timeF.setText(text(r, 3));
        statusBox.setSelectedItem("CLOSED".equals(text(r, 5)) ? "CLOSED" : "OPEN");
        fromBox.setEnabled(false);
        toBox.setEnabled(false);
        reverse.setEnabled(false);
    }

    @Override protected void onClear() {
        nameF.setText("");
        timeF.setText("");
        statusBox.setSelectedIndex(0);
        fromBox.setEnabled(true);
        toBox.setEnabled(true);
        reverse.setEnabled(true);
    }

    @Override protected void onAdd() {
        String from = (String) fromBox.getSelectedItem(), to = (String) toBox.getSelectedItem();
        if (from == null || to == null) throw new IllegalArgumentException("Create at least two junctions first.");
        if (from.equals(to)) throw new IllegalArgumentException("A road must connect two different junctions.");
        final int time = parseInt(timeF, "Travel time", 1, 999);
        final String name = nameF.getText().trim();
        final int closed = "CLOSED".equals(statusBox.getSelectedItem()) ? 1 : 0;
        final String a = from, b = to;
        final boolean both = reverse.isSelected();
        final String sql = "INSERT INTO Road(from_junction, to_junction, road_name, normal_time, travel_time, closed) VALUES (?,?,?,?,?,?)";
        Db.transaction(con -> {
            Db.update(con, sql, a, b, name, time, time, closed);
            if (both) Db.update(con, sql, b, a, name, time, time, closed);
        });
        ActivityLog.add("Road " + from + " \u2192 " + to + (both ? " (both directions)" : "") + " added.");
        finishAction();
    }

    @Override protected void onUpdate() {
        String from = (String) fromBox.getSelectedItem(), to = (String) toBox.getSelectedItem();
        int time = parseInt(timeF, "Travel time", 1, 999);
        int closed = "CLOSED".equals(statusBox.getSelectedItem()) ? 1 : 0;
        Db.update("UPDATE Road SET road_name = ?, normal_time = ?, travel_time = ?, closed = ? WHERE from_junction = ? AND to_junction = ?",
                  nameF.getText().trim(), time, time, closed, from, to);
        ActivityLog.add("Road " + from + " \u2192 " + to + " updated.");
        finishAction();
    }

    @Override protected void onDelete() {
        String from = (String) fromBox.getSelectedItem(), to = (String) toBox.getSelectedItem();
        if (!UI.confirm(this, "Delete road " + from + " \u2192 " + to + "?", "Any queued traffic events for this road are removed too."))
            return;
        Db.update("DELETE FROM Road WHERE from_junction = ? AND to_junction = ?", from, to);
        ActivityLog.add("Road " + from + " \u2192 " + to + " deleted.");
        finishAction();
    }
}
