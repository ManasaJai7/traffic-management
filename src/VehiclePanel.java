import javax.swing.*;
import java.util.List;

/** Emergency vehicles: CRUD on the Vehicle table. */
class VehiclePanel extends CrudPanel {
    private final JTextField idF = UI.field(10);
    private final JComboBox<String> typeBox = UI.combo(TrafficController.VEHICLE_TYPES), junctionBox = UI.combo();

    VehiclePanel(TrafficController c) {
        super(c, "Emergency Vehicles", "Ambulances, fire engines and rescue units that can be routed.",
              "Vehicle", "Type", "Junction", "Location name", "Pending requests");
        JPanel f = formBox();
        UI.formRow(f, "VEHICLE ID", idF);
        UI.formRow(f, "TYPE", typeBox);
        UI.formRow(f, "CURRENT JUNCTION", junctionBox);
        installForm(f);
    }

    @Override protected void refreshLookups() { UI.setItems(junctionBox, controller.junctionIds()); }

    @Override protected List<Object[]> loadRows() {
        return Db.rows("SELECT v.vehicle_id, v.vehicle_type, v.current_junction, j.junction_name, "
            + "(SELECT COUNT(*) FROM RoutingRequest q WHERE q.vehicle_id = v.vehicle_id AND q.status = 'PENDING') "
            + "FROM Vehicle v JOIN Junction j ON j.junction_id = v.current_junction ORDER BY v.vehicle_id");
    }

    @Override protected void onSelect(int r) {
        idF.setText(text(r, 0));
        typeBox.setSelectedItem(text(r, 1));
        junctionBox.setSelectedItem(text(r, 2));
        UI.lock(idF, true);
    }

    @Override protected void onClear() {
        idF.setText("");
        typeBox.setSelectedIndex(0);
        UI.lock(idF, false);
    }

    @Override protected void onAdd() {
        String id = idF.getText().trim();
        requireId(id, "Vehicle ID");
        String j = (String) junctionBox.getSelectedItem();
        if (j == null) throw new IllegalArgumentException("Create a junction first.");
        Db.update("INSERT INTO Vehicle(vehicle_id, vehicle_type, current_junction) VALUES (?,?,?)", id, typeBox.getSelectedItem(), j);
        ActivityLog.add("Vehicle " + id + " added at " + j + ".");
        finishAction();
    }

    @Override protected void onUpdate() {
        String id = idF.getText().trim();
        Db.update("UPDATE Vehicle SET vehicle_type = ?, current_junction = ? WHERE vehicle_id = ?",
                  typeBox.getSelectedItem(), junctionBox.getSelectedItem(), id);
        ActivityLog.add("Vehicle " + id + " updated.");
        finishAction();
    }

    @Override protected void onDelete() {
        String id = idF.getText().trim();
        if (!UI.confirm(this, "Delete vehicle " + id + "?", "Its pending routing requests are removed too. Route history is kept."))
            return;
        Db.update("DELETE FROM Vehicle WHERE vehicle_id = ?", id);
        ActivityLog.add("Vehicle " + id + " deleted.");
        finishAction();
    }
}
