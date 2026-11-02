import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** MySQL connection settings can be supplied with JVM properties or environment variables. */
public class DatabaseConnection {
    private static String setting(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.trim().isEmpty()) value = System.getenv(env);
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }

    public static String databaseName() {
        return setting("traffic.db.name", "TRAFFIC_DB_NAME", "smart_traffic");
    }

    public static String dbDescription() {
        return setting("traffic.db.host", "TRAFFIC_DB_HOST", "localhost") + "/" + databaseName();
    }

    public static Connection connect() throws SQLException {
        String host = setting("traffic.db.host", "TRAFFIC_DB_HOST", "localhost");
        String port = setting("traffic.db.port", "TRAFFIC_DB_PORT", "3306");
        String user = setting("traffic.db.user", "TRAFFIC_DB_USER", "root");
        String password = setting("traffic.db.password", "TRAFFIC_DB_PASSWORD", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
        return DriverManager.getConnection(url, user, password);
    }
}
