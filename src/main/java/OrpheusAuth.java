import static spark.Spark.*;
import okhttp3.*;

public class OrpheusAuth {

    public static void main(String[] args) {
        port(8080);

        get("/", (req, res) -> "Hello! Go to /login to connect with Spotify.");

        get("/login", (req, res) -> {
            String clientId = "0e003a2eb0a7493c86917c5bc3eb5297";
            String redirectUri = "http://localhost:8080/callback";
            String scope = "user-top-read"; // Change this depending on your needs
            String authUrl = "https://accounts.spotify.com/authorize"
                    + "?client_id=" + clientId
                    + "&response_type=code"
                    + "&redirect_uri=" + redirectUri
                    + "&scope=" + scope;

            res.redirect(authUrl);
            return null;
        });

        get("/callback", (req, res) -> {
            String code = req.queryParams("code");

            if (code == null) {
                return "Authorization failed or was canceled.";
            }

            String clientId = "0e003a2eb0a7493c86917c5bc3eb5297";
            String clientSecret = "70e4f66551b84356aad1105e620e6933";
            String redirectUri = "http://localhost:8080/callback";

            OkHttpClient client = new OkHttpClient();

            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .build();

            Request request = new Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Failed to get access token: " + response.body().string();
                }

                return "Access Token Response: " + response.body().string();
            } catch (Exception e) {
                e.printStackTrace();
                return "Error during token exchange: " + e.getMessage();
            }
        });
    }
}