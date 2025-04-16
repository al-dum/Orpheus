import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Base64;

public class SpotifyExample {
    private static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297"; // Replace with yours!
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933"; // Replace!
    private static final OkHttpClient client = new OkHttpClient();

    public static void main(String[] args) throws IOException {
        // Step 1: Get Access Token
        String accessToken = getAccessToken();
        System.out.println("Access Token: " + accessToken);

        // Step 2: Get Track Info (Example: "Blinding Lights" by The Weeknd)
        String trackId = "6rqhFgbbKwnb9MLmUQDhG6";
        String trackInfo = getTrackInfo(trackId, accessToken);

        // Step 3: Parse JSON Response
        JSONObject json = new JSONObject(trackInfo);
        String trackName = json.getString("name");
        String artist = json.getJSONArray("artists").getJSONObject(0).getString("name");

        System.out.println("Track: " + trackName);
        System.out.println("Artist: " + artist);
    }

    // Method to get Spotify Access Token
    private static String getAccessToken() throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to get token: " + response);
            JSONObject json = new JSONObject(response.body().string());
            return json.getString("access_token");
        }
    }

    // Method to fetch track data from Spotify API
    private static String getTrackInfo(String trackId, String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/tracks/" + trackId)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed: " + response);
            return response.body().string();
        }
    }
}