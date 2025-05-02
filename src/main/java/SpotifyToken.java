public class SpotifyToken {
    private String accessToken;
    private long expirationTimeMillis; // cuándo expira el token

    public SpotifyToken(String accessToken, int expiresInSeconds) {
        this.accessToken = accessToken;
        this.expirationTimeMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTimeMillis;
    }

    public String getAccessToken() {
        return accessToken;
    }
}