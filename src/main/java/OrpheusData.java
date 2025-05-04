import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OrpheusData {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/your_database";
    private static final String DB_USER = "your_user";
    private static final String DB_PASSWORD = "your_password";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public void saveAccessToken(String accessToken, long expirationTime) throws SQLException {
        String query = "INSERT INTO spotify_tokens (access_token, expiration_time) VALUES (?, ?)";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, accessToken);
            stmt.setLong(2, expirationTime);
            stmt.executeUpdate();
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
}