import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import okhttp3.*;

/**
 * La clase `SpotifyClient` proporciona métodos para interactuar con la API de Spotify.
 * Permite realizar autenticación, obtener información del usuario, buscar canciones y álbumes,
 * y gestionar la biblioteca del usuario.
 *
 * Funcionalidades principales:
 * <ul>
 *   <li>Autenticación y obtención de tokens de acceso</li>
 *   <li>Consulta de información del perfil del usuario</li>
 *   <li>Búsqueda de canciones y álbumes</li>
 *   <li>Gestión de la biblioteca del usuario (añadir canciones y álbumes)</li>
 *   <li>Obtención de información de canciones y álbumes</li>
 * </ul>
 *
 * @author al
 * @version 1.0
 * @since 2024-06-01
 */
public class SpotifyClient {
    public static final String CLIENT_ID = "0e003a2eb0a7493c86917c5bc3eb5297";
    private static final String CLIENT_SECRET = "70e4f66551b84356aad1105e620e6933";
    public static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String API_BASE_URL = "https://api.spotify.com/v1";
    private static final String AUTH_URL = "https://accounts.spotify.com/api/token";
    private static final OkHttpClient client = configureClientWithSystemProxy();

    /**
     * Configura un cliente OkHttp con proxy del sistema si está disponible.
     * Si las variables de entorno PROXY_USER y PROXY_PASS están defindas
     * */
    public static OkHttpClient configureClientWithSystemProxy() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
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
        try {
            URI uri = new URI("https://api.spotify.com");
            List<java.net.Proxy> proxies = java.net.ProxySelector.getDefault().select(uri);
            if (!proxies.isEmpty() && proxies.get(0).type() != java.net.Proxy.Type.DIRECT) {
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
     * Verifica si hay conexión a Internet intentando conectarse a varios endpoints confiables.
     * Si se puede conectar a cualquiera de ellos, se considera que hay conexión a Internet.
     *
     * @return true si hay conexión a Internet, false en caso contrario.
     */
    public static boolean isInternetAvailable() {
        String[] reliableEndpoints = {
                "https://www.google.com",
                "https://www.cloudflare.com",
                "https://1.1.1.1",
                "https://api.spotify.com"
        };
        for (String endpoint : reliableEndpoints) {
            try {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .head()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    boolean isSuccessful = response.isSuccessful() || response.code() == 401;
                    if (isSuccessful) {
                        System.out.println("Internet available: Connected to " + endpoint);
                        return true;
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to connect to " + endpoint + ": " + e.getMessage());
            }
        }
        System.out.println("Internet check failed: Could not connect to any reliable endpoint");
        return false;
    }

    /**
     * Obtiene la respuesta del token de acceso utilizando un código de autorización.
     *
     * @param authCode El código de autorización obtenido del flujo de autenticación.
     * @return Un objeto JsonObject que contiene la respuesta del token.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static com.google.gson.JsonObject getTokenResponse(String authCode) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", REDIRECT_URI)
                .build();
        Request request = new Request.Builder()
                .url(AUTH_URL)
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting token response: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            return com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
        }
    }

    public static String getAccessToken(String authCode) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("redirect_uri", REDIRECT_URI)
                .build();
        Request request = new Request.Builder()
                .url(AUTH_URL)
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting access token: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
            if (!json.has("access_token")) {
                throw new IOException("Response does not contain an access token: " + jsonResponse);
            }
            return json.get("access_token").getAsString();
        }
    }

    /**
     * Refresca el token de acceso utilizando un token de actualización.
     *
     * @param refreshToken El token de actualización obtenido previamente.
     * @return Un nuevo token de acceso.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String refreshAccessToken(String refreshToken) throws IOException {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build();
        Request request = new Request.Builder()
                .url(AUTH_URL)
                .header("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error refreshing token: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            try {
                JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
                return json.get("access_token").getAsString();
            } catch (Exception e) {
                if (jsonResponse.startsWith("BQ")) {
                    return jsonResponse;
                }
                throw new IOException("Invalid token response: " + jsonResponse);
            }
        }
    }

    /**
     * Obtiene el perfil del usuario autenticado en formato JSON.
     *
     * @param accessToken El token de acceso del usuario.
     * @return Un String con la información del perfil del usuario en formato JSON.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String getUserProfileJson(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting profile: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Obtiene el nombre de visualización del usuario autenticado.
     * Si el nombre de visualización no está disponible, se utiliza el ID del usuario.
     *
     * @param accessToken El token de acceso del usuario.
     * @return El nombre de visualización del usuario.
     * @throws IOException Si ocurre un error al obtener el perfil del usuario.
     */
    public static String getUserDisplayName(String accessToken) throws IOException {
        String json = getUserProfileJson(accessToken);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return obj.has("display_name") && !obj.get("display_name").isJsonNull()
                ? obj.get("display_name").getAsString()
                : obj.get("id").getAsString();
    }

    /**
     * Obtiene el ID del usuario autenticado.
     *
     * @param accessToken El token de acceso del usuario.
     * @return El ID del usuario.
     * @throws IOException Si ocurre un error al obtener el perfil del usuario.
     */
    public static String getTopTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/top/tracks?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting top tracks: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Obtiene los artistas más escuchados del usuario autenticado.
     *
     * @param accessToken El token de acceso del usuario.
     * @return Un String con la información de los artistas más escuchados en formato JSON.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String getTopArtists(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/top/artists?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting top artists: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Obtiene las pistas reproducidas recientemente por el usuario autenticado.
     *
     * @param accessToken El token de acceso del usuario.
     * @return Un String con la información de las pistas reproducidas recientemente en formato JSON.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String getRecentlyPlayedTracks(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/player/recently-played?limit=10")
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting recently played tracks: " + response.code() + " - " + response.body().string());
            }
            return response.body().string();
        }
    }

    /**
     * Busca un ID de pista por su nombre.
     *
     * @param query El nombre de la pista a buscar.
     * @param accessToken El token de acceso del usuario.
     * @return El ID de la pista encontrada.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String searchTrackIdByName(String query, String accessToken) throws IOException {
        String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = API_BASE_URL + "/search?q=" + encodedQuery + "&type=track&limit=1";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error searching for track: " + response.code() + " - " + response.body().string());
            }
            String jsonResponse = response.body().string();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonResponse).getAsJsonObject();
            com.google.gson.JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
            if (items.size() > 0) {
                com.google.gson.JsonObject firstTrack = items.get(0).getAsJsonObject();
                return firstTrack.get("id").getAsString();
            } else {
                throw new IOException("No results found.");
            }
        }
    }

    /**
     * Busca un ID de álbum por su nombre.
     *
     * @param albumName El nombre del álbum a buscar.
     * @param accessToken El token de acceso del usuario.
     * @return El ID del álbum encontrado.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String searchAlbumIdByName(String albumName, String accessToken) throws IOException {
        String url = API_BASE_URL + "/search?q=" + URLEncoder.encode(albumName, StandardCharsets.UTF_8) + "&type=album&limit=1";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error al buscar álbum: " + response.code());
            }
            String responseBody = response.body().string();
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            com.google.gson.JsonArray items = json.getAsJsonObject("albums").getAsJsonArray("items");
            if (items.size() == 0) {
                throw new IOException("Álbum no encontrado");
            }
            return items.get(0).getAsJsonObject().get("id").getAsString();
        }
    }

    /**
     * Añade una pista a la biblioteca del usuario.
     *
     * @param trackId El ID de la pista a añadir.
     * @param accessToken El token de acceso del usuario.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static void addTrackToLibrary(String trackId, String accessToken) throws IOException {
        RequestBody body = RequestBody.create("", null);
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/me/tracks?ids=" + trackId)
                .header("Authorization", "Bearer " + accessToken)
                .put(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error adding track: " + response.code() + " - " + response.body().string());
            }
        }
    }

    /**
     * Añade un álbum a la biblioteca del usuario.
     *
     * @param albumId El ID del álbum a añadir.
     * @param accessToken El token de acceso del usuario.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static void addAlbumToLibrary(String albumId, String accessToken) throws IOException {
        String url = API_BASE_URL + "/me/albums?ids=" + albumId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .put(RequestBody.create(new byte[0]))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error al añadir álbum: " + response.code());
            }
        }
    }

    /**
     * Obtiene información detallada de una pista por su ID.
     *
     * @param trackId El ID de la pista.
     * @param accessToken El token de acceso del usuario.
     * @return Un objeto JsonObject con la información de la pista.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static JsonObject getTrackInfo(String trackId, String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE_URL + "/tracks/" + trackId)
                .header("Authorization", "Bearer " + accessToken)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error getting track info: " + response.code() + " - " + response.body().string());
            }
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
    }

    /**
     * Obtiene la URL de la carátula del álbum de una pista por su ID.
     *
     * @param trackId El ID de la pista.
     * @param accessToken El token de acceso del usuario.
     * @return La URL de la carátula del álbum, o null si no se encuentra.
     * @throws IOException Si ocurre un error al realizar la solicitud HTTP.
     */
    public static String getAlbumCoverUrl(String trackId, String accessToken) throws IOException {
        JsonObject track = getTrackInfo(trackId, accessToken);
        JsonArray images = track.getAsJsonObject("album").getAsJsonArray("images");
        if (images.size() > 0) {
            return images.get(0).getAsJsonObject().get("url").getAsString();
        }
        return null;
    }
}