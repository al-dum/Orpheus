import static spark.Spark.*;
import java.sql.*;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import okhttp3.Request;

public class OrpheusAuth {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = ""; // Add your password here

    public static void main(String[] args) {
        port(8080);

        get("/", (req, res) -> "Hello! Go to /login to connect with Spotify.");

        get("/login", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = "SELECT access_token FROM spotify_tokens ORDER BY created_at DESC LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return "🔐 Ya estás autenticado con Spotify. Token en base de datos.";
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "❌ Error al verificar tokens: " + e.getMessage();
            }

            String clientId = System.getenv("SPOTIFY_CLIENT_ID");
            String redirectUri = "http://localhost:8080/callback";
            String scope = "user-top-read";
            String authUrl = "https://accounts.spotify.com/authorize"
                    + "?client_id=" + clientId
                    + "&response_type=code"
                    + "&redirect_uri=" + redirectUri
                    + "&scope=" + scope;

            res.redirect(authUrl);
            return null;
        });

        get("/callback", (req, res) -> {
            String error = req.queryParams("error");
            if (error != null) {
                return "Spotify error: " + error;
            }

            String code = req.queryParams("code");
            if (code == null) {
                return "Authorization failed or was canceled.";
            }

            String clientId = System.getenv("SPOTIFY_CLIENT_ID");
            String clientSecret = System.getenv("SPOTIFY_CLIENT_SECRET");
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

                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                String accessToken = json.getString("access_token");
                String refreshToken = json.getString("refresh_token");
                int expiresIn = json.getInt("expires_in");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, accessToken);
                        stmt.setString(2, refreshToken);
                        stmt.setInt(3, expiresIn);
                        stmt.executeUpdate();
                    }

                    String deleteOld = "DELETE FROM spotify_tokens WHERE id NOT IN (SELECT id FROM spotify_tokens ORDER BY created_at DESC LIMIT 1)";
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteOld)) {
                        deleteStmt.executeUpdate();
                    }

                    return "✅ Tokens guardados correctamente en PostgreSQL";
                } catch (SQLException e) {
                    e.printStackTrace();
                    return "❌ Error al guardar tokens: " + e.getMessage();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Error during token exchange: " + e.getMessage();
            }
        });
    }
}