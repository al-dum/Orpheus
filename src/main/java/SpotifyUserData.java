import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import spark.Spark;

import java.io.IOException;
import java.util.Base64;

public class SpotifyUserData {
    private static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297";
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final OkHttpClient client = new OkHttpClient();

    public static void main(String[] args) {
        // Start a local server for OAuth callback
        Spark.port(8080);

        // Step 1: Generate Spotify Auth URL
        String authUrl = "https://accounts.spotify.com/authorize?" +
                "client_id=" + CLIENT_ID +
                "&response_type=code" +
                "&redirect_uri=" + REDIRECT_URI +
                "&scope=user-top-read user-read-recently-played"; // Requested permissions

        System.out.println("Open this URL in your browser and log in:");
        System.out.println(authUrl);

        // Step 2: Handle callback after user login
        Spark.get("/callback", (req, res) -> {
            String authCode = req.queryParams("code");
            if (authCode == null) {
                return "Error: No auth code received.";
            }

            // Step 3: Exchange auth code for access token
            String accessToken = getAccessToken(authCode);

            // Step 4: Fetch user data (e.g., top tracks)
            String topTracks = getTopTracks(accessToken);
            return "Your Top Tracks: " + topTracks;
        });
    }

    private static String getAccessToken(String authCode) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", REDIRECT_URI)
                .build();

        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String jsonResponse = response.body().string();
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            return json.get("access_token").getAsString();
        }
    }

    private static String getTopTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?limit=10") // Top 10 tracks
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
}