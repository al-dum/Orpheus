import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import spark.Spark;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;

public class OrpheusTUI extends Application {
    private static OrpheusData orpheusData = new OrpheusData();
    private TextArea resultArea;

    public static void main(String[] args) {
        Spark.port(8080);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Spotify User Data");

        Spark.get("/callback", (req, res) -> {
            String authCode = req.queryParams("code");
            if (authCode != null) {
                try {
                    JsonObject tokenResponse = SpotifyClient.getTokenResponse(authCode);
                    String accessToken = tokenResponse.get("access_token").getAsString();
                    String refreshToken = tokenResponse.get("refresh_token").getAsString();
                    int expiresIn = tokenResponse.get("expires_in").getAsInt();

                    SpotifyToken token = new SpotifyToken(accessToken, expiresIn, refreshToken);
                    token.save();
                    Platform.runLater(() -> resultArea.setText("Login exitoso!"));
                } catch (IOException | SQLException e) {
                    Platform.runLater(() -> resultArea.setText("Error en login: " + e.getMessage()));
                }
            }
            return "Puedes cerrar esta ventana.";
        });

        Button loginButton = new Button("Iniciar sesión en Spotify");
        resultArea = new TextArea();
        resultArea.setEditable(false);

        Button addTrackButton = new Button("Añadir canción a biblioteca");
        Button profileButton = new Button("Ver perfil");
        Button createPlaylistButton = new Button("Crear Playlist");
        Button topTracksButton = new Button("Ver Top Tracks");
        Button topArtistsButton = new Button("Ver Top Artistas");

        loginButton.setOnAction(event -> {
            try {
                SpotifyToken token = SpotifyToken.getValidToken();
                if (token != null) {
                    resultArea.setText("Ya estás autenticado con Spotify.");
                    return;
                }
            } catch (SQLException | IOException e) {
                resultArea.setText("Error al verificar tokens: " + e.getMessage());
                return;
            }

            if (!SpotifyClient.isInternetAvailable()) {
                resultArea.setText("No hay conexión a Internet. Por favor, verifica tu conexión e intenta nuevamente.");
                return;
            }

            String scope = URLEncoder.encode("user-top-read user-read-recently-played user-library-modify playlist-modify-private",
                                             StandardCharsets.UTF_8);
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + SpotifyClient.CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyClient.REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + scope;

            getHostServices().showDocument(authUrl);
        });

        addTrackButton.setOnAction(event -> {
            try {
                if (!SpotifyClient.isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }

                String accessToken = getValidAccessToken();
                javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
                dialog.setTitle("Añadir canción");
                dialog.setHeaderText("Introduce el nombre de la canción");
                dialog.setContentText("Nombre:");
                dialog.showAndWait().ifPresent(songName -> {
                    try {
                        String trackId = SpotifyClient.searchTrackIdByName(songName, accessToken);
                        SpotifyClient.addTrackToLibrary(trackId, accessToken);
                        resultArea.setText("Canción añadida exitosamente");
                    } catch (IOException e) {
                        resultArea.setText("Error al añadir canción: " + e.getMessage());
                    }
                });
            } catch (SQLException | IOException e) {
                resultArea.setText("Error: " + e.getMessage());
            }
        });

        profileButton.setOnAction(event -> {
            try {
                if (!SpotifyClient.isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }

                String accessToken = getValidAccessToken();
                String profile = SpotifyClient.getUserProfile(accessToken);
                resultArea.setText("Perfil de usuario:\n" + profile);
            } catch (SQLException | IOException e) {
                resultArea.setText("Error al obtener perfil: " + e.getMessage());
            }
        });

        createPlaylistButton.setOnAction(event -> createPlaylistAndAddTracks());

        topTracksButton.setOnAction(event -> {
            try {
                if (!SpotifyClient.isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }

                String accessToken = getValidAccessToken();
                displayTopTracks(accessToken);
            } catch (SQLException | IOException e) {
                resultArea.setText("Error al obtener top tracks: " + e.getMessage());
            }
        });

        topArtistsButton.setOnAction(event -> {
            try {
                if (!SpotifyClient.isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }

                String accessToken = getValidAccessToken();
                displayTopArtists(accessToken);
            } catch (SQLException | IOException e) {
                resultArea.setText("Error al obtener top artistas: " + e.getMessage());
            }
        });

        VBox layout = new VBox(10, loginButton, addTrackButton, profileButton, createPlaylistButton, topTracksButton, topArtistsButton, resultArea);
        Scene scene = new Scene(layout, 400, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void displayTopTracks(String accessToken) throws IOException {
        try {
            String topTracksJson = SpotifyClient.getTopTracks(accessToken);
            org.json.JSONObject json = new org.json.JSONObject(topTracksJson);
            org.json.JSONArray items = json.getJSONArray("items");

            StringBuilder tracksText = new StringBuilder("Tus Pistas Principales:\n");
            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject track = items.getJSONObject(i);
                String trackName = track.getString("name");
                org.json.JSONArray artistsArray = track.getJSONArray("artists");

                StringBuilder artistNames = new StringBuilder();
                for (int j = 0; j < artistsArray.length(); j++) {
                    org.json.JSONObject artist = artistsArray.getJSONObject(j);
                    artistNames.append(artist.getString("name"));
                    if (j < artistsArray.length() - 1) artistNames.append(", ");
                }
                tracksText.append(i + 1).append(". ").append(trackName).append(" - ").append(artistNames).append("\n");
            }

            Platform.runLater(() -> resultArea.setText(tracksText.toString()));
        } catch (Exception e) {
            Platform.runLater(() -> resultArea.setText("Error al obtener top tracks: " + e.getMessage()));
        }
    }

    private void displayTopArtists(String accessToken) throws IOException {
        try {
            String topArtistsJson = SpotifyClient.getTopArtists(accessToken);
            org.json.JSONObject json = new org.json.JSONObject(topArtistsJson);
            org.json.JSONArray items = json.getJSONArray("items");

            StringBuilder artistsText = new StringBuilder("Tus Artistas Principales:\n");
            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject artist = items.getJSONObject(i);
                String artistName = artist.getString("name");
                artistsText.append(i + 1).append(". ").append(artistName).append("\n");
            }

            Platform.runLater(() -> resultArea.setText(artistsText.toString()));
        } catch (Exception e) {
            Platform.runLater(() -> resultArea.setText("Error al obtener top artistas: " + e.getMessage()));
        }
    }

