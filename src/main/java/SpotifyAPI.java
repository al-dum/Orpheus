import java.io.*;
import java.net.*;
import java.util.Base64;

/**
 * @deprecated This class contains redundant functionality that is already provided by SpotifyClient.
 * Use SpotifyClient instead for all Spotify API interactions.
 */
@Deprecated
public class SpotifyAPI {
    private static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297";
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933";

    public static String getAccessToken() throws IOException {
        // Encode credentials in Base64
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        // Request token
        URL url = new URL("https://accounts.spotify.com/api/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic " + encodedCredentials);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        // Send request body
        String body = "grant_type=client_credentials";
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes());
        os.flush();

        // Read response
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();

        // Parse JSON to get token (using simple string parsing for demo)
        String jsonResponse = response.toString();
        String accessToken = jsonResponse.split("\"access_token\":\"")[1].split("\"")[0];
        return accessToken;
    }

    public static String getTrackInfo(String trackId, String accessToken) throws IOException {
        URL url = new URL("https://api.spotify.com/v1/tracks/" + trackId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        // Read response
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();

        return response.toString();
    }

    public static void main(String[] args) throws IOException {
        String accessToken = getAccessToken();
        String trackId = "6rqhFgbbKwnb9MLmUQDhG6"; // "Blinding Lights" by The Weeknd
        String trackInfo = getTrackInfo(trackId, accessToken);
        System.out.println("Track Info: " + trackInfo);
    }
}
