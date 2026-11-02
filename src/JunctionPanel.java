import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/** Junctions: CRUD on the Junction table, with a click-to-place mini map. */
class JunctionPanel extends CrudPanel {
    private final JTextField idF = UI.field(10), nameF = UI.field(10), locF = UI.field(10), xF = UI.field(4), yF = UI.field(4);
    private final NetworkPanel picker;

    JunctionPanel(TrafficController c) {
        super(c, "Junction Management", "The nodes of the city road graph. Click the map to choose where a junction sits.",
              "ID", "Name", "Location", "X", "Y", "Signal");
        JPanel f = formBox();
        UI.formRow(f, "JUNCTION ID", idF);
        UI.formRow(f, "NAME", nameF);
        UI.formRow(f, "LOCATION / MAIN ROAD", locF);
        UI.formPair(f, "MAP X  (10-640)", xF, "MAP Y  (10-410)", yF);

        picker = new NetworkPanel(c);
        picker.setPreferredSize(new Dimension(300, 170));
        picker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        picker.setAlignmentX(Component.LEFT_ALIGNMENT);
        picker.setPickListener((x, y) -> { xF.setText(String.valueOf(x)); yF.setText(String.valueOf(y)); });
        JLabel hint = UI.label("Click the mini map to fill X and Y", Font.PLAIN, 11, UI.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.add(Box.createVerticalStrut(12));
        f.add(picker);
        f.add(Box.createVerticalStrut(4));
        f.add(hint);
        KeyAdapter move = new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { syncPreview(); }
        };
        xF.addKeyListener(move);
        yF.addKeyListener(move);
        installForm(f);
    }

    private void syncPreview() {
        try { picker.setPreview(Integer.parseInt(xF.getText().trim()), Integer.parseInt(yF.getText().trim())); }
        catch (NumberFormatException e) { picker.setPreview(null, null); }
    }

    @Override protected List<Object[]> loadRows() {
        return Db.rows("SELECT j.junction_id, j.junction_name, j.location, j.x, j.y, "
            + "COALESCE((SELECT signal_status FROM TrafficSignal s WHERE s.junction_id = j.junction_id ORDER BY signal_id LIMIT 1), '-') "
            + "FROM Junction j ORDER BY j.junction_id");
    }

    @Override protected void onSelect(int r) {
        idF.setText(text(r, 0)); nameF.setText(text(r, 1)); locF.setText(text(r, 2));
        xF.setText(text(r, 3)); yF.setText(text(r, 4));
        UI.lock(idF, true);
        syncPreview();
    }

    @Override protected void onClear() {
        idF.setText(""); nameF.setText(""); locF.setText(""); xF.setText(""); yF.setText("");
        UI.lock(idF, false);
        picker.setPreview(null, null);
    }

    private Object[] readForm() {
        String name = nameF.getText().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Please enter a junction name.");
        return new Object[]{name, locF.getText().trim(), parseInt(xF, "Map X", 10, 640), parseInt(yF, "Map Y", 10, 410)};
    }

    @Override protected void onAdd() {
        String id = idF.getText().trim();
        requireId(id, "Junction ID");
        Object[] v = readForm();
        Db.update("INSERT INTO Junction(junction_id, junction_name, location, x, y) VALUES (?,?,?,?,?)", id, v[0], v[1], v[2], v[3]);
        ActivityLog.add("Junction " + id + " added.");
        finishAction();
    }

    @Override protected void onUpdate() {
        String id = idF.getText().trim();
        Object[] v = readForm();
        Db.update("UPDATE Junction SET junction_name = ?, location = ?, x = ?, y = ? WHERE junction_id = ?", v[0], v[1], v[2], v[3], id);
        ActivityLog.add("Junction " + id + " updated.");
        finishAction();
    }

    @Override protected void onDelete() {
        String id = idF.getText().trim();
        if (Db.scalar("SELECT COUNT(*) FROM Vehicle WHERE current_junction = ?", id) > 0)
            throw new IllegalArgumentException("Vehicles are stationed at " + id + ". Move or delete them first.");
        long roads = Db.scalar("SELECT COUNT(*) FROM Road WHERE from_junction = ? OR to_junction = ?", id, id);
        if (!UI.confirm(this, "Delete junction " + id + "?",
                "This also deletes " + roads + " connected road(s), its traffic signals and any queued events or requests that use it."))
            return;
        Db.update("DELETE FROM Junction WHERE junction_id = ?", id);
        ActivityLog.add("Junction " + id + " deleted.");
        finishAction();
    }
}
