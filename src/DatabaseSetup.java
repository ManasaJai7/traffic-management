import java.sql.Connection;
import java.sql.Statement;

/** Creates the MySQL tables on first run and loads the demo city. Requires MySQL 8.0.16+. */
public class DatabaseSetup {

    private static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS Junction ("
            + " junction_id VARCHAR(20) PRIMARY KEY,"
            + " junction_name VARCHAR(120) NOT NULL,"
            + " location VARCHAR(160),"
            + " x INT NOT NULL DEFAULT 0,"
            + " y INT NOT NULL DEFAULT 0) ENGINE=InnoDB",

        "CREATE TABLE IF NOT EXISTS Road ("
            + " from_junction VARCHAR(20) NOT NULL,"
            + " to_junction VARCHAR(20) NOT NULL,"
            + " road_name VARCHAR(120),"
            + " normal_time INT NOT NULL CHECK (normal_time > 0),"
            + " travel_time INT NOT NULL CHECK (travel_time > 0),"
            + " closed TINYINT NOT NULL DEFAULT 0,"
            + " PRIMARY KEY (from_junction, to_junction),"
            + " CHECK (from_junction <> to_junction),"
            + " FOREIGN KEY (from_junction) REFERENCES Junction(junction_id) ON DELETE CASCADE,"
            + " FOREIGN KEY (to_junction) REFERENCES Junction(junction_id) ON DELETE CASCADE) ENGINE=InnoDB",

        "CREATE TABLE IF NOT EXISTS Vehicle ("
            + " vehicle_id VARCHAR(30) PRIMARY KEY,"
            + " vehicle_type VARCHAR(60) NOT NULL,"
            + " current_junction VARCHAR(20) NOT NULL,"
            + " FOREIGN KEY (current_junction) REFERENCES Junction(junction_id)) ENGINE=InnoDB",

        "CREATE TABLE IF NOT EXISTS TrafficSignal ("
            + " signal_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
            + " junction_id VARCHAR(20) NOT NULL,"
            + " signal_status VARCHAR(10) NOT NULL CHECK (signal_status IN ('GREEN','YELLOW','RED')),"
            + " green_seconds INT NOT NULL DEFAULT 30 CHECK (green_seconds BETWEEN 5 AND 180),"
            + " FOREIGN KEY (junction_id) REFERENCES Junction(junction_id) ON DELETE CASCADE) ENGINE=InnoDB",

        "CREATE TABLE IF NOT EXISTS RoutingRequest ("
            + " request_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
            + " vehicle_id VARCHAR(30) NOT NULL,"
            + " destination VARCHAR(20) NOT NULL,"
            + " severity VARCHAR(10) NOT NULL CHECK (severity IN ('CRITICAL','HIGH','NORMAL')),"
            + " status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
            + " created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " FOREIGN KEY (vehicle_id) REFERENCES Vehicle(vehicle_id) ON DELETE CASCADE,"
            + " FOREIGN KEY (destination) REFERENCES Junction(junction_id) ON DELETE CASCADE) ENGINE=InnoDB",

        "CREATE TABLE IF NOT EXISTS TrafficEvent ("
            + " event_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
            + " event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('CLOSURE','REOPEN','CONGESTION')),"
            + " from_junction VARCHAR(20) NOT NULL,"
            + " to_junction VARCHAR(20) NOT NULL,"
            + " new_time INT,"
            + " status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
            + " created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + " applied_at DATETIME,"
            + " FOREIGN KEY (from_junction, to_junction) REFERENCES Road(from_junction, to_junction) ON DELETE CASCADE) ENGINE=InnoDB",

        "CREATE TABLE IF NOT EXISTS RouteHistory ("
            + " history_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
            + " vehicle_id VARCHAR(30) NOT NULL,"
            + " start_junction VARCHAR(20),"
            + " destination VARCHAR(20) NOT NULL,"
            + " severity VARCHAR(10),"
            + " status VARCHAR(20),"
            + " path TEXT,"
            + " total_time INT,"
            + " created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB"
    };

    public static void init() {
        createTables();
        seedIfEmpty();
    }

    public static void createTables() {
        try (Connection con = DatabaseConnection.connect(); Statement st = con.createStatement()) {
            for (String sql : DDL) st.execute(sql);
        } catch (java.sql.SQLException e) {
            throw new Db.DataAccessException("Could not create the MySQL tables: " + e.getMessage(), e);
        }
    }

