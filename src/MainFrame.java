import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** The application window: sidebar navigation, page area and a status bar. */
class MainFrame extends JFrame {
    private final TrafficController controller = new TrafficController();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final List<Refreshable> pages = new ArrayList<Refreshable>();
    private final List<NavButton> navButtons = new ArrayList<NavButton>();
    private final JLabel status = UI.label("Ready", Font.PLAIN, 12, UI.MUTED);

    MainFrame() {
        super("Smart Traffic Management & Emergency Vehicle Routing");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(UI.appIcon());
        setSize(1400, 860);
        setMinimumSize(new Dimension(1200, 740));
        setLocationRelativeTo(null);
        getContentPane().setBackground(UI.BG);
        setLayout(new BorderLayout());

        content.setOpaque(false);
        addPage("Dashboard", new DashboardPanel(controller, this::show));
        addPage("Routing Console", new RoutingPanel(controller));
        addPage("Junctions", new JunctionPanel(controller));
        addPage("Roads", new RoadPanel(controller));
        addPage("Vehicles", new VehiclePanel(controller));
        addPage("Traffic Signals", new SignalPanel(controller));
        addPage("History & Reports", new HistoryPanel(controller));

        add(buildSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        ActivityLog.addListener(line -> status.setText(line));
        controller.addChangeListener(this::refreshAll);
        refreshAll();
        show("Dashboard");
        ActivityLog.add("Connected to MySQL database " + DatabaseConnection.dbDescription() + ": "
            + controller.junctions.size() + " junctions, " + controller.roads.size() + " roads, " + controller.vehicles.size() + " vehicles loaded.");
    }

    private void addPage(String name, JPanel page) {
        content.add(page, name);
        pages.add((Refreshable) page);
    }

    private void refreshAll() { for (Refreshable p : pages) p.refresh(); }

    private void show(String name) {
        cards.show(content, name);
        for (NavButton b : navButtons) b.setActive(b.getText().equals(name));
    }

    // ------------------------------------------------------------------ sidebar

    private JComponent buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(UI.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(240, 10));
        side.setBorder(new EmptyBorder(22, 0, 16, 0));

        side.add(new Logo());
        side.add(Box.createVerticalStrut(22));
        side.add(navSection("OVERVIEW"));
        side.add(nav("Dashboard"));
        side.add(nav("Routing Console"));
        side.add(navSection("MANAGE DATA"));
        side.add(nav("Junctions"));
        side.add(nav("Roads"));
        side.add(nav("Vehicles"));
        side.add(nav("Traffic Signals"));
        side.add(navSection("REPORTS"));
        side.add(nav("History & Reports"));
        side.add(Box.createVerticalGlue());

        JButton reset = UI.button("Reset demo data", UI.GRAY);
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.setMaximumSize(new Dimension(190, 38));
        reset.addActionListener(e -> {
            if (!UI.confirm(this, "Reset demo data?", "Every table is emptied and the sample city is loaded again. This cannot be undone.")) return;
            try {
                DatabaseSetup.resetDemoData();
                controller.dataChanged();
                ActivityLog.add("Demo data restored.");
            } catch (Db.DataAccessException ex) {
                UI.message(this, "Database error", ex.getMessage(), true);
            }
        });
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 0));
        wrap.setOpaque(false);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(reset);
        side.add(wrap);
        return side;
    }

    private JComponent navSection(String title) {
        JLabel l = UI.label(title, Font.BOLD, 10, new Color(88, 110, 140));
        l.setBorder(new EmptyBorder(18, 26, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private NavButton nav(final String name) {
        final NavButton b = new NavButton(name);
        navButtons.add(b);
        b.addActionListener(e -> show(name));
        return b;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(UI.SIDEBAR);
        bar.setBorder(new EmptyBorder(8, 18, 8, 18));
        bar.add(status, BorderLayout.CENTER);
        JLabel dbLabel = UI.label("MySQL  \u2022  " + DatabaseConnection.dbDescription(), Font.PLAIN, 12, UI.ACCENT);
        dbLabel.setToolTipText("MySQL database connection");
        bar.add(dbLabel, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ small components

    private static class NavButton extends JButton {
        private boolean active, hover;
        NavButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(UI.font(Font.BOLD, 13));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setPreferredSize(new Dimension(240, 42));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        void setActive(boolean a) { active = a; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.antialias(g);
            if (active) {
                g.setColor(UI.alpha(UI.ACCENT, 30));
                g.fillRoundRect(12, 2, getWidth() - 24, getHeight() - 4, 12, 12);
                g.setColor(UI.ACCENT);
                g.fillRoundRect(12, 10, 4, getHeight() - 20, 4, 4);
            } else if (hover) {
                g.setColor(new Color(255, 255, 255, 12));
                g.fillRoundRect(12, 2, getWidth() - 24, getHeight() - 4, 12, 12);
            }
            g.setFont(getFont());
            g.setColor(active ? UI.TEXT : UI.MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), 30, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    private static class Logo extends JComponent {
        Logo() {
            setPreferredSize(new Dimension(240, 56));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.antialias(g);
            g.setColor(UI.PANEL_ALT);
            g.fillRoundRect(24, 4, 36, 48, 12, 12);
            Color[] c = {UI.RED, UI.AMBER, UI.ACCENT};
            for (int i = 0; i < 3; i++) { g.setColor(c[i]); g.fillOval(34, 9 + i * 14, 16, 12); }
            g.setColor(UI.TEXT);
            g.setFont(UI.font(Font.BOLD, 15));
            g.drawString("SMART TRAFFIC", 72, 24);
            g.setColor(UI.MUTED);
            g.setFont(UI.font(Font.PLAIN, 11));
            g.drawString("Emergency Routing", 72, 41);
            g.dispose();
        }
    }
}
