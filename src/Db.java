import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Small JDBC helper. Every statement uses ? placeholders (PreparedStatement), so user input can never inject SQL. */
final class Db {
    private Db() { }

    interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }
    interface TxWork { void run(Connection c) throws SQLException; }

    static class DataAccessException extends RuntimeException {
        DataAccessException(String message, Throwable cause) { super(message, cause); }
    }

    static String friendly(SQLException e) {
        String m = String.valueOf(e.getMessage()).toLowerCase();
        if (m.contains("foreign key")) return "This change conflicts with related data (a linked junction, road or vehicle is missing or still in use).";
        if (m.contains("unique") || m.contains("primary key")) return "A record with this ID already exists.";
        if (m.contains("check constraint")) return "One of the values is outside the allowed range.";
        if (m.contains("not null")) return "A required field is empty.";
        return e.getMessage();
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = DatabaseConnection.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<T>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DataAccessException(friendly(e), e);
        }
    }

    /** Raw rows (one Object[] per row) - used to fill JTables. */
    static List<Object[]> rows(String sql, Object... params) {
        return query(sql, new RowMapper<Object[]>() {
            public Object[] map(ResultSet rs) throws SQLException {
                ResultSetMetaData md = rs.getMetaData();
                Object[] row = new Object[md.getColumnCount()];
                for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
                return row;
            }
        }, params);
    }

    static long scalar(String sql, Object... params) {
        List<Long> r = query(sql, new RowMapper<Long>() {
            public Long map(ResultSet rs) throws SQLException { return rs.getLong(1); }
        }, params);
        return r.isEmpty() ? 0L : r.get(0);
    }

    static int update(String sql, Object... params) {
        try (Connection c = DatabaseConnection.connect()) {
            return update(c, sql, params);
        } catch (SQLException e) {
            throw new DataAccessException(friendly(e), e);
        }
    }

    static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    static long insert(String sql, Object... params) {
        try (Connection c = DatabaseConnection.connect();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { return keys.next() ? keys.getLong(1) : -1L; }
        } catch (SQLException e) {
            throw new DataAccessException(friendly(e), e);
        }
    }

    /** Runs several statements atomically: all succeed or none are saved. */
    static void transaction(TxWork work) {
        try (Connection c = DatabaseConnection.connect()) {
            c.setAutoCommit(false);
            try {
                work.run(c);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException ignore) { }
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(friendly(e), e);
        }
    }
}
