import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.GraphicsEnvironment;

/** Entry point: opens the database (creating tables and demo data on first run) and shows the main window. */
public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            fatal("The MySQL JDBC driver was not found.\nAdd mysql-connector-j-*.jar to the lib folder and start the program with run.bat.");
            return;
        }
        try {
            DatabaseSetup.init();
        } catch (RuntimeException e) {
            fatal("Could not open the database:\n" + e.getMessage());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            UIManager.put("ToolTip.background", new ColorUIResource(UI.PANEL_ALT));
            UIManager.put("ToolTip.foreground", new ColorUIResource(UI.TEXT));
            UIManager.put("ToolTip.border", BorderFactory.createLineBorder(UI.BORDER));
            UIManager.put("ToolTip.font", UI.font(java.awt.Font.PLAIN, 12));
            new MainFrame().setVisible(true);
        });
    }

    private static void fatal(String message) {
        System.err.println(message);
        if (!GraphicsEnvironment.isHeadless())
            JOptionPane.showMessageDialog(null, message, "Smart Traffic Management", JOptionPane.ERROR_MESSAGE);
    }
}
