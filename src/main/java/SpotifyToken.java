import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.sql.*;

/**
 * Unified token handling class for Spotify authentication.
 * Handles token storage, retrieval, and refresh operations.
 */
public class SpotifyToken {
    private String accessToken;
    private long expirationTimeMillis;
    private String refreshToken;
    private static OrpheusData dataStore = new OrpheusData();

    private static final String DB_URL = "jdbc:sqlite:spotify_tokens.db";
    private static final String DB_USER = "";
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
            String sql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, accessToken);
            stmt.setString(2, refreshToken);
            stmt.setInt(3, (int) (expirationTimeMillis - System.currentTimeMillis()) / 1000);
            stmt.executeUpdate();
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
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String accessToken = rs.getString("access_token");
                String refreshToken = rs.getString("refresh_token");
                int expiresIn = rs.getInt("expires_in");
                Timestamp createdAt = rs.getTimestamp("created_at");

                long currentTime = System.currentTimeMillis();
                long tokenTime = createdAt.getTime();
                if ((currentTime - tokenTime) >= expiresIn * 1000L) {
                    JsonObject refreshed = JsonParser.parseString(SpotifyClient.refreshAccessToken(refreshToken)).getAsJsonObject();
                    String newAccessToken = refreshed.get("access_token").getAsString();
                    int newExpiresIn = refreshed.get("expires_in").getAsInt();

                    String updateSql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
                    PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                    updateStmt.setString(1, newAccessToken);
                    updateStmt.setString(2, refreshToken);
                    updateStmt.setInt(3, newExpiresIn);
                    updateStmt.executeUpdate();

                    String deleteOld = "DELETE FROM spotify_tokens WHERE id NOT IN (SELECT id FROM spotify_tokens ORDER BY created_at DESC LIMIT 1)";
                    PreparedStatement deleteStmt = conn.prepareStatement(deleteOld);
                    deleteStmt.executeUpdate();

                    return new SpotifyToken(newAccessToken, System.currentTimeMillis() + (newExpiresIn * 1000L), refreshToken);
                } else {
                    return new SpotifyToken(accessToken, System.currentTimeMillis() + (expiresIn * 1000L), refreshToken);
                }
            }
        }
        return null;
    }

    public static void clear() throws SQLException {
        dataStore.deleteAccessToken();
    }
}