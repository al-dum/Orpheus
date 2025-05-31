import java.io.IOException;
import java.sql.*;

public class SpotifyToken {
    private String accessToken;
    private long expirationTimeMillis;
    private String refreshToken;
    private static OrpheusData dataStore = new OrpheusData();

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "";

    public SpotifyToken(String accessToken, long expirationTimeMillis, String refreshToken) {
        this.accessToken = accessToken;
        this.expirationTimeMillis = expirationTimeMillis;
        this.refreshToken = refreshToken;
    }

    public SpotifyToken(String accessToken, int expiresInSeconds, String refreshToken) {
        this(accessToken, System.currentTimeMillis() + (expiresInSeconds * 1000L), refreshToken);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTimeMillis;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpirationTimeMillis() {
        return expirationTimeMillis;
    }

    public void save() throws SQLException {
        dataStore.saveAccessToken(accessToken, expirationTimeMillis, refreshToken);
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in, expiration_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, accessToken);
                stmt.setString(2, refreshToken);
                stmt.setInt(3, (int) ((expirationTimeMillis - System.currentTimeMillis()) / 1000));
                stmt.setLong(4, expirationTimeMillis);
                stmt.executeUpdate();
                System.out.println("Token saved: " + accessToken);
            }
        }
    }

    public static SpotifyToken load() throws SQLException {
        OrpheusData.TokenData tokenData = dataStore.getTokenData();
        if (tokenData == null) {
            return null;
        }
        return new SpotifyToken(tokenData.accessToken, tokenData.expirationTime, tokenData.refreshToken);
    }

    public static SpotifyToken getValidToken() throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT * FROM spotify_tokens ORDER BY created_at DESC LIMIT 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String accessToken = rs.getString("access_token");
                    String refreshToken = rs.getString("refresh_token");
                    long expirationTime = rs.getLong("expiration_time");

                    if (System.currentTimeMillis() >= expirationTime) {
                        System.out.println("Token expired, refreshing...");
                        String refreshedToken = SpotifyClient.refreshAccessToken(refreshToken);

                        // Get new expiration time (default 1 hour)
                        int newExpiresIn = 3600;
                        long newExpirationTime = System.currentTimeMillis() + (newExpiresIn * 1000L);

                        // Save new token
                        String updateSql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in, expiration_time) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setString(1, refreshedToken);
                            updateStmt.setString(2, refreshToken);
                            updateStmt.setInt(3, newExpiresIn);
                            updateStmt.setLong(4, newExpirationTime);
                            updateStmt.executeUpdate();
                        }

                        // Clean old tokens
                        String deleteOld = "DELETE FROM spotify_tokens WHERE id NOT IN (SELECT id FROM spotify_tokens ORDER BY created_at DESC LIMIT 1)";
                        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteOld)) {
                            deleteStmt.executeUpdate();
                        }

                        return new SpotifyToken(refreshedToken, newExpirationTime, refreshToken);
                    } else {
                        return new SpotifyToken(accessToken, expirationTime, refreshToken);
                    }
                }
            }
        }
        return null;
    }

    public static void clear() throws SQLException {
        dataStore.deleteAccessToken();
    }
}