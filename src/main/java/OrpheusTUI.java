import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Pair;
import spark.Spark;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;
import java.util.Optional;

public class OrpheusTUI extends Application implements ReviewVoteSystem.VoteUpdateListener {
    private ReviewVoteSystem voteSystem;
    private static final String DEFAULT_AVATAR = "https://i.scdn.co/image/ab6775700000ee8510e0a4a948b6e370d1cbda0f";
    private Stage primaryStage;
    private StackPane resultContainer;
    private TextArea resultArea;
    private int currentUserId = -1;
    private String currentUsername = "";
    private String currentAvatarUrl = DEFAULT_AVATAR;
    private String currentAccessToken = "";
    private TableView<ReviewManager.Review> reviewsTable = new TableView<>();
    private TableView<UserManager.UserProfile> usersTable = new TableView<>();
    private ObservableList<ReviewManager.Review> reviewsData = FXCollections.observableArrayList();
    private ObservableList<UserManager.UserProfile> usersData = FXCollections.observableArrayList();
    private static OrpheusData orpheusData = new OrpheusData();

    public static void main(String[] args) {
        Spark.port(8080);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        this.primaryStage = primaryStage;
        this.voteSystem = new ReviewVoteSystem(this);
        primaryStage.setTitle("Orpheus - Spotify Social");

        // Panel superior de usuario
        HBox userPanel = createUserPanel();

        // Contenedor de resultados
        resultContainer = new StackPane();
        resultContainer.getChildren().add(resultArea);
        ScrollPane resultScroll = new ScrollPane(resultContainer);
        resultScroll.setFitToWidth(true);
        resultScroll.setPrefHeight(300);

        // Pestañas principales
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
                createMusicTab(),
                createSocialTab(),
                createReviewsTab()
        );

        // Layout principal
        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(userPanel);
        mainLayout.setCenter(tabPane);
        mainLayout.setBottom(resultScroll);

        primaryStage.setScene(new Scene(mainLayout, 1000, 800));
        primaryStage.show();

        configureSpotifyCallback();

