import okhttp3.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;

public class SpotifyPlaylistManager {
    private static final String BASE_URL = "https://api.spotify.com/v1/";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * Crea una nueva playlist para el usuario autenticado.
     * @param accessToken Token de acceso del usuario.
     * @param playlistName Nombre de la playlist.
     * @param description Descripción de la playlist.
     * @return ID de la playlist creada.
     * @throws IOException Si ocurre un error en la solicitud.
     */
    public static String createPlaylist(String accessToken, String playlistName, String description) throws IOException {
        // Obtener el ID del usuario
        Request userRequest = new Request.Builder()
                .url(BASE_URL + "me")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response userResponse = client.newCall(userRequest).execute()) {
            if (!userResponse.isSuccessful()) {
                throw new IOException("Error al obtener el ID del usuario: " + userResponse.body().string());
            }

            String userJson = userResponse.body().string();
            JsonObject userObject = JsonParser.parseString(userJson).getAsJsonObject();
            String userId = userObject.get("id").getAsString();

            // Crear la playlist
            JsonObject playlistBody = new JsonObject();
            playlistBody.addProperty("name", playlistName);
            playlistBody.addProperty("description", description);
            playlistBody.addProperty("public", false);

            RequestBody body = RequestBody.create(
                    playlistBody.toString(),
                    MediaType.parse("application/json")
            );

            Request playlistRequest = new Request.Builder()
                    .url(BASE_URL + "users/" + userId + "/playlists")
                    .header("Authorization", "Bearer " + accessToken)
                    .post(body)
                    .build();

            try (Response playlistResponse = client.newCall(playlistRequest).execute()) {
                if (!playlistResponse.isSuccessful()) {
                    throw new IOException("Error al crear la playlist: " + playlistResponse.body().string());
                }

                String playlistJson = playlistResponse.body().string();
                JsonObject playlistObject = JsonParser.parseString(playlistJson).getAsJsonObject();
                return playlistObject.get("id").getAsString();
            }
        }
    }

    /**
     * Agrega canciones a una playlist existente.
     * @param accessToken Token de acceso del usuario.
     * @param playlistId ID de la playlist.
     * @param trackUris Lista de URIs de las canciones a agregar.
     * @throws IOException Si ocurre un error en la solicitud.
     */
    public static void addTracksToPlaylist(String accessToken, String playlistId, List<String> trackUris) throws IOException {
        JsonObject tracksBody = new JsonObject();
        tracksBody.addProperty("uris", String.join(",", trackUris));

        RequestBody body = RequestBody.create(
                tracksBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(BASE_URL + "playlists/" + playlistId + "/tracks")
                .header("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Error al agregar canciones a la playlist: " + response.body().string());
            }
        }
    }
}