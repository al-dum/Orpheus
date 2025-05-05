import java.net.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import javafx.application.Application;
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
/**
 * Interfaz gráfica de usuario para interactuar con la API de Spotify.
 * Permite iniciar sesión, ver el perfil del usuario, buscar canciones por nombre y agregarlas a la biblioteca.
 * También maneja la autorización OAuth2 y la comunicación con la API de Spotify.
 */
public class OrpheusTUI extends Application {
    private static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297";
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933";
    private static final String REDIRECT_URI = "https://sites.google.com/view/orpheus-app/p%C3%A1gina-principal";

    private static String accessToken;
    private static OrpheusData orpheusData = new OrpheusData();

    // Configura el cliente OkHttp para usar el proxy del sistema
    private static final OkHttpClient client = configureClientWithSystemProxy();

    private static OkHttpClient configureClientWithSystemProxy() {
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
            List<Proxy> proxies = ProxySelector.getDefault().select(uri);
            if (!proxies.isEmpty() && proxies.get(0).type() != Proxy.Type.DIRECT) {
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
     * Verifica si hay conexión a internet intentando hacer una solicitud HTTP a api.spotify.com.
     * Utiliza el cliente OkHttp configurado con los ajustes de proxy del sistema.
     * @return true si se puede acceder, false si no.
     */
    private boolean isInternetAvailable() {
        try {
            Request request = new Request.Builder()
                    .url("https://api.spotify.com/v1/ping")
                    .head()  // Solo solicita los encabezados, no el cuerpo completo
                    .build();

            try (Response response = client.newCall(request).execute()) {
                boolean isSuccessful = response.isSuccessful() || response.code() == 401;  // 401 significa que necesitamos autenticación, pero el servidor está disponible
                System.out.println("Internet available: " + isSuccessful + " (HTTP status: " + response.code() + ")");
                return isSuccessful;
            }
        } catch (IOException e) {
            System.out.println("Internet check failed: " + e.getMessage());
            return false;
        }
    }
    /**
     * Función principal que lanza la aplicación JavaFX y ofrece opciones por consola para prueba.
     * @param args Argumentos de la línea de comandos.
     */
    public static void main(String[] args) throws SQLException {
        System.out.printf("Iniciando aplicación...\n");
        launch(args);

        accessToken = orpheusData.getAccessToken();
        if (accessToken == null) {
            System.out.println("No hay token guardado. Inicia sesión con la interfaz gráfica.");
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
            String scope = URLEncoder.encode("user-top-read user-read-recently-played user-library-modify",
                                             StandardCharsets.UTF_8);
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + scope;

            getHostServices().showDocument(authUrl);

            spark.Spark.get("/callback", (req, res) -> {
                String authCode = req.queryParams("code");
                if (authCode != null) {
                    try {
                        JsonObject tokenResponse = getTokenResponse(authCode);
                        String accessToken = tokenResponse.get("access_token").getAsString();
                        String refreshToken = tokenResponse.get("refresh_token").getAsString();
                        long expirationTime = System.currentTimeMillis() +
                                (tokenResponse.get("expires_in").getAsLong() * 1000);

                        orpheusData.saveAccessToken(accessToken, expirationTime, refreshToken);
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





    private JsonObject getTokenResponse(String authCode) throws IOException {
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
            return JsonParser.parseString(jsonResponse).getAsJsonObject();
        }
    }


    /**
     * Intercambia el código de autorización por un token de acceso usando OAuth2.
     * @param authCode Código de autorización recibido tras el login.
     * @return Token de acceso válido.
     * @throws IOException Si ocurre un error de red o de respuesta.
     */
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
            if (!response.isSuccessful()) {
                throw new IOException("Error al obtener token de acceso: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!json.has("access_token")) {
                throw new IOException("La respuesta no contiene un token de acceso: " + jsonResponse);
            }
            return json.get("access_token").getAsString();
        }
    }

    /**
     * Busca una canción por nombre en la API de Spotify y devuelve su ID.
     * @param query Nombre de la canción.
     * @param accessToken Token de acceso del usuario.
     * @return ID de la primera canción encontrada.
     * @throws IOException Si ocurre un error en la búsqueda.
     */
    private String searchTrackIdByName(String query, String accessToken) throws IOException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://api.spotify.com/v1/search?q=" + encodedQuery + "&type=track&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error al buscar canción: " + response.body().string());
            }
            String jsonResponse = response.body().string();
            JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
            if (items.size() > 0) {
                JsonObject firstTrack = items.get(0).getAsJsonObject();
                return firstTrack.get("id").getAsString();
            } else {
                throw new IOException("No se encontraron resultados.");
            }
        }
    }

    /**
     * Obtiene las canciones más escuchadas del usuario.
     * @param accessToken Token de acceso del usuario.
     * @return JSON con las canciones más escuchadas.
     * @throws IOException Si ocurre un error al hacer la solicitud.
     */
    private String getTopTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error al obtener canciones más escuchadas: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Añade una canción a la biblioteca del usuario en Spotify.
     * @param trackId ID de la canción a añadir.
     * @param accessToken Token de acceso del usuario.
     * @throws IOException Si ocurre un error al hacer la solicitud.
     */
    private static void addTrackToLibrary(String trackId, String accessToken) throws IOException {
        RequestBody body = RequestBody.create("", null); // Cuerpo vacío para esta solicitud
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/tracks?ids=" + trackId)
                .header("Authorization", "Bearer " + accessToken)
                .put(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Canción añadida a tu biblioteca.");
            } else {
                System.out.println("Error al añadir canción: " + response.body().string());
            }
        }
    }

    /**
     * Obtiene el perfil del usuario autenticado desde la API de Spotify.
     * @param accessToken Token de acceso del usuario.
     * @return JSON con información del perfil.
     * @throws IOException Si ocurre un error en la solicitud.
     */
    private static String getUserProfile(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                throw new IOException("Error al obtener perfil: " + response.body().string());
            }
        }
    }

    /**
     * Obtiene las últimas canciones reproducidas por el usuario.
     * @param accessToken Token de acceso del usuario.
     * @return JSON con las canciones reproducidas recientemente.
     * @throws IOException Si ocurre un error al consultar la API.
     */
    private String getRecentlyPlayedTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me/player/recently-played?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error al obtener canciones reproducidas recientemente: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
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
        OrpheusData.TokenData tokenData = orpheusData.getTokenData();

        if (tokenData == null) {
            throw new IOException("No token available. Please login first.");
        }

        if (tokenData.isExpired()) {
            String newAccessToken = refreshAccessToken(tokenData.refreshToken);
            long newExpirationTime = System.currentTimeMillis() + (3600 * 1000); // 1 hour
            orpheusData.saveAccessToken(newAccessToken, newExpirationTime, tokenData.refreshToken);
            return newAccessToken;
        }

        return tokenData.accessToken;
    }

    private String refreshAccessToken(String refreshToken) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
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
}