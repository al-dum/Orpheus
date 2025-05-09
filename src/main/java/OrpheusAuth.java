import static spark.Spark.*;

import java.sql.Connection;
import okhttp3.*;
import java.sql.*;
import org.json.JSONObject; // si usas org.json
public class OrpheusAuth {

    public static void main(String[] args) {
        port(8080);

        get("/", (req, res) -> "Hello! Go to /login to connect with Spotify.");

        get("/login", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/spotify_auth",
                    "postgres",
                    "")) {

                String sql = "SELECT access_token FROM spotify_tokens ORDER BY created_at DESC LIMIT 1";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    if (rs.next()) {
                        // Ya hay un token almacenado
                        return "🔐 Ya estás autenticado con Spotify. Token en base de datos.";
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "❌ Error al verificar tokens: " + e.getMessage();
            }

            // Si no hay token, redirigimos a Spotify
            String clientId = "0e003a2eb0a7493c86917c5bc3eb5297";
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

                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);

                String accessToken = json.getString("access_token");
                String refreshToken = json.getString("refresh_token");
                int expiresIn = json.getInt("expires_in");

                // Guardar en PostgreSQL
                try (Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql://localhost:5432/spotify_auth", // cambia si usas otro puerto/base
                        "postgres",                                      // tu usuario
                        "")) {                                            // tu contraseña (si tienes una)

                    String sql = "INSERT INTO spotify_tokens (access_token, refresh_token, expires_in) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, accessToken);
                        stmt.setString(2, refreshToken);
                        stmt.setInt(3, expiresIn);
                        stmt.executeUpdate();
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