    public static void seedIfEmpty() {
        if (Db.scalar("SELECT COUNT(*) FROM Junction") == 0) seed();
    }

    /** Wipes every table and reloads the demo city. */
    public static void resetDemoData() {
        Db.transaction(c -> {
            String[] tables = {"TrafficEvent", "RoutingRequest", "RouteHistory", "TrafficSignal", "Vehicle", "Road", "Junction"};
            for (String t : tables) Db.update(c, "DELETE FROM " + t);
        });
        // MySQL ALTER TABLE implicitly commits, so reset counters after the delete transaction.
        try (Connection con = DatabaseConnection.connect(); Statement st = con.createStatement()) {
            String[] tables = {"TrafficEvent", "RoutingRequest", "RouteHistory", "TrafficSignal"};
            for (String t : tables) st.execute("ALTER TABLE " + t + " AUTO_INCREMENT = 1");
        } catch (java.sql.SQLException e) {
            throw new Db.DataAccessException("Could not reset MySQL auto-increment counters: " + e.getMessage(), e);
        }
        seed();
    }

    private static void seed() {
        Db.transaction(c -> {
            Object[][] junctions = {
                {"J1", "Central Junction", "Main Road", 80, 75},
                {"J2", "Bus Stand Junction", "Bus Stand Road", 220, 55},
                {"J3", "Market Junction", "Market Road", 370, 75},
                {"J4", "Railway Station", "Station Road", 520, 60},
                {"J5", "Government Hospital", "Hospital Road", 110, 205},
                {"J6", "Town Hall Square", "Town Hall Road", 270, 190},
                {"J7", "Collector Office", "Collectorate Road", 430, 205},
                {"J8", "Airport Junction", "Airport Road", 570, 205},
                {"J9", "Fire Station", "Fire Station Road", 75, 340},
                {"J10", "Medical College", "College Road", 235, 340},
                {"J11", "Stadium Junction", "Stadium Road", 405, 345},
                {"J12", "Bypass Junction", "Bypass Road", 555, 350}
            };
            for (Object[] j : junctions)
                Db.update(c, "INSERT INTO Junction(junction_id, junction_name, location, x, y) VALUES (?,?,?,?,?)", j);

            Object[][] pairs = {
                {"J1", "J2", 5, "Main Road"}, {"J2", "J3", 7, "Market Road"}, {"J3", "J4", 6, "Station Road"},
                {"J1", "J5", 8, "Hospital Road"}, {"J2", "J6", 5, "Town Hall Road"}, {"J3", "J6", 4, "Lake Road"},
                {"J4", "J8", 9, "Airport Road"}, {"J5", "J6", 3, "Cross Road"}, {"J6", "J7", 6, "Ring Road"},
                {"J7", "J8", 4, "Collectorate Road"}, {"J5", "J9", 5, "Fire Station Road"}, {"J9", "J10", 4, "College Road"},
                {"J6", "J10", 8, "Canal Road"}, {"J10", "J11", 5, "Stadium Road"}, {"J7", "J11", 4, "Park Road"},
                {"J8", "J12", 5, "Bypass Road"}, {"J11", "J12", 6, "Mill Road"}
            };
            String roadSql = "INSERT INTO Road(from_junction, to_junction, road_name, normal_time, travel_time, closed) VALUES (?,?,?,?,?,0)";
            for (Object[] p : pairs) {
                Db.update(c, roadSql, p[0], p[1], p[3], p[2], p[2]);
                Db.update(c, roadSql, p[1], p[0], p[3], p[2], p[2]);
            }

            Object[][] vehicles = {
                {"AMB-01", "Ambulance", "J1"}, {"FIRE-01", "Fire Engine", "J4"},
                {"RES-01", "Rescue", "J9"}, {"AMB-02", "Ambulance", "J12"}
            };
            for (Object[] v : vehicles)
                Db.update(c, "INSERT INTO Vehicle(vehicle_id, vehicle_type, current_junction) VALUES (?,?,?)", v);

            String[] statuses = {"GREEN", "RED", "YELLOW"};
            int[] greens = {30, 45, 60, 40};
            for (int i = 1; i <= 12; i++)
                Db.update(c, "INSERT INTO TrafficSignal(junction_id, signal_status, green_seconds) VALUES (?,?,?)",
                          "J" + i, statuses[i % 3], greens[i % 4]);
        });
    }
}