    private void createPlaylistAndAddTracks() {
        try {
            if (!SpotifyClient.isInternetAvailable()) {
                resultArea.setText("No hay conexión a Internet.");
                return;
            }

            String accessToken = getValidAccessToken();
            javafx.scene.control.TextInputDialog playlistDialog = new javafx.scene.control.TextInputDialog();
            playlistDialog.setTitle("Crear Playlist");
            playlistDialog.setHeaderText("Introduce el nombre de la playlist");
            playlistDialog.setContentText("Nombre:");
            String playlistName = playlistDialog.showAndWait().orElse(null);

            if (playlistName == null || playlistName.isEmpty()) {
                resultArea.setText("Nombre de playlist no proporcionado.");
                return;
            }

            String playlistId = SpotifyPlaylistManager.createPlaylist(accessToken, playlistName, "Playlist creada desde OrpheusTUI");
            javafx.scene.control.TextInputDialog tracksDialog = new javafx.scene.control.TextInputDialog();
            tracksDialog.setTitle("Agregar Canciones");
            tracksDialog.setHeaderText("Introduce los URIs de las canciones separados por comas");
            tracksDialog.setContentText("URIs:");
            String trackUrisInput = tracksDialog.showAndWait().orElse(null);

            if (trackUrisInput == null || trackUrisInput.isEmpty()) {
                resultArea.setText("No se proporcionaron canciones.");
                return;
            }

            List<String> trackUris = List.of(trackUrisInput.split(","));
            SpotifyPlaylistManager.addTracksToPlaylist(accessToken, playlistId, trackUris);
            resultArea.setText("Playlist creada y canciones añadidas exitosamente.");
        } catch (IOException | SQLException e) {
            resultArea.setText("Error al crear la playlist o agregar canciones: " + e.getMessage());
        }
    }

    private String getValidAccessToken() throws SQLException, IOException {
        SpotifyToken token = SpotifyToken.getValidToken();
        if (token == null) {
            throw new IOException("No token available. Please login first.");
        }
        return token.getAccessToken();
    }
}