import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.util.List;
import java.util.*;

/** Draws the city graph: junctions, directed roads (with their own lane), signals, vehicles and highlighted routes. */
class NetworkPanel extends JPanel {
    static final int MW = 650, MH = 420;     // model space used by the x / y columns in the Junction table

    interface PickListener { void picked(int x, int y); }

    private final TrafficController controller;
    private List<RouteResult> routes = new ArrayList<RouteResult>();
    private PickListener pickListener;
    private Point preview;
    private float phase = 0f;
    private double scale = 1, ox, oy;
    private final javax.swing.Timer timer;

    NetworkPanel(TrafficController controller) {
        this.controller = controller;
        setOpaque(true);
        setBackground(UI.MAP_BG);
        setToolTipText("");
        timer = new javax.swing.Timer(70, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                phase = (phase + 1.6f) % 28f;
                if (!routes.isEmpty()) repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (pickListener == null || scale <= 0) return;
                int x = (int) Math.round((e.getX() - ox) / scale);
                int y = (int) Math.round((e.getY() - oy) / scale);
                x = Math.max(10, Math.min(MW - 10, x));
                y = Math.max(10, Math.min(MH - 10, y));
                preview = new Point(x, y);
                pickListener.picked(x, y);
                repaint();
            }
        });
    }

    @Override public void addNotify() { super.addNotify(); timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

    void setRoutes(List<RouteResult> r) { routes = r == null ? new ArrayList<RouteResult>() : r; repaint(); }
    void setPickListener(PickListener l) { pickListener = l; setCursor(l == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); }
    void setPreview(Integer x, Integer y) { preview = (x == null || y == null) ? null : new Point(x, y); repaint(); }

    private double X(double mx) { return ox + mx * scale; }
    private double Y(double my) { return oy + my * scale; }
    private int radius() { return (int) Math.max(5, Math.min(13, 14 * scale)); }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        UI.antialias(g);
        int pad = 30;
        scale = Math.min((getWidth() - 2.0 * pad) / MW, (getHeight() - 2.0 * pad) / MH);
        if (scale <= 0.05) { g.dispose(); return; }
        ox = (getWidth() - MW * scale) / 2;
        oy = (getHeight() - MH * scale) / 2;

        drawGrid(g);
        for (RoadSegment r : controller.roads.values()) drawRoad(g, r);
        drawRoutes(g);
        for (Junction j : controller.junctions.values()) drawJunction(g, j);
        if (scale >= 0.55) drawVehicles(g);
        drawPreview(g);
        g.dispose();
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(16, 26, 42));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(ox - 14, oy - 14, MW * scale + 28, MH * scale + 28, 18, 18));
        g.setColor(new Color(24, 38, 58));
        for (int x = 0; x <= MW; x += 50) g.draw(new Line2D.Double(X(x), oy, X(x), oy + MH * scale));
        for (int y = 0; y <= MH; y += 50) g.draw(new Line2D.Double(ox, Y(y), ox + MW * scale, Y(y)));
    }

    /** Geometry of one directed lane: trimmed to the junction circles and shifted sideways so both directions are visible. */
    private double[] lane(Junction a, Junction b, double off) {
        double ax = X(a.x), ay = Y(a.y), bx = X(b.x), by = Y(b.y);
        double dx = bx - ax, dy = by - ay, len = Math.max(1, Math.hypot(dx, dy));
        double ux = dx / len, uy = dy / len, nx = -uy, ny = ux, trim = radius() + 3;
        return new double[]{ax + ux * trim + nx * off, ay + uy * trim + ny * off, bx - ux * trim + nx * off, by - uy * trim + ny * off, ux, uy, nx, ny};
    }

    private void drawRoad(Graphics2D g, RoadSegment r) {
        Junction a = controller.junctions.get(r.from), b = controller.junctions.get(r.to);
        if (a == null || b == null) return;
        double off = Math.max(2.5, 4 * scale);
        double[] l = lane(a, b, off);
        Color c = r.closed ? UI.RED : (r.congested() ? UI.AMBER : new Color(66, 98, 132));
        if (r.closed) g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 4f}, 0f));
        else g.setStroke(new BasicStroke(r.congested() ? 2.4f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c);
        g.draw(new Line2D.Double(l[0], l[1], l[2], l[3]));
        g.setStroke(new BasicStroke(1.4f));
        // arrow head at 62 % of the lane
        double ax = l[0] + (l[2] - l[0]) * 0.62, ay = l[1] + (l[3] - l[1]) * 0.62, s = 5.5;
        g.setColor(r.closed ? UI.RED : (r.congested() ? UI.AMBER : new Color(96, 134, 172)));
        g.fill(new Polygon(
            new int[]{(int) (ax + l[4] * s), (int) (ax - l[4] * s * 0.6 + l[6] * s * 0.7), (int) (ax - l[4] * s * 0.6 - l[6] * s * 0.7)},
            new int[]{(int) (ay + l[5] * s), (int) (ay - l[5] * s * 0.6 + l[7] * s * 0.7), (int) (ay - l[5] * s * 0.6 - l[7] * s * 0.7)}, 3));
        if (scale >= 0.55) {
            double mx = (l[0] + l[2]) / 2 + l[6] * (off + 8), my = (l[1] + l[3]) / 2 + l[7] * (off + 8);
            g.setFont(UI.font(Font.PLAIN, 10));
            String t = r.closed ? "\u00d7" : r.travelTime + "m";
            FontMetrics fm = g.getFontMetrics();
            g.setColor(r.closed ? UI.RED : (r.congested() ? UI.AMBER : new Color(150, 175, 205)));
            g.drawString(t, (float) (mx - fm.stringWidth(t) / 2.0), (float) (my + fm.getAscent() / 2.0 - 1));
        }
    }

    private void drawRoutes(Graphics2D g) {
        for (RouteResult rr : routes) {
            if (!rr.found() || rr.path.size() < 2) continue;
            Color c = UI.vehicleColor(controller, rr.vehicleId);
            for (int i = 0; i + 1 < rr.path.size(); i++) {
                Junction a = controller.junctions.get(rr.path.get(i)), b = controller.junctions.get(rr.path.get(i + 1));
                if (a == null || b == null) continue;
                double[] l = lane(a, b, Math.max(2.5, 4 * scale));
                Line2D line = new Line2D.Double(l[0], l[1], l[2], l[3]);
                g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(UI.alpha(c, 55));
                g.draw(line);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(c);
                g.draw(line);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 10f}, -phase));
                g.setColor(Color.WHITE);
                g.draw(line);
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawJunction(Graphics2D g, Junction j) {
        int r = radius();
        double x = X(j.x), y = Y(j.y);
        g.setColor(new Color(15, 24, 39));
        g.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke(2f));
        g.setColor(UI.ACCENT);
        g.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        if (r >= 8) {
            g.setFont(UI.font(Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(UI.TEXT);
            g.drawString(j.id, (float) (x - fm.stringWidth(j.id) / 2.0), (float) (y + fm.getAscent() / 2.0 - 1));
        }
        String sig = controller.signalStatus.get(j.id);
        if (sig != null) {
            Color sc = "GREEN".equals(sig) ? UI.ACCENT : ("RED".equals(sig) ? UI.RED : UI.AMBER);
            int d = Math.max(5, (int) (7 * Math.min(1.2, scale + 0.3)));
            double sx = x + r * 0.75, sy = y - r * 0.75;
            g.setColor(new Color(11, 18, 30));
            g.fill(new java.awt.geom.Ellipse2D.Double(sx - d / 2.0 - 1.5, sy - d / 2.0 - 1.5, d + 3, d + 3));
            g.setColor(sc);
            g.fill(new java.awt.geom.Ellipse2D.Double(sx - d / 2.0, sy - d / 2.0, d, d));
        }
        if (scale >= 1.0) {
            g.setFont(UI.font(Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(UI.MUTED);
            g.drawString(j.name, (float) (x - fm.stringWidth(j.name) / 2.0), (float) (y + r + 13));
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawVehicles(Graphics2D g) {
        Map<String, Integer> stack = new HashMap<String, Integer>();
        g.setFont(UI.font(Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        for (EmergencyVehicle v : controller.vehicles.values()) {
            Junction j = controller.junctions.get(v.currentJunction);
            if (j == null) continue;
            Integer n = stack.get(j.id);
            int idx = n == null ? 0 : n;
            stack.put(j.id, idx + 1);
            int w = fm.stringWidth(v.id) + 12, h = 15;
            double x = X(j.x) + radius() + 5, y = Y(j.y) - h / 2.0 + idx * (h + 2) + 6;
            Color c = UI.vehicleColor(controller, v.id);
            g.setColor(c);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, 8, 8));
            g.setColor(new Color(12, 18, 30));
            g.drawString(v.id, (float) (x + 6), (float) (y + (h + fm.getAscent()) / 2.0 - 2));
        }
    }

    private void drawPreview(Graphics2D g) {
        if (preview == null) return;
        double x = X(preview.x), y = Y(preview.y);
        g.setColor(UI.AMBER);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 3f}, 0f));
        g.draw(new java.awt.geom.Ellipse2D.Double(x - 9, y - 9, 18, 18));
        g.setStroke(new BasicStroke(1f));
        g.fill(new java.awt.geom.Ellipse2D.Double(x - 2.5, y - 2.5, 5, 5));
    }

    @Override public String getToolTipText(MouseEvent e) {
        for (Junction j : controller.junctions.values()) {
            if (Math.hypot(e.getX() - X(j.x), e.getY() - Y(j.y)) <= radius() + 3) {
                StringBuilder sb = new StringBuilder(j.id + " \u2013 " + j.name);
                if (!j.location.isEmpty()) sb.append("  (").append(j.location).append(")");
                String sig = controller.signalStatus.get(j.id);
                if (sig != null) sb.append("   Signal: ").append(sig);
                for (EmergencyVehicle v : controller.vehicles.values())
                    if (v.currentJunction.equals(j.id)) sb.append("   [").append(v.id).append("]");
                return sb.toString();
            }
        }
        return null;
    }
}
