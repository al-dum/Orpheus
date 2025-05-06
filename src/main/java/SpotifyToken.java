import java.io.IOException;
import java.sql.SQLException;

/**
 * Unified token handling class for Spotify authentication.
 * Handles token storage, retrieval, and refresh operations.
 */
public class SpotifyToken {
    private String accessToken;
    private long expirationTimeMillis;
    private String refreshToken;
    private static OrpheusData dataStore = new OrpheusData();

    public SpotifyToken(String accessToken, long expirationTimeMillis, String refreshToken) {
        this.accessToken = accessToken;
        this.expirationTimeMillis = expirationTimeMillis;
        this.refreshToken = refreshToken;
    }

    /**
     * Creates a token with the specified expiration time in seconds from now.
     */
    public SpotifyToken(String accessToken, int expiresInSeconds, String refreshToken) {
        this(accessToken, System.currentTimeMillis() + (expiresInSeconds * 1000L), refreshToken);
    }

    /**
     * Checks if the token is expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTimeMillis;
    }

    /**
     * Gets the access token.
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Gets the refresh token.
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Gets the expiration time in milliseconds.
     */
    public long getExpirationTimeMillis() {
        return expirationTimeMillis;
    }

    /**
     * Saves the token to the database.
     */
    public void save() throws SQLException {
        dataStore.saveAccessToken(accessToken, expirationTimeMillis, refreshToken);
    }

    /**
     * Loads the most recent token from the database.
     */
    public static SpotifyToken load() throws SQLException {
        OrpheusData.TokenData tokenData = dataStore.getTokenData();
        if (tokenData == null) {
            return null;
        }
        return new SpotifyToken(tokenData.accessToken, tokenData.expirationTime, tokenData.refreshToken);
    }

    /**
     * Gets a valid token, refreshing if necessary.
     */
    public static SpotifyToken getValidToken() throws SQLException, IOException {
        SpotifyToken token = load();
        if (token == null) {
            return null;
        }

        if (token.isExpired() && token.refreshToken != null) {
            String newAccessToken = SpotifyClient.refreshAccessToken(token.refreshToken);
            // Assume 1 hour validity for refreshed tokens
            token = new SpotifyToken(newAccessToken, 3600, token.refreshToken);
            token.save();
        }

        return token;
    }

    /**
     * Clears the token from the database.
     */
    public static void clear() throws SQLException {
        dataStore.deleteAccessToken();
    }
}
