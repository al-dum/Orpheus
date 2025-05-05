import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.*;

public class OrpheusData {
    private static final String DB_URL = "jdbc:sqlite:orpheus.db";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void saveAccessToken(String accessToken, long expirationTime) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS spotify_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT, access_token TEXT NOT NULL, expiration_time INTEGER);";
        String query = "INSERT INTO spotify_tokens (access_token, expiration_time) VALUES (?, ?)";
        try (Connection conn = connect()) {
            conn.createStatement().execute(createTable);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, accessToken);
                stmt.setLong(2, expirationTime);
                stmt.executeUpdate();
            }
        }
    }

    public String getAccessToken() throws SQLException {
        String query = "SELECT access_token FROM spotify_tokens ORDER BY id DESC LIMIT 1";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("access_token");
            }
        }
        return null;
    }

    public void deleteAccessToken() throws SQLException {
        String query = "DELETE FROM spotify_tokens";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
}