import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;

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

//    public static void showAlert(String title, String message) {
//        Alert alert = new Alert(Alert.AlertType.ERROR);
//        alert.setTitle(title);
//        alert.setContentText(message);
//        alert.showAndWait();
//    }

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
        String sql = "SELECT id, username, password_hash, is_premium FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean isPremium = rs.getBoolean("is_premium");
                if (isPremium) {
                    throw new IllegalArgumentException("Este usuario es premium. Selecciona el modo premium.");
                }
                String storedHash = rs.getString("password_hash");
                if (PasswordUtil.checkPassword(password, storedHash)) {
                    return new ReviewUser(rs.getInt("id"), rs.getString("username"));
                }
            }
            throw new SQLException("Nombre de usuario o contraseña incorrectos.");
        }
    }

    public static ReviewUser loginPremiumUser(String username, String password) throws SQLException {
        String sql = "SELECT id, username, password_hash, is_premium FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean isPremium = rs.getBoolean("is_premium");
                if (!isPremium) {
                    throw new IllegalArgumentException("Este usuario no es premium. Selecciona el modo usuario.");
                }
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
        // Eliminar de premium_users primero (si existe)
        String sqlDeletePremium = "DELETE FROM premium_users WHERE username = ?";
        String sqlDeleteReview = "DELETE FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Eliminar de premium_users
            try (PreparedStatement stmtPremium = conn.prepareStatement(sqlDeletePremium)) {
                stmtPremium.setString(1, username);
                stmtPremium.executeUpdate();
            }
            // Eliminar de review_users
            try (PreparedStatement stmtReview = conn.prepareStatement(sqlDeleteReview)) {
                stmtReview.setString(1, username);
                int rows = stmtReview.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("El usuario " + username + " no existe.");
                }
            }
        }
    }

    public static ReviewUser registerPremiumUser(String username, String password) throws SQLException {
        // Primero, insertar en premium_users
        String sqlPremium = "INSERT INTO premium_users (username, password_hash) VALUES (?, ?) RETURNING id";
        // Luego, insertar en review_users con is_premium=true
        String sqlReview = "INSERT INTO review_users (username, password_hash, is_premium) VALUES (?, ?, true) RETURNING id";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                // premium_users
                try (PreparedStatement stmt = conn.prepareStatement(sqlPremium)) {
                    stmt.setString(1, username);
                    stmt.setString(2, PasswordUtil.hashPassword(password));
                    stmt.executeQuery();
                }
                // review_users
                int id;
                try (PreparedStatement stmt = conn.prepareStatement(sqlReview)) {
                    stmt.setString(1, username);
                    stmt.setString(2, PasswordUtil.hashPassword(password));
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        id = rs.getInt("id");
                    } else {
                        throw new SQLException("No se pudo registrar el usuario premium en review_users.");
                    }
                }
                conn.commit();
                return new ReviewUser(id, username);
            } catch (SQLException e) {
                conn.rollback();
                if (e.getSQLState().equals("23505")) { // Unique violation
                    throw new SQLException("El nombre de usuario ya está en uso.");
                }
                throw e;
            }
        }
    }

    public static void deletePremiumUser(String username, String password) throws SQLException {
        String sqlSelect = "SELECT password_hash FROM review_users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmtSelect = conn.prepareStatement(sqlSelect)) {
            stmtSelect.setString(1, username);
            ResultSet rs = stmtSelect.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (!PasswordUtil.checkPassword(password, storedHash)) {
                    throw new SQLException("Contraseña incorrecta.");
                }
            } else {
                throw new SQLException("El usuario no existe.");
            }

            String sqlDelete = "DELETE FROM premium_users WHERE username = ?";
            try (PreparedStatement stmtDelete = conn.prepareStatement(sqlDelete)) {
                stmtDelete.setString(1, username);
                stmtDelete.executeUpdate();
            }
        }
    }
}

