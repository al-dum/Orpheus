import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;

public class ReviewUserManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5433/reviews_db";
    private static final String DB_USER = "orpheusers";
    private static final String DB_PASSWORD = "munyun214";

    public static class ReviewUser {
        public final int id;
        public final String username;

        public ReviewUser(int id, String username) {
            this.id = id;
            this.username = username;
        }
    }

    public static ReviewUser registerUser(String username, String password) throws SQLException {
        String sql = "INSERT INTO review_users (username, password_hash) VALUES (?, ?) RETURNING id";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, PasswordUtil.hashPassword(password));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                return new ReviewUser(id, username);
            }
            return null;
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) { // Unique violation
                throw new SQLException("El nombre de usuario ya está en uso.");
            }
            throw e;
        }
    }

    public static ReviewUser loginUser(String username, String password) throws SQLException {
        String sql = "SELECT id, username, password_hash FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (PasswordUtil.checkPassword(password, storedHash)) {
                    return new ReviewUser(rs.getInt("id"), rs.getString("username"));
                }
            }
            throw new SQLException("Nombre de usuario o contraseña incorrectos.");
        }
    }

    public static ReviewUser getUserByUsername(String username) throws SQLException {
        String sql = "SELECT id, username FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new ReviewUser(rs.getInt("id"), rs.getString("username"));
            }
            return null;
        }
    }

    public static void clearUsers() throws SQLException {
        String sql = "DELETE FROM review_users";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    public static void exportUsersToCsv(File file) throws SQLException, IOException {
        String sql = "SELECT id, username, created_at FROM review_users ORDER BY created_at";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery();
             BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("ID,Username,Created_At\n");
            while (rs.next()) {
                writer.write(String.format("%d,%s,%s\n",
                                           rs.getInt("id"),
                                           escapeCsv(rs.getString("username")),
                                           rs.getTimestamp("created_at").toString()
                ));
            }
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public static void deleteUser(String username) throws SQLException {
        String sql = "DELETE FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("El usuario " + username + " no existe.");
            }
        }
    }
}