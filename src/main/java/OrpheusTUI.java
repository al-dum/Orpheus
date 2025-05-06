import java.net.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import okhttp3.OkHttpClient;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;
/**
 * Interfaz gráfica de usuario para interactuar con la API de Spotify.
 * Permite iniciar sesión, ver el perfil del usuario, buscar canciones por nombre y agregarlas a la biblioteca.
 * También maneja la autorización OAuth2 y la comunicación con la API de Spotify.
 */
public class OrpheusTUI extends Application {
    private static String accessToken;
    private static OrpheusData orpheusData = new OrpheusData();

    // Configura el cliente OkHttp para usar el proxy del sistema
    private static final OkHttpClient client = configureClientWithSystemProxy();

    /**
     * @deprecated Use SpotifyClient.configureClientWithSystemProxy() instead
     */
    @Deprecated
    private static OkHttpClient configureClientWithSystemProxy() {
        return SpotifyClient.configureClientWithSystemProxy();
    }

    /**
     * Verifica si hay conexión a internet.
     * @return true si se puede acceder, false si no.
     */
    private boolean isInternetAvailable() {
        return SpotifyClient.isInternetAvailable();
    }
    /**
     * Función principal que lanza la aplicación JavaFX y ofrece opciones por consola para prueba.
     * @param args Argumentos de la línea de comandos.
     */
    public static void main(String[] args) throws SQLException {
        System.out.printf("Iniciando aplicación...\n");
        launch(args);

        try {
            if (orpheusData != null) {
                OrpheusData.TokenData tokenData = orpheusData.getTokenData();
                if (tokenData != null && !tokenData.isExpired()) {
                    accessToken = tokenData.accessToken;
                } else {
                    System.out.println("El token ha expirado o no está disponible");
                }
            } else {
                System.out.println("Error: orpheusData no está inicializado");
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener el token de acceso: " + e.getMessage());
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("Opciones:");
        System.out.println("1. Añadir canción a biblioteca");
        System.out.println("2. Ver perfil");
        int option = scanner.nextInt();

        switch (option) {
            case 1:
                System.out.println("Introduce el ID de la canción:");
                String trackId = scanner.next();
                try {
                    addTrackToLibrary(trackId, accessToken);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case 2:
                try {
                    String profile = getUserProfile(accessToken);
                    System.out.println("Perfil de usuario:\n" + profile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                System.out.println("Opción no válida.");
        }
    }

    /**
     * Inicializa la interfaz gráfica de usuario con botones para iniciar sesión,
     * agregar canciones por nombre y ver el perfil del usuario.
     * @param primaryStage Ventana principal de la aplicación.
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Spotify User Data");

        Button loginButton = new Button("Iniciar sesión en Spotify");
        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);

        Button addTrackButton = new Button("Añadir canción a biblioteca");
        Button profileButton = new Button("Ver perfil");
        Button makePlaylistButton = new Button("Crear Playlist");

        addTrackButton.setOnAction(event -> {
            try {
                if (!isInternetAvailable()) {
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
                        String trackId = searchTrackIdByName(songName, accessToken);
                        addTrackToLibrary(trackId, accessToken);
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
                if (!isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }

                String accessToken = getValidAccessToken();
                String profile = getUserProfile(accessToken);
                resultArea.setText("Perfil de usuario:\n" + profile);
            } catch (SQLException | IOException e) {
                resultArea.setText("Error al obtener perfil: " + e.getMessage());
            }
        });

        loginButton.setOnAction(event -> {
            if (!isInternetAvailable()) {
                resultArea.setText("No hay conexión a Internet. Por favor, verifica tu conexión e intenta nuevamente.");
                return;
            }

            String scope = URLEncoder.encode("user-top-read user-read-recently-played user-library-modify",
                                             StandardCharsets.UTF_8);
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + SpotifyClient.CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyClient.REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + scope;

            getHostServices().showDocument(authUrl);

            spark.Spark.get("/callback", (req, res) -> {
                String authCode = req.queryParams("code");
                if (authCode != null) {
                    try {
                        JsonObject tokenResponse = getTokenResponse(authCode);
                        String accessToken = tokenResponse.get("access_token").getAsString();
                        String refreshToken = tokenResponse.get("refresh_token").getAsString();
                        int expiresIn = tokenResponse.get("expires_in").getAsInt();

                        SpotifyToken token = new SpotifyToken(accessToken, expiresIn, refreshToken);
                        token.save();
                        resultArea.setText("Login exitoso!");
                    } catch (IOException | SQLException e) {
                        resultArea.setText("Error en login: " + e.getMessage());
                    }
                }
                return "Puedes cerrar esta ventana.";
            });
            spark.Spark.init();
        });

        VBox layout = new VBox(10, loginButton, addTrackButton, profileButton, resultArea);
        Scene scene = new Scene(layout, 400, 300);
        primaryStage.setScene(scene);
        primaryStage.show();
    }





    /**
     * @deprecated Use SpotifyClient.getTokenResponse(String) directly instead
     */
    @Deprecated
    private JsonObject getTokenResponse(String authCode) throws IOException {
        return SpotifyClient.getTokenResponse(authCode);
    }


    /**
     * Intercambia el código de autorización por un token de acceso usando OAuth2.
     * @param authCode Código de autorización recibido tras el login.
     * @return Token de acceso válido.
     * @throws IOException Si ocurre un error de red o de respuesta.
     * @deprecated Use SpotifyClient.getAccessToken(String) directly instead
     */
    @Deprecated
    private String getAccessToken(String authCode) throws IOException {
        return SpotifyClient.getAccessToken(authCode);
    }

    /**
     * Busca una canción por nombre en la API de Spotify y devuelve su ID.
     * @param query Nombre de la canción.
     * @param accessToken Token de acceso del usuario.
     * @return ID de la primera canción encontrada.
     * @throws IOException Si ocurre un error en la búsqueda.
     * @deprecated Use SpotifyClient.searchTrackIdByName(String, String) directly instead
     */
    @Deprecated
    private String searchTrackIdByName(String query, String accessToken) throws IOException {
        return SpotifyClient.searchTrackIdByName(query, accessToken);
    }

    /**
     * Obtiene las canciones más escuchadas del usuario.
     * @param accessToken Token de acceso del usuario.
     * @return JSON con las canciones más escuchadas.
     * @throws IOException Si ocurre un error al hacer la solicitud.
     * @deprecated Use SpotifyClient.getTopTracks(String) directly instead
     */
    @Deprecated
    private String getTopTracks(String accessToken) throws IOException {
        return SpotifyClient.getTopTracks(accessToken);
    }

    /**
     * Añade una canción a la biblioteca del usuario en Spotify.
     * @param trackId ID de la canción a añadir.
     * @param accessToken Token de acceso del usuario.
     * @throws IOException Si ocurre un error al hacer la solicitud.
     * @deprecated Use SpotifyClient.addTrackToLibrary(String, String) directly instead
     */
    @Deprecated
    private static void addTrackToLibrary(String trackId, String accessToken) throws IOException {
        SpotifyClient.addTrackToLibrary(trackId, accessToken);
        System.out.println("Canción añadida a tu biblioteca.");
    }

    /**
     * Obtiene el perfil del usuario autenticado desde la API de Spotify.
     * @param accessToken Token de acceso del usuario.
     * @return JSON con información del perfil.
     * @throws IOException Si ocurre un error en la solicitud.
     * @deprecated Use SpotifyClient.getUserProfile(String) directly instead
     */
    @Deprecated
    private static String getUserProfile(String accessToken) throws IOException {
        return SpotifyClient.getUserProfile(accessToken);
    }

    /**
     * Obtiene las últimas canciones reproducidas por el usuario.
     * @param accessToken Token de acceso del usuario.
     * @return JSON con las canciones reproducidas recientemente.
     * @throws IOException Si ocurre un error al consultar la API.
     * @deprecated Use SpotifyClient.getRecentlyPlayedTracks(String) directly instead
     */
    @Deprecated
    private String getRecentlyPlayedTracks(String accessToken) throws IOException {
        return SpotifyClient.getRecentlyPlayedTracks(accessToken);
    }

    /**
     * Inicializa botones para añadir canciones por ID y mostrar el perfil del usuario (usado para pruebas).
     */
    private void initializeButtons() {
        Button addTrackButton = new Button("Añadir canción a biblioteca");
        addTrackButton.setOnAction(event -> {
            try {
                addTrackToLibrary("track_id_aquí", accessToken); // Reemplaza con un ID válido
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Button profileButton = new Button("Ver perfil");
        profileButton.setOnAction(event -> {
            try {
                String profile = getUserProfile(accessToken);
                System.out.println("Perfil de usuario:\n" + profile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // Check and refresh token if needed
    private String getValidAccessToken() throws SQLException, IOException {
        SpotifyToken token = SpotifyToken.getValidToken();
        if (token == null) {
            throw new IOException("No token available. Please login first.");
        }
        return token.getAccessToken();
    }

    /**
     * @deprecated Use SpotifyClient.refreshAccessToken(String) directly instead
     */
    @Deprecated
    private String refreshAccessToken(String refreshToken) throws IOException {
        return SpotifyClient.refreshAccessToken(refreshToken);
    }

    public static void getTopTracks(Stage stage) throws IOException {
        try {
            String topTracksJson = SpotifyClient.getTopTracks(accessToken);
            JSONObject json = new JSONObject(topTracksJson);
            JSONArray items = json.getJSONArray("items");

            StringBuilder tracksText = new StringBuilder("Tus Pistas Principales:\n");

            for (int i = 0; i < items.length(); i++) {
                JSONObject track = items.getJSONObject(i);
                String trackName = track.getString("name");
                JSONArray artistsArray = track.getJSONArray("artists");

                StringBuilder artistNames = new StringBuilder();
                for (int j = 0; j < artistsArray.length(); j++) {
                    JSONObject artist = artistsArray.getJSONObject(j);
                    artistNames.append(artist.getString("name"));
                    if (j < artistsArray.length() - 1) artistNames.append(", ");
                }
                System.out.println(trackName + " by " + artistNames);
                tracksText.append(i + 1).append(". ").append(trackName).append(" - ").append(artistNames).append("\n");
            }

            Platform.runLater(() -> {
                TextArea textArea = new TextArea(tracksText.toString());
                textArea.setEditable(false);

                VBox layout = new VBox(10, textArea);
                Scene scene = new Scene(layout, 400, 300);

                stage.setTitle("Top Tracks");
                stage.setScene(scene);
                stage.show();
            });
        } catch (Exception e) {
            System.err.println("Error getting top tracks: " + e.getMessage());
            Platform.runLater(() -> {
                TextArea textArea = new TextArea("Error getting top tracks: " + e.getMessage());
                textArea.setEditable(false);

                VBox layout = new VBox(10, textArea);
                Scene scene = new Scene(layout, 400, 300);

                stage.setTitle("Error");
                stage.setScene(scene);
                stage.show();
            });
        }
    }

    private void createPlaylistAndAddTracks() {
        try {
            if (!isInternetAvailable()) {
                System.out.println("No hay conexión a Internet.");
                return;
            }

            String accessToken = getValidAccessToken();

            // Solicitar al usuario el nombre de la playlist
            javafx.scene.control.TextInputDialog playlistDialog = new javafx.scene.control.TextInputDialog();
            playlistDialog.setTitle("Crear Playlist");
            playlistDialog.setHeaderText("Introduce el nombre de la playlist");
            playlistDialog.setContentText("Nombre:");
            String playlistName = playlistDialog.showAndWait().orElse(null);

            if (playlistName == null || playlistName.isEmpty()) {
                System.out.println("Nombre de playlist no proporcionado.");
                return;
            }

            // Crear la playlist
            String playlistId = SpotifyPlaylistManager.createPlaylist(accessToken, playlistName, "Playlist creada desde OrpheusTUI");

            // Solicitar al usuario las canciones a agregar
            javafx.scene.control.TextInputDialog tracksDialog = new javafx.scene.control.TextInputDialog();
            tracksDialog.setTitle("Agregar Canciones");
            tracksDialog.setHeaderText("Introduce los URIs de las canciones separados por comas");
            tracksDialog.setContentText("URIs:");
            String trackUrisInput = tracksDialog.showAndWait().orElse(null);

            if (trackUrisInput == null || trackUrisInput.isEmpty()) {
                System.out.println("No se proporcionaron canciones.");
                return;
            }

            List<String> trackUris = List.of(trackUrisInput.split(","));
            SpotifyPlaylistManager.addTracksToPlaylist(accessToken, playlistId, trackUris);

            System.out.println("Playlist creada y canciones añadidas exitosamente.");
        } catch (IOException | SQLException e) {
            System.err.println("Error al crear la playlist o agregar canciones: " + e.getMessage());
        }
    }

    private void initializePlaylistButtons(VBox layout) {
        Button createPlaylistButton = new Button("Crear Playlist");
        createPlaylistButton.setOnAction(event -> createPlaylistAndAddTracks());

        layout.getChildren().add(createPlaylistButton);
    }
}