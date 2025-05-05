import java.sql.*;

public class OrpheusData {
    private static final String DB_URL = "jdbc:sqlite:orpheus.db";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void saveAccessToken(String accessToken, long expirationTime, String refreshToken) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS spotify_tokens (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "access_token TEXT NOT NULL, " +
                "expiration_time INTEGER, " +
                "refresh_token TEXT)";
        String query = "INSERT INTO spotify_tokens (access_token, expiration_time, refresh_token) VALUES (?, ?, ?)";

        try (Connection conn = connect()) {
            conn.createStatement().execute(createTable);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, accessToken);
                stmt.setLong(2, expirationTime);
                stmt.setString(3, refreshToken);
                stmt.executeUpdate();
            }
        }
    }

    public TokenData getTokenData() throws SQLException {
        String query = "SELECT access_token, expiration_time, refresh_token FROM spotify_tokens ORDER BY id DESC LIMIT 1";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TokenData(
                        rs.getString("access_token"),
                        rs.getLong("expiration_time"),
                        rs.getString("refresh_token")
                );
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

    // Inner class to hold token data
    public static class TokenData {
        public final String accessToken;
        public final long expirationTime;
        public final String refreshToken;

        public TokenData(String accessToken, long expirationTime, String refreshToken) {
            this.accessToken = accessToken;
            this.expirationTime = expirationTime;
            this.refreshToken = refreshToken;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
    }
}