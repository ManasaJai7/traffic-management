import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;

/** Shared look & feel: colours, fonts and small custom components (dark theme). */
final class UI {
    private UI() { }

    static final Color BG = new Color(13, 19, 31);
    static final Color SIDEBAR = new Color(9, 14, 24);
    static final Color PANEL = new Color(21, 31, 47);
    static final Color PANEL_ALT = new Color(26, 38, 58);
    static final Color BORDER = new Color(42, 58, 82);
    static final Color FIELD = new Color(31, 44, 65);
    static final Color FIELD_LOCKED = new Color(22, 32, 48);
    static final Color TEXT = new Color(228, 237, 249);
    static final Color MUTED = new Color(133, 157, 187);
    static final Color ACCENT = new Color(63, 207, 163);
    static final Color BLUE = new Color(66, 118, 205);
    static final Color AMBER = new Color(230, 156, 52);
    static final Color RED = new Color(235, 87, 87);
    static final Color GRAY = new Color(72, 88, 114);
    static final Color MAP_BG = new Color(11, 18, 30);
    static final Color SELECT = new Color(38, 62, 99);
    static final Color[] VEHICLE_COLORS = {
        new Color(113, 169, 255), new Color(255, 150, 97), new Color(211, 145, 255),
        new Color(255, 219, 102), new Color(120, 230, 200), new Color(255, 128, 160)
    };

    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        try {
            Set<String> names = new HashSet<String>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : new String[]{"Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Roboto", "Noto Sans", "DejaVu Sans"})
                if (names.contains(f)) return f;
        } catch (Throwable ignore) { }
        return Font.SANS_SERIF;
    }

    static Font font(int style, int size) { return new Font(FAMILY, style, size); }

    static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                         Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                         Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    static Color vehicleColor(TrafficController c, String vehicleId) {
        int i = c.vehicleIndex(vehicleId);
        return VEHICLE_COLORS[(i < 0 ? 0 : i) % VEHICLE_COLORS.length];
    }

    static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ------------------------------------------------------------------ simple factories

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    static JPanel pageHeader(String title, String subtitle) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel t = label(title, Font.BOLD, 24, TEXT);
        JLabel s = label(subtitle, Font.PLAIN, 13, MUTED);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setBorder(new EmptyBorder(4, 0, 0, 0));
        p.add(t);
        p.add(s);
        return p;
    }

    static Image appIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        antialias(g);
        g.setColor(new Color(20, 30, 48));
        g.fillRoundRect(4, 4, 56, 56, 16, 16);
        Color[] c = {RED, AMBER, ACCENT};
        for (int i = 0; i < 3; i++) { g.setColor(c[i]); g.fillOval(22, 9 + i * 16, 20, 14); }
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ cards, buttons

    static class Card extends JPanel {
        private Color stripe;
        Card(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(new EmptyBorder(16, 18, 16, 18));
        }
        Card stripe(Color c) { stripe = c; return this; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            antialias(g);
            g.setColor(PANEL);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            if (stripe != null) { g.setColor(stripe); g.fillRoundRect(0, 16, 4, Math.max(8, getHeight() - 32), 4, 4); }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    static class FlatButton extends JButton {
        private final Color base;
        private boolean hover;
        FlatButton(String text, Color base) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(font(Font.BOLD, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 16, 10, 16));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            antialias(g);
            Color fill = base;
            if (!isEnabled()) fill = mix(PANEL, base, 0.28f);
            else if (getModel().isPressed()) fill = mix(base, Color.BLACK, 0.2f);
            else if (hover) fill = mix(base, Color.WHITE, 0.14f);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.dispose();
            super.paintComponent(g0);
        }
        @Override public Color getForeground() { return isEnabled() ? Color.WHITE : new Color(150, 165, 185); }
    }

    static JButton button(String text, Color color) { return new FlatButton(text, color); }

    // ------------------------------------------------------------------ inputs

    static void styleText(JTextComponent f) {
        f.setBackground(FIELD);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setSelectionColor(BLUE);
        f.setSelectedTextColor(Color.WHITE);
        f.setFont(font(Font.PLAIN, 13));
        f.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(7, 10, 7, 10)));
    }

    static JTextField field(int columns) {
        JTextField f = new JTextField(columns);
        styleText(f);
        return f;
    }

    static void lock(JTextComponent f, boolean locked) {
        f.setEditable(!locked);
        f.setBackground(locked ? FIELD_LOCKED : FIELD);
        f.setForeground(locked ? MUTED : TEXT);
    }

    static JComboBox<String> combo(String... items) {
        JComboBox<String> c = new JComboBox<String>(items);
        c.setUI(new DarkComboUI());
        c.setFont(font(Font.PLAIN, 13));
        c.setBackground(FIELD);
        c.setForeground(TEXT);
        c.setBorder(new LineBorder(BORDER, 1, true));
        c.setRenderer(new ComboRenderer());
        c.setMaximumRowCount(12);
        c.setPreferredSize(new Dimension(120, 36));
        return c;
    }

    /** Replaces the items of a combo box but keeps the current selection if it still exists. */
    static void setItems(JComboBox<String> box, Collection<String> items) {
        Object selected = box.getSelectedItem();
        box.removeAllItems();
        for (String s : items) box.addItem(s);
        if (selected != null && items.contains(selected)) box.setSelectedItem(selected);
        else if (box.getItemCount() > 0) box.setSelectedIndex(0);
    }

    static class ComboRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, false);
            setBorder(new EmptyBorder(7, 10, 7, 10));
            setFont(font(Font.PLAIN, 13));
            setOpaque(true);
            setForeground(TEXT);
            setBackground(index == -1 ? FIELD : (sel ? SELECT : PANEL_ALT));
            return this;
        }
    }

    static class DarkComboUI extends BasicComboBoxUI {
        @Override protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    antialias(g);
                    g.setColor(FIELD);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(MUTED);
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g.fillPolygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
                    g.dispose();
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setFocusable(false);
            b.setContentAreaFilled(false);
            return b;
        }
        @Override public void paintCurrentValueBackground(Graphics g, Rectangle b, boolean hasFocus) {
            g.setColor(FIELD);
            g.fillRect(b.x, b.y, b.width, b.height);
        }
        @Override protected ComboPopup createPopup() {
            return new BasicComboPopup(comboBox) {
                @Override protected JScrollPane createScroller() {
                    JScrollPane sp = super.createScroller();
                    sp.getVerticalScrollBar().setUI(new DarkScrollBarUI());
                    sp.getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
                    sp.setBorder(new LineBorder(BORDER));
                    return sp;
                }
            };
        }
    }

    /** Adds "LABEL" above a field inside a vertical (BoxLayout) form panel. */
    static void formRow(JPanel form, String label, JComponent field) {
        JLabel l = label(label, Font.BOLD, 11, MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(10, 0, 4, 0));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
        form.add(l);
        form.add(field);
    }

    /** Two labelled fields side by side. */
    static void formPair(JPanel form, String l1, JComponent f1, String l2, JComponent f2) {
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.add(column(l1, f1));
        row.add(column(l2, f2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        form.add(row);
    }

    private static JPanel column(String label, JComponent field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        formRow(p, label, field);
        return p;
    }

    /** Vertical form that always matches the width of the scroll pane it sits in (no sideways scrolling). */
    static class ScrollForm extends JPanel implements Scrollable {
        ScrollForm() { setOpaque(false); setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JLabel sectionLabel(String text) {
        JLabel l = label(text, Font.BOLD, 11, ACCENT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(14, 0, 0, 0));
        return l;
    }

    // ------------------------------------------------------------------ tables & scrolling

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                for (int r = 0; r < getRowCount(); r++) {
                    Object v = getValueAt(r, c);
                    if (v != null) return v.getClass();
                }
                return Object.class;
            }
        };
    }

    static Color badgeColor(String s) {
        if (s == null || s.length() > 16) return null;
        switch (s) {
            case "OPEN": case "GREEN": case "ROUTE FOUND": case "APPLIED": case "DONE": case "REOPEN": return ACCENT;
            case "CLOSED": case "RED": case "UNREACHABLE": case "CRITICAL": case "CLOSURE": return RED;
            case "CONGESTED": case "YELLOW": case "HIGH": case "CONGESTION": return AMBER;
            case "NORMAL": case "PENDING": return BLUE;
            default: return null;
        }
    }

    static class CellRenderer extends DefaultTableCellRenderer {
        private Color badge, rowBg = PANEL;
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            setOpaque(false);
            setFont(font(Font.PLAIN, 13));
            setBorder(new EmptyBorder(0, 12, 0, 12));
            setHorizontalAlignment(SwingConstants.LEFT);
            rowBg = sel ? SELECT : (row % 2 == 0 ? PANEL : new Color(24, 35, 53));
            badge = v instanceof String ? badgeColor((String) v) : null;
            if (badge != null) {                       // shorter text so the pill always fits
                String txt = (String) v;
                if (txt.equals("ROUTE FOUND")) setText("FOUND");
                else if (txt.equals("CONGESTION")) setText("CONGEST");
                else if (txt.equals("UNREACHABLE")) setText("NO ROUTE");
            }
            setForeground(sel ? Color.WHITE : (badge != null ? badge : TEXT));
            return this;
        }
        @Override protected void paintComponent(Graphics g0) {
            g0.setColor(rowBg);
            g0.fillRect(0, 0, getWidth(), getHeight());
            if (badge != null) {
                Graphics2D g = (Graphics2D) g0.create();
                antialias(g);
                int tw = getFontMetrics(getFont()).stringWidth(getText());
                g.setColor(alpha(badge, 46));
                g.fillRoundRect(6, (getHeight() - 22) / 2, tw + 12, 22, 12, 12);
                g.dispose();
            }
            super.paintComponent(g0);
        }
    }

    static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, false, false, row, col);
            setText(v == null ? "" : v.toString().toUpperCase());
            setFont(font(Font.BOLD, 11));
            setForeground(MUTED);
            setBackground(PANEL_ALT);
            setOpaque(true);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(0, 12, 0, 12)));
            return this;
        }
    }

    static JTable table(TableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(34);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setBackground(PANEL);
        t.setForeground(TEXT);
        t.setSelectionBackground(SELECT);
        t.setSelectionForeground(Color.WHITE);
        t.setFont(font(Font.PLAIN, 13));
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        CellRenderer r = new CellRenderer();
        t.setDefaultRenderer(Object.class, r);
        t.setDefaultRenderer(Number.class, r);
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setDefaultRenderer(new HeaderRenderer());
        h.setPreferredSize(new Dimension(10, 36));
        h.setBackground(PANEL_ALT);
        return t;
    }

    /** Sizes every column to its content so names are not cut off. */
    static void fitColumns(JTable t) {
        FontMetrics hm = t.getFontMetrics(font(Font.BOLD, 11));
        for (int c = 0; c < t.getColumnCount(); c++) {
            javax.swing.table.TableColumn col = t.getColumnModel().getColumn(c);
            int w = hm.stringWidth(String.valueOf(col.getHeaderValue())) + 34;
            for (int r = 0; r < Math.min(t.getRowCount(), 200); r++) {
                Component comp = t.prepareRenderer(t.getCellRenderer(r, c), r, c);
                w = Math.max(w, comp.getPreferredSize().width + 10);
            }
            col.setPreferredWidth(Math.min(w, 340));
        }
    }

    static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(62, 84, 116);
            trackColor = PANEL;
        }
        private JButton zero() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
            return b;
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(PANEL);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) g0.create();
            antialias(g);
            g.setColor(thumbColor);
            g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g.dispose();
        }
    }

    static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(BORDER, 1, false));
        sp.getViewport().setBackground(PANEL);
        sp.setBackground(PANEL);
        sp.getVerticalScrollBar().setUI(new DarkScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 11));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        JPanel corner = new JPanel();
        corner.setBackground(PANEL_ALT);
        sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        if (c instanceof JTable) sp.setColumnHeaderView(((JTable) c).getTableHeader());
        return sp;
    }

    // ------------------------------------------------------------------ tabs

    static class TabButton extends JToggleButton {
        TabButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(font(Font.BOLD, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 14, 8, 14));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            antialias(g);
            if (isSelected()) {
                g.setColor(PANEL_ALT);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.setColor(ACCENT);
                g.fillRoundRect(12, getHeight() - 3, getWidth() - 24, 3, 3, 3);
            }
            g.setFont(getFont());
            g.setColor(isSelected() ? TEXT : MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    static class Tabs extends JPanel {
        private final CardLayout cards = new CardLayout();
        private final JPanel body = new JPanel(cards);
        private final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        private final List<TabButton> buttons = new ArrayList<TabButton>();
        Tabs() {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            header.setOpaque(false);
            body.setOpaque(false);
            add(header, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
        }
        void addTab(final String title, JComponent comp) {
            TabButton b = new TabButton(title);
            buttons.add(b);
            header.add(b);
            body.add(comp, title);
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { select(title); }
            });
            if (buttons.size() == 1) select(title);
        }
        void select(String title) {
            cards.show(body, title);
            for (TabButton b : buttons) b.setSelected(b.getText().equals(title));
        }
    }

    // ------------------------------------------------------------------ legend

    static JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 2));
        p.setOpaque(false);
        p.add(legendItem(new Color(86, 120, 156), "Open road"));
        p.add(legendItem(AMBER, "Congested"));
        p.add(legendItem(RED, "Closed"));
        p.add(legendItem(VEHICLE_COLORS[0], "Fastest route"));
        p.add(legendItem(ACCENT, "Signal green"));
        return p;
    }

    private static JComponent legendItem(Color c, String text) {
        JLabel l = label(text, Font.PLAIN, 11, MUTED);
        l.setIcon(new Icon() {
            public void paintIcon(Component comp, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                antialias(g);
                g.setColor(c);
                g.fillOval(x, y, 10, 10);
                g.dispose();
            }
            public int getIconWidth() { return 10; }
            public int getIconHeight() { return 10; }
        });
        l.setIconTextGap(6);
        return l;
    }

    // ------------------------------------------------------------------ dialogs

    static void message(Component parent, String title, String text, boolean error) {
        showDialog(parent, title, text, error ? RED : ACCENT, false);
    }

    static boolean confirm(Component parent, String title, String text) {
        return showDialog(parent, title, text, AMBER, true);
    }

    private static boolean showDialog(Component parent, String title, String text, Color accent, boolean ask) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        final JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        final boolean[] result = {false};
        d.setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(PANEL);
        root.setBorder(new CompoundBorder(new LineBorder(accent, 2), new EmptyBorder(20, 24, 16, 24)));
        root.add(label(title, Font.BOLD, 16, accent), BorderLayout.NORTH);
        JLabel body = label("<html><body style='width:330px'>" + esc(text).replace("\n", "<br>") + "</body></html>", Font.PLAIN, 13, TEXT);
        root.add(body, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton ok = button(ask ? "Confirm" : "OK", ask ? AMBER : accent);
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { result[0] = true; d.dispose(); }
        });
        if (ask) {
            JButton cancel = button("Cancel", GRAY);
            cancel.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { d.dispose(); }
            });
            buttons.add(cancel);
        }
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        root.getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { d.dispose(); }
        });
        d.setContentPane(root);
        d.getRootPane().setDefaultButton(ok);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }
}
