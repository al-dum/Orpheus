import java.sql.*;

public class OrpheusData {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "";

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public void saveAccessToken(String accessToken, long expirationTime, String refreshToken) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS spotify_tokens (" +
                "id SERIAL PRIMARY KEY, " +
                "access_token TEXT NOT NULL, " +
                "expiration_time BIGINT, " +
                "refresh_token TEXT, " +
                "expires_in INTEGER, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        String query = "INSERT INTO spotify_tokens (access_token, expiration_time, refresh_token, expires_in) VALUES (?, ?, ?, ?)";

        try (Connection conn = connect()) {
            conn.createStatement().execute(createTable);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, accessToken);
                stmt.setLong(2, expirationTime);
                stmt.setString(3, refreshToken);
                stmt.setInt(4, (int) ((expirationTime - System.currentTimeMillis()) / 1000));
                stmt.executeUpdate();
            }
        }
    }

    public TokenData getTokenData() throws SQLException {
        String query = "SELECT access_token, expiration_time, refresh_token, expires_in FROM spotify_tokens ORDER BY created_at DESC LIMIT 1";
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