        new Thread(() -> {
            try {
                SpotifyToken token = SpotifyToken.getValidToken();
                if (token != null) {
                    currentAccessToken = token.getAccessToken();
                    String userProfileJson = SpotifyClient.getUserProfile(currentAccessToken);
                    org.json.JSONObject userProfile = new org.json.JSONObject(userProfileJson);
                    String spotifyId = userProfile.getString("id");
                    String username = userProfile.optString("display_name", spotifyId);
                    String avatarUrl = userProfile.has("images") && !userProfile.getJSONArray("images").isEmpty() ?
                            userProfile.getJSONArray("images").getJSONObject(0).getString("url") :
                            DEFAULT_AVATAR;

                    currentUserId = UserManager.getOrCreateUser(spotifyId, username, avatarUrl);
                    currentUsername = username;
                    currentAvatarUrl = avatarUrl;

                    Platform.runLater(() -> {
                        ((ImageView)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(0)).setImage(new Image(currentAvatarUrl));
                        ((Label)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(1)).setText(currentUsername);
                        resultArea.setText("¡Bienvenido, " + currentUsername + "!");
                    });
                }
            } catch (Exception e) {
                // Ignorar si no hay sesión previa
            }
        }).start();
    }

    private HBox createUserPanel() {
        ImageView avatarView = new ImageView(new Image(DEFAULT_AVATAR, 50, 50, true, true));
        Label usernameLabel = new Label("No autenticado");
        Button logoutButton = new Button("Cerrar sesión");

        logoutButton.setOnAction(e -> {
            try {
                SpotifyToken.clear();
                currentUserId = -1;
                currentUsername = "";
                currentAccessToken = "";
                avatarView.setImage(new Image(DEFAULT_AVATAR));
                usernameLabel.setText("No autenticado");
                resultArea.setText("Sesión cerrada correctamente");
            } catch (SQLException ex) {
                resultArea.setText("Error al cerrar sesión: " + ex.getMessage());
            }
        });

        HBox userPanel = new HBox(15, avatarView, usernameLabel, logoutButton);
        userPanel.setAlignment(Pos.CENTER_LEFT);
        userPanel.setPadding(new Insets(10));
        return userPanel;
    }

    private Tab createMusicTab() {
        VBox musicTab = new VBox(15);
        musicTab.setPadding(new Insets(15));

        // Botones originales de OrpheusTUI
        Button loginButton = new Button("Iniciar sesión en Spotify");
        Button addTrackButton = new Button("Añadir canción a biblioteca");
        Button addAlbumButton = new Button("Añadir álbum a biblioteca");
        Button profileButton = new Button("Ver perfil");
        Button createPlaylistButton = new Button("Crear Playlist");
        Button topTracksButton = new Button("Ver Top Tracks");
        Button topArtistsButton = new Button("Ver Top Artistas");

        // Acciones de los botones (reutilizando métodos existentes)
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

            String scope = URLEncoder.encode(
                    "user-top-read user-read-recently-played user-library-modify playlist-modify-private",
                    StandardCharsets.UTF_8
            );
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + SpotifyClient.CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyClient.REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + scope +
                    "&show_dialog=true";

            getHostServices().showDocument(authUrl);
        });

        addTrackButton.setOnAction(event -> {
            try {
                if (!SpotifyClient.isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }

                String accessToken = getValidAccessToken();
                TextInputDialog dialog = new TextInputDialog();
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

        addAlbumButton.setOnAction(event -> {
            try {
                if (!SpotifyClient.isInternetAvailable()) {
                    resultArea.setText("No hay conexión a Internet.");
                    return;
                }
                String accessToken = getValidAccessToken();
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Añadir álbum");
                dialog.setHeaderText("Introduce el nombre del álbum");
                dialog.setContentText("Álbum:");
                dialog.showAndWait().ifPresent(albumName -> {
                    try {
                        String albumId = SpotifyClient.searchAlbumIdByName(albumName, accessToken);
                        SpotifyClient.addAlbumToLibrary(albumId, accessToken);
                        resultArea.setText("Álbum añadido exitosamente");
                    } catch (IOException e) {
                        resultArea.setText("Error al añadir álbum: " + e.getMessage());
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

        // Diseño
        GridPane buttonGrid = new GridPane();
        buttonGrid.setVgap(10);
        buttonGrid.setHgap(10);
        buttonGrid.addRow(0, loginButton, addTrackButton, addAlbumButton);
        buttonGrid.addRow(1, profileButton, createPlaylistButton, topTracksButton);
        buttonGrid.addRow(2, topArtistsButton);

        musicTab.getChildren().addAll(
                new Label("Música"),
                new Separator(),
                buttonGrid
        );

        Tab tab = new Tab("Música", musicTab);
        tab.setClosable(false);
        return tab;
    }

    private Tab createSocialTab() {
        VBox socialTab = new VBox(15);
        socialTab.setPadding(new Insets(15));

        // Tabla de usuarios
        initializeUsersTable();

        // Botones
        Button searchUsersButton = new Button("Buscar usuarios");
        Button followUserButton = new Button("Seguir usuario seleccionado");
        Button unfollowUserButton = new Button("Dejar de seguir");
        Button friendActivityButton = new Button("Ver actividad de amigos");

        // Acciones
        searchUsersButton.setOnAction(e -> searchUsersDialog());
        followUserButton.setOnAction(e -> followSelectedUser());
        unfollowUserButton.setOnAction(e -> unfollowSelectedUser());
        friendActivityButton.setOnAction(e -> showFriendActivity());

        VBox userControls = new VBox(10,
                                     new Label("Usuarios"),
                                     searchUsersButton,
                                     new HBox(10, followUserButton, unfollowUserButton),
                                     friendActivityButton
        );

        socialTab.getChildren().addAll(
                userControls,
                new Separator(),
                new Label("Lista de Usuarios"),
                usersTable
        );

        Tab tab = new Tab("Social", socialTab);
        tab.setClosable(false);
        return tab;
    }

    private Tab createReviewsTab() {
        VBox reviewsTab = new VBox(15);
        reviewsTab.setPadding(new Insets(15));

        // Tabla de reseñas
        initializeReviewsTable();

        // Botones
        Button leaveReviewButton = new Button("Dejar reseña");
        Button searchReviewsButton = new Button("Buscar reseñas por canción");
        Button trendingReviewsButton = new Button("Reseñas populares");

        // Acciones
        leaveReviewButton.setOnAction(e -> leaveReviewDialog());
        searchReviewsButton.setOnAction(e -> searchReviewsDialog());
        trendingReviewsButton.setOnAction(e -> showTrendingReviews());

        reviewsTab.getChildren().addAll(
                new HBox(15, leaveReviewButton, searchReviewsButton, trendingReviewsButton),
                new Separator(),
                new Label("Reseñas"),
                reviewsTable
        );

        Tab tab = new Tab("Reseñas", reviewsTab);
        tab.setClosable(false);
        return tab;
    }

    private void initializeUsersTable() {
        TableColumn<UserManager.UserProfile, String> avatarCol = new TableColumn<>("Avatar");
        avatarCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            {
                imageView.setFitHeight(40);
                imageView.setFitWidth(40);
            }
            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty || url == null) {
                    setGraphic(null);
                } else {
                    imageView.setImage(new Image(url));
                    setGraphic(imageView);
                }
            }
        });
        avatarCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().avatarUrl));

        TableColumn<UserManager.UserProfile, String> usernameCol = new TableColumn<>("Usuario");
        usernameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().username));

        TableColumn<UserManager.UserProfile, Number> followersCol = new TableColumn<>("Seguidores");
        followersCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().followersCount));

        TableColumn<UserManager.UserProfile, Number> followingCol = new TableColumn<>("Siguiendo");
        followingCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().followingCount));

        usersTable.getColumns().setAll(avatarCol, usernameCol, followersCol, followingCol);
        usersTable.setItems(usersData);
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void initializeReviewsTable() {
        TableColumn<ReviewManager.Review, String> userCol = new TableColumn<>("Usuario");
        userCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().username));

        TableColumn<ReviewManager.Review, String> trackCol = new TableColumn<>("Canción");
        trackCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().trackName));

        TableColumn<ReviewManager.Review, Number> ratingCol = new TableColumn<>("Rating");
        ratingCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().rating));

        TableColumn<ReviewManager.Review, String> reviewCol = new TableColumn<>("Reseña");
        reviewCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().reviewText));

        TableColumn<ReviewManager.Review, Number> usefulCol = new TableColumn<>("Útil");
        usefulCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().usefulVotes));

        TableColumn<ReviewManager.Review, Number> notUsefulCol = new TableColumn<>("No útil");
        notUsefulCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().notUsefulVotes));

        TableColumn<ReviewManager.Review, String> dateCol = new TableColumn<>("Fecha");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDate()));

        reviewsTable.getColumns().setAll(userCol, trackCol, ratingCol, reviewCol, usefulCol, notUsefulCol, dateCol);
        reviewsTable.setItems(reviewsData);
        reviewsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ReviewManager.Review, Void> voteUsefulCol = new TableColumn<>("Útil");
        voteUsefulCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("👍");
            {
                btn.setOnAction(e -> {
                    ReviewManager.Review review = getTableView().getItems().get(getIndex());
                    voteSystem.handleVote(review.id, currentUserId, true);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        TableColumn<ReviewManager.Review, Void> voteNotUsefulCol = new TableColumn<>("No útil");
        voteNotUsefulCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("👎");
            {
                btn.setOnAction(e -> {
                    ReviewManager.Review review = getTableView().getItems().get(getIndex());
                    voteSystem.handleVote(review.id, currentUserId, false);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        reviewsTable.getColumns().addAll(voteUsefulCol, voteNotUsefulCol);
    }

    private void configureSpotifyCallback() {
        Spark.get("/callback", (req, res) -> {
            String authCode = req.queryParams("code");
            if (authCode != null) {
                try {
                    JsonObject tokenResponse = SpotifyClient.getTokenResponse(authCode);
                    currentAccessToken = tokenResponse.get("access_token").getAsString();
                    String refreshToken = tokenResponse.get("refresh_token").getAsString();
                    int expiresIn = tokenResponse.get("expires_in").getAsInt();

                    SpotifyToken token = new SpotifyToken(currentAccessToken, expiresIn, refreshToken);
                    token.save();

                    String userProfileJson = SpotifyClient.getUserProfile(currentAccessToken);
                    org.json.JSONObject userProfile = new org.json.JSONObject(userProfileJson);
                    String spotifyId = userProfile.getString("id");
                    String username = userProfile.optString("display_name", spotifyId);
                    String avatarUrl = userProfile.has("images") && !userProfile.getJSONArray("images").isEmpty() ?
                            userProfile.getJSONArray("images").getJSONObject(0).getString("url") :
                            DEFAULT_AVATAR;

                    // Pedir nombre de usuario único para reseñas
                    String reviewUsername = obtenerONuevoNombreDeUsuario(spotifyId);

                    // Guardar usuario (implementa este método si no existe)
                    guardarUsuario(spotifyId, reviewUsername, currentAccessToken, refreshToken, "spotify");

                    currentUserId = UserManager.getOrCreateUser(spotifyId, username, avatarUrl);
                    currentUsername = username;
                    currentAvatarUrl = avatarUrl;

                    Platform.runLater(() -> {
                        ((ImageView)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(0)).setImage(new Image(currentAvatarUrl));
                       ((Label)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(1)).setText(currentUsername);
                        resultArea.setText("¡Bienvenido, " + currentUsername + "!");
                    });

                } catch (IOException | SQLException e) {
                    Platform.runLater(() -> resultArea.setText("Error en login: " + e.getMessage()));
                }
            }
            return "Puedes cerrar esta ventana.";
        });
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
            TextInputDialog playlistDialog = new TextInputDialog();
            playlistDialog.setTitle("Crear Playlist");
            playlistDialog.setHeaderText("Introduce el nombre de la playlist");
            playlistDialog.setContentText("Nombre:");
            String playlistName = playlistDialog.showAndWait().orElse(null);

            if (playlistName == null || playlistName.isEmpty()) {
                resultArea.setText("Nombre de playlist no proporcionado.");
                return;
            }

            String playlistId = SpotifyPlaylistManager.createPlaylist(accessToken, playlistName, "Playlist creada desde OrpheusTUI");
            TextInputDialog tracksDialog = new TextInputDialog();
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
//            Minority
        }
    }

    private String getValidAccessToken() throws SQLException, IOException {
        SpotifyToken token = SpotifyToken.getValidToken();
        if (token == null) {
            throw new IOException("No token available. Please login first.");
        }
        return token.getAccessToken();
    }

    private String obtenerONuevoNombreDeUsuario(String spotifyId) throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/spotify_auth";
        String user = "orpheusers";
        String password = "munyun214";
        String sql = "SELECT review_username FROM usuarios WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, spotifyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) {
                    return rs.getString(1);
                }
            }
        }
        // Si no existe, pedirlo
        return solicitarNombreDeUsuarioUnico();
    }

    private void eliminarUsuario() throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/spotify_auth";
        String user = "orpheusers";
        String password = "munyun214";
        String sql = "DELETE FROM usuarios";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    @Override
    public void onVoteSuccess() {
        refreshReviewsTable();
        showAlert("Éxito", "Tu voto se registró correctamente");
    }

    @Override
    public void onVoteFailure(String errorMessage) {
        showAlert("Error", "No se pudo registrar el voto: " + errorMessage);
    }

    private void refreshReviewsTable() {
        try {
            reviewsData.setAll(ReviewManager.getTrendingReviews(20));
        } catch (SQLException e) {
            showAlert("Error", "No se pudieron cargar las reseñas: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void leaveReviewDialog() {
        if (!checkAuth()) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Dejar reseña");
        dialog.setHeaderText("Ingresa el nombre de la canción");
        dialog.setContentText("Canción:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(trackName -> {
            new Thread(() -> {
                try {
                    String trackId = SpotifyClient.searchTrackIdByName(trackName, currentAccessToken);
                    JsonObject track = SpotifyClient.getTrackInfo(trackId, currentAccessToken);
                    String fullTrackName = track.get("name").getAsString() + " - ";

                    JsonArray artists = track.getAsJsonArray("artists");
                    for (int i = 0; i < artists.size(); i++) {
                        if (i > 0) fullTrackName += ", ";
                        fullTrackName += artists.get(i).getAsJsonObject().get("name").getAsString();
                    }

                    String albumCover = SpotifyClient.getAlbumCoverUrl(trackId, currentAccessToken);

                    String finalFullTrackName = fullTrackName;
                    String finalFullTrackName1 = fullTrackName;
                    Platform.runLater(() -> {
                        Dialog<Pair<String, Integer>> reviewDialog = new Dialog<>();
                        reviewDialog.setTitle("Nueva reseña");
                        reviewDialog.setHeaderText("Reseña para: " + finalFullTrackName1);

                        // Configurar contenido del diálogo
                        GridPane grid = new GridPane();
                        grid.setHgap(10);
                        grid.setVgap(10);
                        grid.setPadding(new Insets(20, 150, 10, 10));

                        TextArea reviewText = new TextArea();
                        reviewText.setPromptText("Escribe tu reseña...");

                        Spinner<Integer> ratingSpinner = new Spinner<>(1, 5, 5);

                        if (albumCover != null) {
                            ImageView albumCoverView = new ImageView(new Image(albumCover, 100, 100, true, true));
                            grid.add(albumCoverView, 0, 0, 1, 3);
                        }

                        grid.add(new Label("Reseña:"), albumCover != null ? 1 : 0, 0);
                        grid.add(reviewText, albumCover != null ? 1 : 0, 1);
                        grid.add(new Label("Rating (1-5):"), albumCover != null ? 1 : 0, 2);
                        grid.add(ratingSpinner, albumCover != null ? 2 : 1, 2);

                        reviewDialog.getDialogPane().setContent(grid);
                        reviewDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                        reviewDialog.setResultConverter(buttonType -> {
                            if (buttonType == ButtonType.OK) {
                                return new Pair<>(reviewText.getText(), ratingSpinner.getValue());
                            }
                            return null;
                        });

                        Optional<Pair<String, Integer>> reviewResult = reviewDialog.showAndWait();
                        reviewResult.ifPresent(review -> {
                            new Thread(() -> {
                                try {
                                    ReviewManager.addReview(trackId, finalFullTrackName, currentUserId, review.getKey(), review.getValue());
                                    Platform.runLater(() -> resultArea.setText("¡Reseña publicada con éxito!")
                                    );
                                } catch (SQLException e) {
                                    Platform.runLater(() -> resultArea.setText("Error al publicar reseña: " + e.getMessage())
                                    );
                                }
                            }).start();
                        });
                    });

                } catch (IOException e) {
                    Platform.runLater(() -> resultArea.setText("Error al buscar canción: " + e.getMessage())
                    );
                }
            }).start();
        });
    }

    private void searchReviewsDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Buscar reseñas");
        dialog.setHeaderText("Ingresa el nombre de la canción");
        dialog.setContentText("Canción:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(trackName -> {
            new Thread(() -> {
                try {
                    String trackId = SpotifyClient.searchTrackIdByName(trackName, currentAccessToken);
                    List<ReviewManager.Review> reviews = ReviewManager.getReviewsForTrack(trackId);

                    Platform.runLater(() -> {
                        reviewsData.clear();
                        reviewsData.addAll(reviews);
                        resultArea.setText("Mostrando " + reviews.size() + " reseñas para '" + trackName + "'");
                    });

                } catch (IOException | SQLException e) {
                    Platform.runLater(() ->
                                              resultArea.setText("Error al buscar reseñas: " + e.getMessage())
                    );
                }
            }).start();
        });
    }

    private void showTrendingReviews() {
        new Thread(() -> {
            try {
                List<ReviewManager.Review> reviews = ReviewManager.getTrendingReviews(20);
                Platform.runLater(() -> {
                    reviewsData.clear();
                    reviewsData.addAll(reviews);
                    resultArea.setText("Reseñas populares");
                });
            } catch (SQLException e) {
                Platform.runLater(() ->
                                          resultArea.setText("Error al obtener reseñas: " + e.getMessage())
                );
            }
        }).start();
    }

    private void searchUsersDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Buscar usuarios");
        dialog.setHeaderText("Ingresa el nombre de usuario a buscar");
        dialog.setContentText("Usuario:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(query -> {
            new Thread(() -> {
                try {
                    List<UserManager.UserProfile> users = UserManager.searchUsers(query);
                    Platform.runLater(() -> {
                        usersData.clear();
                        usersData.addAll(users);
                        resultArea.setText("Mostrando " + users.size() + " resultados para '" + query + "'");
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> resultArea.setText("Error al buscar usuarios: " + e.getMessage()));
                }
            }).start();
        });
    }

    private void followSelectedUser() {
        if (currentUserId == -1) {
            resultArea.setText("Debes iniciar sesión primero");
            return;
        }

        UserManager.UserProfile selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            resultArea.setText("Selecciona un usuario primero");
            return;
        }

        new Thread(() -> {
            try {
                UserManager.followUser(currentUserId, selected.id);
                Platform.runLater(() ->
                                          resultArea.setText("Ahora sigues a " + selected.username)
                );
            } catch (SQLException e) {
                Platform.runLater(() ->
                                          resultArea.setText("Error al seguir usuario: " + e.getMessage())
                );
            }
        }).start();
    }

    private void unfollowSelectedUser() {
        if (currentUserId == -1) {
            resultArea.setText("Debes iniciar sesión primero");
            return;
        }

        UserManager.UserProfile selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            resultArea.setText("Selecciona un usuario primero");
            return;
        }

        new Thread(() -> {
            try {
                UserManager.unfollowUser(currentUserId, selected.id);
                Platform.runLater(() ->
                                          resultArea.setText("Has dejado de seguir a " + selected.username)
                );
            } catch (SQLException e) {
                Platform.runLater(() ->
                                          resultArea.setText("Error al dejar de seguir: " + e.getMessage())
                );
            }
        }).start();
    }

    private void showFriendActivity() {
        if (currentUserId == -1) {
            resultArea.setText("Debes iniciar sesión primero");
            return;
        }

        new Thread(() -> {
            try {
                List<MusicPlayer.PlayHistory> activity = MusicPlayer.getFriendActivity(currentUserId);
                StringBuilder sb = new StringBuilder("Actividad de amigos:\n\n");

                for (MusicPlayer.PlayHistory item : activity) {
                    sb.append(item.trackName).append("\n");
                    sb.append("   Escuchado el: ").append(item.playedAt.toString().substring(0, 16)).append("\n\n");
                }

                Platform.runLater(() -> resultArea.setText(sb.toString()));
            } catch (SQLException e) {
                Platform.runLater(() ->
                                          resultArea.setText("Error al obtener actividad: " + e.getMessage())
                );
            }
        }).start();
    }

    private boolean checkAuth() {
        if (currentUserId == 0) {
            resultArea.setText("Debes iniciar sesión primero");
            return false;
        }
        return true;
    }

    private void guardarUsuario(String id, String reviewUsername, String accessToken, String refreshToken, String tipo) throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/spotify_auth";
        String user = "orpheusers";
        String password = "munyun214";
        String sql = "INSERT INTO usuarios (id, review_username, access_token, refresh_token, tipo) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET review_username = EXCLUDED.review_username, access_token = EXCLUDED.access_token, refresh_token = EXCLUDED.refresh_token, tipo = EXCLUDED.tipo";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, reviewUsername);
            stmt.setString(3, accessToken);
            stmt.setString(4, refreshToken);
            stmt.setString(5, tipo);
            stmt.executeUpdate();
        }
    }

    private String solicitarNombreDeUsuarioUnico() throws SQLException {
        while (true) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Nombre de usuario");
            dialog.setHeaderText("Elige un nombre de usuario único para tus reseñas");
            dialog.setContentText("Nombre de usuario:");
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                String username = result.get().trim();
                if (!existeNombreDeUsuario(username)) {
                    return username;
                } else {
                    showAlert("Error", "Ese nombre de usuario ya está en uso. Elige otro.");
                }
            } else {
                showAlert("Error", "Debes ingresar un nombre de usuario.");
            }
        }
    }

    private boolean existeNombreDeUsuario(String username) throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/spotify_auth";
        String user = "orpheusers";
        String password = "munyun214";
        String sql = "SELECT 1 FROM usuarios WHERE review_username = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void incrementarContadorReseñas(String userId) throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/spotify_auth";
        String user = "orpheusers";
        String password = "munyun214";
        String sql = "UPDATE usuarios SET review_count = review_count + 1 WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        }
    }
}