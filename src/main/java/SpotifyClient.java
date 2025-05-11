import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import okhttp3.*;

/**
 * Centralized client for Spotify API calls.
 * This class handles all communication with the Spotify API using OkHttp.
 */
public class SpotifyClient {
    public static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297";
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933";
    public static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String API_BASE_URL = "https://api.spotify.com/v1";
    private static final String AUTH_URL = "https://accounts.spotify.com/api/token";

    private static final OkHttpClient client = configureClientWithSystemProxy();

    /**
     * Configures OkHttp client with system proxy settings.
     */
    public static OkHttpClient configureClientWithSystemProxy() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // Optional: Handle proxy authentication via environment variables
        String proxyUser = System.getenv("PROXY_USER");
        String proxyPass = System.getenv("PROXY_PASS");
        if (proxyUser != null && proxyPass != null) {
            builder.proxyAuthenticator((route, response) -> {
                String credential = Credentials.basic(proxyUser, proxyPass);
                return response.request().newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build();
            });
        }

        // Detect and apply system proxy
        try {
            URI uri = new URI("https://api.spotify.com");
            List<java.net.Proxy> proxies = java.net.ProxySelector.getDefault().select(uri);
            if (!proxies.isEmpty() && proxies.get(0).type() != java.net.Proxy.Type.DIRECT) {
                builder.proxy(proxies.get(0));
                System.out.println("Using system proxy: " + proxies.get(0));
            } else {
                System.out.println("No proxy detected, using direct connection.");
            }
        } catch (Exception e) {
            System.out.println("Error detecting system proxy: " + e.getMessage());
        }
        return builder.build();
    }

    /**
     * Checks if internet connection is available.
     * Uses multiple reliable endpoints to ensure a robust check.
     */
    public static boolean isInternetAvailable() {
        String[] reliableEndpoints = {
                "https://www.google.com",
                "https://www.cloudflare.com",
                "https://1.1.1.1",
                "https://api.spotify.com"
        };

        for (String endpoint : reliableEndpoints) {
            try {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .head()
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    boolean isSuccessful = response.isSuccessful() || response.code() == 401;
                    if (isSuccessful) {
                        System.out.println("Internet available: Connected to " + endpoint);
                        return true;
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to connect to " + endpoint + ": " + e.getMessage());
            }
        }

        System.out.println("Internet check failed: Could not connect to any reliable endpoint");
        return false;
    }

    /**
     * Gets token response as a JsonObject.
     */
    public static com.google.gson.JsonObject getTokenResponse(String authCode) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", REDIRECT_URI)
                .build();

        Request request = new Request.Builder()
                .url(AUTH_URL)
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting token response: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            return com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
        }
    }

    /**
     * Gets an access token using authorization code.
     */
    public static String getAccessToken(String authCode) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", REDIRECT_URI)
                .build();

        Request request = new Request.Builder()
                .url(AUTH_URL)
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting access token: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!json.has("access_token")) {
                throw new IOException("Response does not contain an access token: " + jsonResponse);
            }
            return json.get("access_token").getAsString();
        }
    }

    /**
     * Refreshes an access token using a refresh token.
     */
    public static String refreshAccessToken(String refreshToken) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build();

        Request request = new Request.Builder()
                .url(AUTH_URL)
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error refreshing token: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
            return json.get("access_token").getAsString();
        }
    }

    /**
     * Gets the user's profile information.
     */
    public static String getUserProfile(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting profile: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Gets the user's top tracks.
     */
    public static String getTopTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/top/tracks?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting top tracks: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Gets the user's top artists.
     */
    public static String getTopArtists(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/top/artists?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting top artists: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Gets the user's recently played tracks.
     */
    public static String getRecentlyPlayedTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/player/recently-played?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting recently played tracks: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Searches for a track by name and returns its ID.
     */
    public static String searchTrackIdByName(String query, String accessToken) throws IOException {
        String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = API_BASE_URL + "/search?q=" + encodedQuery + "&type=track&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error searching for track: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
            com.google.gson.JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
            if (items.size() > 0) {
                com.google.gson.JsonObject firstTrack = items.get(0).getAsJsonObject();
                return firstTrack.get("id").getAsString();
            } else {
                throw new IOException("No results found.");
            }
        }
    }

    /**
     * Adds a track to the user's library.
     */
    public static void addTrackToLibrary(String trackId, String accessToken) throws IOException {
        RequestBody body = RequestBody.create("", null);
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/tracks?ids=" + trackId)
                .header("Authorization", "Bearer " + accessToken)
                .put(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error adding track: " + response.code() + " - " + response.body().string());
            }
        }
    }

    /**
     * Gets information about a specific track.
     */
    public static String getTrackInfo(String trackId, String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/tracks/" + trackId)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting track info: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }  
}