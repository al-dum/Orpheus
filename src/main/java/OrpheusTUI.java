import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Base64;


public class OrpheusTUI extends Application {
    private static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297";
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final OkHttpClient client = new OkHttpClient();

    private String accessToken;

    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Spotify User Data");

        Button loginButton = new Button("Iniciar sesión en Spotify");
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);

        loginButton.setOnAction(event -> {
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + REDIRECT_URI +
                    "&scope=user-top-read user-read-recently-played";

            // Abre el navegador para iniciar sesión
            getHostServices().showDocument(authUrl);

            // Inicia un servidor para manejar el callback
            spark.Spark.get("/callback", (req, res) -> {
                String authCode = req.queryParams("code");
                if (authCode != null) {
                    try {
                        accessToken = getAccessToken(authCode);
                        String topTracks = getTopTracks(accessToken);
                        resultArea.setText("Tus canciones principales:\n" + topTracks);
                    } catch (IOException e) {
                        resultArea.setText("Error al obtener datos: " + e.getMessage());
                    }
                }
                return "Puedes cerrar esta ventana.";
            });
        });

        VBox layout = new VBox(10, loginButton, resultArea);
        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private String getAccessToken(String authCode) throws IOException {
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

    private String getTopTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
}
