import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

/** Base screen for the four management pages: searchable table on the left, Add / Update / Delete form on the right. */
abstract class CrudPanel extends JPanel implements Refreshable {
    protected final TrafficController controller;
    protected final DefaultTableModel model;
    protected final JTable table;
    protected final JButton addBtn = UI.button("Add", UI.ACCENT);
    protected final JButton updateBtn = UI.button("Update", UI.BLUE);
    protected final JButton deleteBtn = UI.button("Delete", UI.RED);
    protected final JButton clearBtn = UI.button("Clear", UI.GRAY);
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTextField search = UI.field(16);
    private final JLabel countLabel = UI.label("", Font.PLAIN, 12, UI.MUTED);
    private final JLabel formTitle = UI.label("New record", Font.BOLD, 16, UI.TEXT);
    private final JPanel formHolder = new JPanel(new BorderLayout());

    CrudPanel(TrafficController controller, String title, String subtitle, String... columns) {
        super(new BorderLayout(0, 18));
        this.controller = controller;
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        model = UI.readOnlyModel(columns);
        table = UI.table(model);
        sorter = new TableRowSorter<DefaultTableModel>(model);
        table.setRowSorter(sorter);

        // header: title on the left, search box on the right
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(UI.pageHeader(title, subtitle), BorderLayout.WEST);
        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchBox.setOpaque(false);
        searchBox.add(UI.label("Search", Font.BOLD, 12, UI.MUTED));
        searchBox.add(search);
        head.add(searchBox, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        // table card
        UI.Card tableCard = new UI.Card(new BorderLayout(0, 10));
        tableCard.add(countLabel, BorderLayout.NORTH);
        tableCard.add(UI.scroll(table), BorderLayout.CENTER);

        // form card
        UI.Card formCard = new UI.Card(new BorderLayout(0, 8));
        formCard.setPreferredSize(new Dimension(350, 10));
        formTitle.setBorder(new EmptyBorder(0, 0, 4, 0));
        formCard.add(formTitle, BorderLayout.NORTH);
        formHolder.setOpaque(false);
        formCard.add(formHolder, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        buttons.setOpaque(false);
        buttons.add(addBtn);
        buttons.add(updateBtn);
        buttons.add(deleteBtn);
        buttons.add(clearBtn);
        formCard.add(buttons, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        center.add(tableCard, BorderLayout.CENTER);
        center.add(formCard, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        addBtn.addActionListener(e -> guarded(this::onAdd));
        updateBtn.addActionListener(e -> guarded(this::onUpdate));
        deleteBtn.addActionListener(e -> guarded(this::onDelete));
        clearBtn.addActionListener(e -> resetForm());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int vr = table.getSelectedRow();
            if (vr >= 0) {
                onSelect(table.convertRowIndexToModel(vr));
                formTitle.setText("Edit record");
            }
            updateButtons();
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        updateButtons();
    }

    // ---- hooks implemented by the four screens
    protected abstract List<Object[]> loadRows();
    protected abstract void onSelect(int modelRow);
    protected abstract void onClear();
    protected abstract void onAdd();
    protected abstract void onUpdate();
    protected abstract void onDelete();
    protected void refreshLookups() { }

    protected void installForm(JComponent form) { formHolder.add(form, BorderLayout.CENTER); }

    protected Object cell(int modelRow, int col) { return model.getValueAt(modelRow, col); }
    protected String text(int modelRow, int col) { Object o = cell(modelRow, col); return o == null ? "" : o.toString(); }

    private void applyFilter() {
        String q = search.getText().trim();
        sorter.setRowFilter(q.isEmpty() ? null : RowFilter.<DefaultTableModel, Integer>regexFilter("(?i)" + Pattern.quote(q)));
    }

    private void updateButtons() {
        boolean selected = table.getSelectedRow() >= 0;
        addBtn.setEnabled(!selected);
        updateBtn.setEnabled(selected);
        deleteBtn.setEnabled(selected);
    }

    /** Clears the selection and the form fields. */
    protected void resetForm() {
        table.clearSelection();
        onClear();
        formTitle.setText("New record");
        updateButtons();
    }

    /** Call after a successful database change. */
    protected void finishAction() {
        controller.dataChanged();   // reloads memory + refreshes every screen
        resetForm();
    }

    protected void guarded(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException ex) {
            UI.message(this, "Please check your input", ex.getMessage(), true);
        } catch (Db.DataAccessException ex) {
            UI.message(this, "Database error", ex.getMessage(), true);
        }
    }

    @Override public void refresh() {
        boolean hadSelection = table.getSelectedRow() >= 0;
        refreshLookups();
        model.setRowCount(0);
        for (Object[] r : loadRows()) model.addRow(r);
        UI.fitColumns(table);
        countLabel.setText(model.getRowCount() + " records   \u2022   click a row to edit it");
        if (hadSelection) resetForm();
    }

    // ---- validation helpers
    protected static int parseInt(JTextField f, String name, int min, int max) {
        try {
            int v = Integer.parseInt(f.getText().trim());
            if (v < min || v > max) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number between " + min + " and " + max + ".");
        }
    }

    protected static void requireId(String id, String what) {
        if (!id.matches("[A-Za-z0-9_-]{1,12}"))
            throw new IllegalArgumentException(what + " must be 1-12 letters, digits, '-' or '_' (no spaces).");
    }

    protected static JPanel formBox() {
        JPanel f = new JPanel();
        f.setOpaque(false);
        f.setLayout(new BoxLayout(f, BoxLayout.Y_AXIS));
        return f;
    }
}
