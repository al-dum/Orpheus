import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;
import spark.Spark;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrpheusTUI extends Application implements ReviewVoteSystem.VoteUpdateListener {
    private ReviewVoteSystem voteSystem;
    private static final String DEFAULT_AVATAR = "https://i.scdn.co/image/ab6775700000ee8510e0a4a948b6e370d1cbda0f";
    private Stage primaryStage;
    private StackPane resultContainer;
    private TextArea resultArea;
    private TabPane tabPane;
    private int currentUserId = -1;
    private String currentUsername = "";
    private String currentAvatarUrl = DEFAULT_AVATAR;
    private static final String SPOTIFY_AUTH_DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String SPOTIFY_AUTH_DB_USER = "postgres";
    private static final String SPOTIFY_AUTH_DB_PASSWORD = "1234";
    private String currentAccessToken = "";
    private String currentRefreshToken = "";
    private TableView<ReviewManager.Review> reviewsTable = new TableView<>();
    private TableView<UserManager.UserProfile> usersTable = new TableView<>();
    private ObservableList<ReviewManager.Review> reviewsData = FXCollections.observableArrayList();
    private ObservableList<UserManager.UserProfile> usersData = FXCollections.observableArrayList();
    private static OrpheusData orpheusData = new OrpheusData();
    private ReviewUserManager.ReviewUser currentReviewUser = null; // Tracks logged-in review user
    private boolean isAdminMode = false;
    private static final String ADMIN_PASSWORD = "admin123"; // Hardcoded for simplicity; consider securing this

    public static void main(String[] args) {
        try {
            ReviewManager.inicializarTablas();
        } catch (SQLException e) {
            e.printStackTrace();
        }

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

        // Prompt for user type
        ChoiceDialog<String> userTypeDialog = new ChoiceDialog<>("Usuario", "Usuario", "Admin");
        userTypeDialog.setTitle("Seleccionar Tipo de Usuario");
        userTypeDialog.setHeaderText("¿Qué tipo de usuario eres?");
        userTypeDialog.setContentText("Selecciona:");
        Optional<String> userTypeResult = userTypeDialog.showAndWait();

        if (!userTypeResult.isPresent()) {
            System.out.println("Usuario no existe, por favor registrelo");
            return;
        }

        if (userTypeResult.get().equals("Admin")) {
            TextInputDialog passwordDialog = new TextInputDialog();
            passwordDialog.setTitle("Autenticación de Admin");
            passwordDialog.setHeaderText("Ingresa la contraseña de administrador");
            passwordDialog.setContentText("Contraseña:");
            Optional<String> passwordResult = passwordDialog.showAndWait();
            if (passwordResult.isPresent() && passwordResult.get().equals(ADMIN_PASSWORD)) {
                isAdminMode = true;
            } else {
                showAlert("Error", "Contraseña de administrador incorrecta.");
                Platform.exit();
                return;
            }
        } else {
            // User mode: Prompt for login or registration
            Dialog<ButtonType> loginDialog = new Dialog<>();
            loginDialog.setTitle("Inicio de Sesión");
            loginDialog.setHeaderText("Inicia sesión o regístrate como usuario de reseñas");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            TextField usernameField = new TextField();
            PasswordField passwordField = new PasswordField();
            grid.add(new Label("Usuario:"), 0, 0);
            grid.add(usernameField, 1, 0);
            grid.add(new Label("Contraseña:"), 0, 1);
            grid.add(passwordField, 1, 1);

            loginDialog.getDialogPane().setContent(grid);
            loginDialog.getDialogPane().getButtonTypes().addAll(
                    new ButtonType("Iniciar Sesión", ButtonBar.ButtonData.OK_DONE),
                    new ButtonType("Registrarse", ButtonBar.ButtonData.OTHER),
                    ButtonType.CANCEL
            );

            Optional<ButtonType> loginResult = loginDialog.showAndWait();
            if (!loginResult.isPresent() || loginResult.get() == ButtonType.CANCEL) {
                Platform.exit();
                return;
            }

            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            if (username.isEmpty() || password.isEmpty()) {
                showAlert("Error", "Debes ingresar un nombre de usuario y contraseña.");
                Platform.exit();
                return;
            }

            try {
            if (loginResult.get().getText().equals("Iniciar Sesión")) {
                currentReviewUser = ReviewUserManager.loginUser(username, password);
            } else {
                currentReviewUser = ReviewUserManager.registerUser(username, password);
            }
            resultArea.setText("Bienvenido, " + currentReviewUser.username + "!");
        } catch (SQLException e) {
            showAlert("Error", "Error en autenticación: " + e.getMessage());
            e.printStackTrace(); // Imprime el error completo en la consola para depuración
            // Comentar o eliminar la siguiente línea para evitar que se cierre la aplicación
            // Platform.exit();
            return;
        }
        }

        HBox userPanel = createUserPanel();
        resultContainer = new StackPane();
        resultContainer.getChildren().add(resultArea);
        ScrollPane resultScroll = new ScrollPane(resultContainer);
        resultScroll.setFitToWidth(true);
        resultScroll.setPrefHeight(100);

        // In start method, replace tabPane initialization
        tabPane = new TabPane();
        if (isAdminMode) {
            tabPane.getTabs().add(createAdminTab());
        } else {
            tabPane.getTabs().addAll(
                    createMusicTab(),
                    createReviewsTab()
            );
        }

        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(userPanel);
        mainLayout.setCenter(tabPane);
        mainLayout.setBottom(resultScroll);

        primaryStage.setScene(new Scene(mainLayout, 1000, 800));
        primaryStage.show();

        configureSpotifyCallback();


        // Check for existing Spotify session (only for User mode)
        if (!isAdminMode) {
            new Thread(() -> {
                try {
                    SpotifyToken token = SpotifyToken.getValidToken();
                    if (token != null) {
                        currentAccessToken = token.getAccessToken();
                        String userProfileJson = SpotifyClient.getUserProfileJson(currentAccessToken);
                        org.json.JSONObject userProfile = new org.json.JSONObject(userProfileJson);
                        String spotifyId = userProfile.getString("id");
                        String username = userProfile.optString("display_name", spotifyId);
                        String avatarUrl = userProfile.has("images") && !userProfile.getJSONArray("images").isEmpty() ?
                                userProfile.getJSONArray("images").getJSONObject(0).getString("url") :
                                DEFAULT_AVATAR;

                        System.out.println("Intentando obtener/crear usuario con spotifyId: " + spotifyId);
                        currentUserId = UserManager.getOrCreateUser(spotifyId, username, avatarUrl);
                        System.out.println("Usuario creado/Obtenido con ID: " + currentUserId);
                        currentUsername = username;
                        currentAvatarUrl = avatarUrl;

                        Platform.runLater(() -> {
                            ((ImageView)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(0)).setImage(new Image(currentAvatarUrl));
                            ((Label)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(1)).setText(currentUsername);
                            resultArea.appendText("\nConectado con Spotify como " + currentUsername + ". User ID: " + currentUserId);
                        });
                    } else {
                        Platform.runLater(() -> resultArea.appendText("\nNo hay sesión de Spotify activa. Inicia sesión para funciones avanzadas."));
                    }
                } catch (Exception e) {
                    e.printStackTrace(); // Imprime el stack trace completo
                    Platform.runLater(() -> resultArea.appendText("\nError al verificar sesión de Spotify: " + e.getMessage()));
                }
            }).start();
        }
    }

    private HBox createUserPanel() {
        ImageView avatarView = new ImageView(new Image(DEFAULT_AVATAR, 50, 50, true, true));
        Label usernameLabel = new Label("No autenticado");
        Button logoutButton = new Button("Cerrar sesión");

        Button exitButton = new Button("Salir");
        exitButton.setOnAction(e -> Platform.exit());
        exitButton.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white;");

        Button volverButton = new Button("Volver a selección de usuario");
        volverButton.setOnAction(e -> volverASeleccionUsuario());
        volverButton.setStyle("-fx-background-color: #4286f4; -fx-text-fill: white;");

        logoutButton.setOnAction(e -> {
            try {
                SpotifyToken.clear();
                currentUserId = -1;
                currentUsername = "";
                currentAccessToken = "";
                currentAvatarUrl = DEFAULT_AVATAR;
                avatarView.setImage(new Image(DEFAULT_AVATAR));
                usernameLabel.setText("No autenticado");
                resultArea.setText("Sesión cerrada correctamente");
                HBox buttonBox = (HBox) ((VBox) tabPane.getTabs().get(2).getContent()).getChildren().get(0);
                if (!buttonBox.getChildren().stream().anyMatch(node -> node instanceof Button && ((Button) node).getText().equals("Iniciar sesión en Spotify"))) {
                    Button loginButton = new Button("Iniciar sesión en Spotify");
                    loginButton.setOnAction(event -> triggerSpotifyLogin());
                    buttonBox.getChildren().add(loginButton);
                }
            } catch (SQLException ex) {
                resultArea.setText("Error al cerrar sesión: " + ex.getMessage());
            }
        });

        HBox userPanel = new HBox(15, avatarView, usernameLabel, logoutButton, volverButton, exitButton);
        userPanel.setAlignment(Pos.CENTER_LEFT);
        userPanel.setPadding(new Insets(10));
        return userPanel;
    }

    private Tab createAdminTab() {
        VBox adminTab = new VBox(15);
        adminTab.setPadding(new Insets(15));

        Button clearUsersButton = new Button("Vaciar tabla de usuarios");
        Button exportUsersButton = new Button("Descargar tabla de usuarios (CSV)");
        Button deleteUserButton = new Button("Eliminar usuario específico");

        Button volverButton = new Button("Volver a selección de usuario");
        volverButton.setOnAction(e -> volverASeleccionUsuario());
        volverButton.setStyle("-fx-background-color: #4286f4; -fx-text-fill: white;");

        Button exitButton = new Button("Salir");
        exitButton.setOnAction(e -> Platform.exit());
        exitButton.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white;");

        clearUsersButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar");
            confirm.setHeaderText("¿Estás seguro de que quieres vaciar la tabla de usuarios?");
            confirm.setContentText("Esta acción no se puede deshacer.");
            confirm.showAndWait().filter(response -> response == ButtonType.OK).ifPresent(response -> {
                try {
                    ReviewUserManager.clearUsers();
                    showAlert("Éxito", "Tabla de usuarios vaciada correctamente.");
                } catch (SQLException ex) {
                    showAlert("Error", "Error al vaciar tabla: " + ex.getMessage());
                }
            });
        });

        exportUsersButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Guardar tabla de usuarios");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try {
                    ReviewUserManager.exportUsersToCsv(file);
                    showAlert("Éxito", "Tabla exportada correctamente a " + file.getAbsolutePath());
                } catch (SQLException | IOException ex) {
                    showAlert("Error", "Error al exportar tabla: " + ex.getMessage());
                }
            }
        });

        deleteUserButton.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Eliminar usuario");
            dialog.setHeaderText("Ingresa el nombre de usuario a eliminar");
            dialog.setContentText("Usuario:");
            dialog.showAndWait().ifPresent(username -> {
                try {
                    ReviewUserManager.deleteUser(username);
                    showAlert("Éxito", "Usuario " + username + " y sus reseñas eliminados correctamente.");
                } catch (SQLException ex) {
                    showAlert("Error", "Error al eliminar usuario: " + ex.getMessage());
                }
            });
        });

        adminTab.getChildren().addAll(
                new Label("Administración"),
                new Separator(),
                clearUsersButton,
                exportUsersButton,
                deleteUserButton
        );

        Tab tab = new Tab("Admin", adminTab);
        tab.setClosable(false);
        return tab;
    }

    private Tab createMusicTab() {
        VBox musicTab = new VBox(15);
        musicTab.setPadding(new Insets(15));

        Button loginButton = new Button("Iniciar sesión en Spotify");
        Button addTrackButton = new Button("Añadir canción a biblioteca");
        Button addAlbumButton = new Button("Añadir álbum a biblioteca");
        Button profileButton = new Button("Ver perfil");
        Button topTracksButton = new Button("Ver Top Tracks");
        Button topArtistsButton = new Button("Ver Top Artistas");

        Button volverButton = new Button("Volver a selección de usuario");
        volverButton.setOnAction(e -> volverASeleccionUsuario());
        volverButton.setStyle("-fx-background-color: #4286f4; -fx-text-fill: white;");

        Button exitButton = new Button("Salir");
        exitButton.setOnAction(e -> Platform.exit());
        exitButton.setStyle("-fx-background-color: #ff5252; -fx-text-fill: white;");

        loginButton.setOnAction(event -> triggerSpotifyLogin());

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
                String displayName = SpotifyClient.getUserDisplayName(accessToken);
                resultArea.setText("Nombre de usuario: " + displayName);
            } catch (SQLException | IOException e) {
                resultArea.setText("Error al obtener perfil: " + e.getMessage());
            }
        });

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

        GridPane buttonGrid = new GridPane();
        buttonGrid.setVgap(10);
        buttonGrid.setHgap(10);
        buttonGrid.addRow(0, loginButton, addTrackButton, addAlbumButton);
        buttonGrid.addRow(1, profileButton, topTracksButton, topArtistsButton);

        buttonGrid.addRow(2, volverButton, exitButton);

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

        initializeUsersTable();

        Button searchUsersButton = new Button("Buscar usuarios");
        Button followUserButton = new Button("Seguir usuario seleccionado");
        Button unfollowUserButton = new Button("Dejar de seguir");
        Button friendActivityButton = new Button("Ver actividad de amigos");

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
        Button leaveReviewButton = new Button("Dejar reseña");
        Button searchReviewsButton = new Button("Buscar reseñas por canción");
        Button trendingReviewsButton = new Button("Reseñas populares");
        Button loginButton = new Button("Iniciar sesión en Spotify");

        leaveReviewButton.setOnAction(e -> leaveReviewDialog());
        searchReviewsButton.setOnAction(e -> searchReviewsDialog());
        trendingReviewsButton.setOnAction(e -> showTrendingReviews());
        loginButton.setOnAction(e -> triggerSpotifyLogin());

        HBox buttonBox = new HBox(15, leaveReviewButton, searchReviewsButton, trendingReviewsButton);
        if (currentUserId == -1) {
            buttonBox.getChildren().add(loginButton);
        }

        // Initialize reviews table
        initializeSongReviewsTable();

        reviewsTab.getChildren().addAll(
                buttonBox,
                new Separator(),
                new Label("Reseñas"),
                reviewsTable
        );

        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == tabPane.getTabs().get(1)) {
                buttonBox.getChildren().remove(loginButton);
                if (currentUserId == -1) {
                    buttonBox.getChildren().add(loginButton);
                }
                // Refresh reviews when tab is selected
                refreshSongReviewsTable();
            }
        });

        Tab tab = new Tab("Reseñas", reviewsTab);
        tab.setClosable(false);
        tab.setId("reviewsTab");
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

    private void initializeSongReviewsTable() {
        TableColumn<ReviewManager.Review, Number> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().id));

        TableColumn<ReviewManager.Review, String> trackIdCol = new TableColumn<>("Track ID");
        trackIdCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().trackId));

        TableColumn<ReviewManager.Review, String> trackNameCol = new TableColumn<>("Canción");
        trackNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().trackName));

        TableColumn<ReviewManager.Review, String> reviewUsernameCol = new TableColumn<>("Usuario");
        reviewUsernameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().reviewUsername));

        TableColumn<ReviewManager.Review, String> reviewTextCol = new TableColumn<>("Reseña");
        reviewTextCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().reviewText));

        TableColumn<ReviewManager.Review, Number> ratingCol = new TableColumn<>("Rating");
        ratingCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().rating));

        TableColumn<ReviewManager.Review, Number> usefulVotesCol = new TableColumn<>("Útil");
        usefulVotesCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().usefulVotes));

        TableColumn<ReviewManager.Review, Number> notUsefulVotesCol = new TableColumn<>("No útil");
        notUsefulVotesCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().notUsefulVotes));

        TableColumn<ReviewManager.Review, String> createdAtCol = new TableColumn<>("Fecha");
        createdAtCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFormattedDate()));

        reviewsTable.getColumns().setAll(idCol, trackIdCol, trackNameCol, reviewUsernameCol, reviewTextCol, ratingCol, usefulVotesCol, notUsefulVotesCol, createdAtCol);
        reviewsTable.setItems(reviewsData);
        reviewsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // In initializeSongReviewsTable, update vote buttons
        TableColumn<ReviewManager.Review, Void> voteUsefulCol = new TableColumn<>("Útil");
        voteUsefulCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("👍");
            {
                btn.setOnAction(e -> {
                    ReviewManager.Review review = getTableView().getItems().get(getIndex());
                    // Usar solamente el ID del usuario de reseñas
                    Integer reviewUserId = currentReviewUser != null ? currentReviewUser.id : null;
                    voteSystem.handleVote(review.id, null, currentReviewUser != null ? currentReviewUser.id : null, true);
                });
                // Deshabilitar el botón solo si no hay usuario de reseñas
                btn.setDisable(currentReviewUser == null);
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
                    // Usar solamente el ID del usuario de reseñas
                    Integer reviewUserId = currentReviewUser != null ? currentReviewUser.id : null;
                    voteSystem.handleVote(review.id, null, currentReviewUser != null ? currentReviewUser.id : null, false);                });
                // Deshabilitar el botón solo si no hay usuario de reseñas
                btn.setDisable(currentReviewUser == null);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        reviewsTable.getColumns().addAll(voteUsefulCol, voteNotUsefulCol);

        // Set preferred height to ensure visibility
        reviewsTable.setPrefHeight(400);
        reviewsTable.setMinHeight(200);

        refreshSongReviewsTable();
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
                    System.out.println("Token guardado: accessToken=" + currentAccessToken + ", refreshToken=" + refreshToken);

                    currentRefreshToken = refreshToken;

                    String userProfileJson = SpotifyClient.getUserProfileJson(currentAccessToken);
                    org.json.JSONObject userProfile = new org.json.JSONObject(userProfileJson);
                    String spotifyId = userProfile.getString("id");
                    String username = userProfile.optString("display_name", spotifyId);
                    String avatarUrl = userProfile.has("images") && !userProfile.getJSONArray("images").isEmpty() ?
                            userProfile.getJSONArray("images").getJSONObject(0).getString("url") : DEFAULT_AVATAR;

                    currentUserId = UserManager.getOrCreateUser(spotifyId, username, avatarUrl);
                    currentUsername = username;
                    currentAvatarUrl = avatarUrl;

                    Platform.runLater(() -> {
                        ((ImageView)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(0)).setImage(new Image(currentAvatarUrl));
                        ((Label)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(1)).setText(currentUsername);
                        resultArea.setText("¡Bienvenido, " + currentUsername + "! User ID: " + currentUserId);
                        System.out.println("UI actualizada: userId=" + currentUserId + ", accessToken=" + currentAccessToken);
                        HBox buttonBox = (HBox) ((VBox) tabPane.getTabs().get(2).getContent()).getChildren().get(0);
                        buttonBox.getChildren().removeIf(node -> node instanceof Button && ((Button) node).getText().equals("Iniciar sesión en Spotify"));
                    });
                } catch (IOException | SQLException e) {
                    Platform.runLater(() -> resultArea.setText("Error en login: " + e.getMessage()));
                    e.printStackTrace();
                }
            } else {
                Platform.runLater(() -> resultArea.setText("Error en autenticación: Código de autorización no recibido."));
            }
            return "Puedes cerrar esta ventana.";
        });
    }

    private void triggerSpotifyLogin() {
        try {
            SpotifyToken token = SpotifyToken.getValidToken();
            if (token != null) {
                currentAccessToken = token.getAccessToken();
                String userProfileJson = SpotifyClient.getUserProfileJson(currentAccessToken);
                org.json.JSONObject userProfile = new org.json.JSONObject(userProfileJson);
                String spotifyId = userProfile.getString("id");
                String username = userProfile.optString("display_name", spotifyId);
                String avatarUrl =
                        userProfile.has("images") && !userProfile.getJSONArray("images").isEmpty() ?
                                userProfile.getJSONArray("images").getJSONObject(0)
                                        .getString("url") : DEFAULT_AVATAR;

                System.out.println("Intentando obtener/crear usuario con spotifyId: " + spotifyId);
                currentUserId = UserManager.getOrCreateUser(spotifyId, username, avatarUrl);
                System.out.println("Usuario creado/Obtenido con ID: " + currentUserId);
                System.out.println(currentAvatarUrl);
                currentUsername = username;
                currentAvatarUrl = avatarUrl;

                Platform.runLater(() -> {
                    ((ImageView) ((HBox) ((BorderPane) primaryStage.getScene()
                            .getRoot()).getTop()).getChildren().get(0)).setImage(
                            new Image(currentAvatarUrl));
                    ((Label) ((HBox) ((BorderPane) primaryStage.getScene()
                            .getRoot()).getTop()).getChildren().get(1)).setText(currentUsername);
                    resultArea.setText(
                            "Ya estás autenticado con Spotify. User ID: " + currentUserId);
                    HBox buttonBox = (HBox) ((VBox) tabPane.getTabs().get(2)
                            .getContent()).getChildren().get(0);
                    buttonBox.getChildren().removeIf(
                            node -> node instanceof Button && ((Button) node).getText()
                                    .equals("Iniciar sesión en Spotify"));
                });
                return;
            }
        } catch (SQLException | IOException e) {
            resultArea.setText("Error al verificar tokens: " + e.getMessage());
            e.printStackTrace();
        }

        if (!SpotifyClient.isInternetAvailable()) {
            resultArea.setText(
                    "No hay conexión a Internet. Por favor, verifica tu conexión e intenta nuevamente.");
            return;
        }

        String scope = URLEncoder.encode(
                "user-top-read user-read-recently-played user-library-modify playlist-modify-private",
                StandardCharsets.UTF_8);
        String authUrl = "https://accounts.spotify.com/authorize?" + "client_id=" + SpotifyClient.CLIENT_ID + "&response_type=code" + "&redirect_uri=" + URLEncoder.encode(
                SpotifyClient.REDIRECT_URI,
                StandardCharsets.UTF_8) + "&scope=" + scope + "&show_dialog=true";

        getHostServices().showDocument(authUrl);

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

    private String getValidAccessToken() throws SQLException, IOException {
        SpotifyToken token = SpotifyToken.getValidToken();
        if (token == null) {
            System.out.println("No se encontró un token válido en la base de datos");
            throw new IOException("No token available. Please login first.");
        }
        System.out.println("Token recuperado: " + token.getAccessToken());
        return token.getAccessToken();
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
        refreshSongReviewsTable();
        showAlert("Éxito", "Tu voto se registró correctamente");
    }

    @Override
    public void onVoteFailure(String errorMessage) {
        showAlert("Error", "No se pudo registrar el voto: " + errorMessage);
    }

    private void refreshSongReviewsTable() {
        try {
            reviewsData.setAll(ReviewManager.getAllSongReviews());
        } catch (SQLException e) {
            Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las reseñas: " + e.getMessage()));
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
        if (currentReviewUser == null && !isAdminMode) {
            showAlert("Error", "Debes iniciar sesión como usuario de reseñas.");
            return;
        }

        // Prompt for review username
        TextInputDialog usernameDialog = new TextInputDialog(currentReviewUser != null ? currentReviewUser.username : "");
        usernameDialog.setTitle("Nombre de usuario");
        usernameDialog.setHeaderText("Ingresa el nombre de usuario para la reseña");
        usernameDialog.setContentText("Usuario:");
        Optional<String> usernameResult = usernameDialog.showAndWait();

        if (!usernameResult.isPresent() || usernameResult.get().trim().isEmpty()) {
            showAlert("Error", "Debes proporcionar un nombre de usuario.");
            return;
        }

        String reviewUsername = usernameResult.get().trim();
        ReviewUserManager.ReviewUser reviewUser = null;

        try {
            reviewUser = ReviewUserManager.getUserByUsername(reviewUsername);
            if (reviewUser == null) {
                showAlert("Error", "El nombre de usuario no existe.");
                return;
            }

            // If username matches current review user, proceed
            if (currentReviewUser != null && reviewUser.id == currentReviewUser.id) {
                // Proceed to review dialog
            } else {
                // Prompt for password
                TextInputDialog passwordDialog = new TextInputDialog();
                passwordDialog.setTitle("Verificación");
                passwordDialog.setHeaderText("Ingresa la contraseña para " + reviewUsername);
                passwordDialog.setContentText("Contraseña:");
                Optional<String> passwordResult = passwordDialog.showAndWait();

                if (!passwordResult.isPresent() || passwordResult.get().isEmpty()) {
                    showAlert("Error", "Debes ingresar una contraseña.");
                    return;
                }

                try {
                    ReviewUserManager.loginUser(reviewUsername, passwordResult.get());
                } catch (SQLException e) {
                    showAlert("Error", "Contraseña incorrecta para " + reviewUsername);
                    return;
                }
            }
        } catch (SQLException e) {
            showAlert("Error", "Error al verificar usuario: " + e.getMessage());
            return;
        }

        final ReviewUserManager.ReviewUser finalReviewUser = reviewUser;
        Dialog<Pair<String, String>> trackDialog = new Dialog<>();
        trackDialog.setTitle("Dejar reseña");
        trackDialog.setHeaderText("Ingresa los detalles de la canción");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField trackIdField = new TextField();
        trackIdField.setPromptText("Spotify Track ID (e.g., 4uUG5triady4h6WJeZUFw9)");
        TextField trackNameField = new TextField();
        trackNameField.setPromptText("Nombre de la canción (ej., Bohemian Rhapsody)");
        CheckBox useSpotifySearch = new CheckBox("Buscar con Spotify (requiere login)");

        grid.add(new Label("Track ID (opcional):"), 0, 0);
        grid.add(trackIdField, 1, 0);
        grid.add(new Label("Nombre de la canción:"), 0, 1);
        grid.add(trackNameField, 1, 1);
        grid.add(useSpotifySearch, 1, 2);

        trackDialog.getDialogPane().setContent(grid);
        trackDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        trackDialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new Pair<>(trackIdField.getText(), trackNameField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> trackResult = trackDialog.showAndWait();
        trackResult.ifPresent(track -> {
            String trackId = track.getKey().trim();
            String trackName = track.getValue().trim();

            if (trackName.isEmpty()) {
                Platform.runLater(() -> showAlert("Error", "Debes proporcionar un nombre de canción."));
                return;
            }

            if (trackId.length() > 255) {
                Platform.runLater(() -> showAlert("Error", "El Track ID no debe exceder los 255 caracteres."));
                return;
            }

            if (!trackId.isEmpty() && !trackId.matches("[a-zA-Z0-9_-]+")) {
                Platform.runLater(() -> showAlert("Error", "El Track ID contiene caracteres inválidos."));
                return;
            }

            if (useSpotifySearch.isSelected()) {
                if (currentUserId == -1 || currentAccessToken.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Autenticación requerida");
                        alert.setHeaderText("Debes iniciar sesión en Spotify para buscar canciones");
                        alert.setContentText("Inicia sesión con Spotify o ingresa el Track ID manualmente.");
                        ButtonType loginButton = new ButtonType("Iniciar sesión");
                        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
                        alert.getButtonTypes().setAll(loginButton, cancelButton);
                        alert.showAndWait().filter(response -> response == loginButton).ifPresent(response -> {
                            triggerSpotifyLogin();
                        });
                    });
                    return;
                }

                new Thread(() -> {
                    try {
                        String searchedTrackId = SpotifyClient.searchTrackIdByName(trackName, currentAccessToken);
                        if (searchedTrackId == null) {
                            Platform.runLater(() -> showAlert("Error", "No se encontró ninguna canción con el nombre proporcionado."));
                            return;
                        }
                        JsonObject trackInfo = SpotifyClient.getTrackInfo(searchedTrackId, currentAccessToken);
                        String fullTrackName = trackInfo.get("name").getAsString() + " - ";

                        JsonArray artists = trackInfo.getAsJsonArray("artists");
                        for (int i = 0; i < artists.size(); i++) {
                            if (i > 0) fullTrackName += ", ";
                            fullTrackName += artists.get(i).getAsJsonObject().get("name").getAsString();
                        }

                        String albumCover = SpotifyClient.getAlbumCoverUrl(searchedTrackId, currentAccessToken);

                        String finalTrackId = searchedTrackId;
                        String finalTrackName = fullTrackName;
                        showReviewDialog(finalTrackId, finalTrackName, finalReviewUser, albumCover);
                    } catch (IOException e) {
                        Platform.runLater(() -> showAlert("Error", "Error al buscar canción: " + e.getMessage()));
                        e.printStackTrace();
                    }
                }).start();
            } else {
                if (trackId.isEmpty()) {
                    trackId = "unknown_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
                }
                showReviewDialog(trackId, trackName, finalReviewUser, null);
            }
        });
    }

    private void showReviewDialog(String trackId, String trackName, ReviewUserManager.ReviewUser reviewUser, String albumCover) {
        Platform.runLater(() -> {
            Dialog<Pair<String, Integer>> reviewDialog = new Dialog<>();
            reviewDialog.setTitle("Nueva reseña");
            reviewDialog.setHeaderText("Reseña para: " + trackName);

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
                        ReviewManager.addReview(
                                trackId,
                                trackName,
                                currentUserId == -1 ? null : currentUserId,
                                reviewUser.id,
                                review.getKey(),
                                review.getValue(),
                                reviewUser.username
                        );
                        Platform.runLater(() -> {
                            resultArea.setText("¡Reseña publicada con éxito!");
                            refreshSongReviewsTable();
                        });
                    } catch (SQLException e) {
                        Platform.runLater(() -> showAlert("Error", "Error al publicar reseña: " + e.getMessage()));
                        e.printStackTrace();
                    }
                }).start();
            });
        });
    }

    private void searchReviewsDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Buscar reseñas");
        dialog.setHeaderText("Ingresa el nombre de la canción");
        dialog.setContentText("Canción:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(trackName -> {
            if (currentUserId == -1 || currentAccessToken.isEmpty()) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Autenticación requerida");
                    alert.setHeaderText("Debes iniciar sesión para buscar con Spotify");
                    alert.setContentText("Inicia sesión con Spotify para buscar canciones.");
                    ButtonType loginButton = new ButtonType("Iniciar sesión");
                    ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(loginButton, cancelButton);
                    alert.showAndWait().filter(response -> response == loginButton).ifPresent(response -> triggerSpotifyLogin());
                });
                return;
            }

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
                    Platform.runLater(() -> showAlert("Error", "Error al buscar reseñas: " + e.getMessage()));
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
                Platform.runLater(() -> showAlert("Error", "Error al obtener reseñas: " + e.getMessage()));
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
                    Platform.runLater(() -> showAlert("Error", "Error al buscar usuarios: " + e.getMessage()));
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
                Platform.runLater(() -> resultArea.setText("Ahora sigues a " + selected.username));
            } catch (SQLException e) {
                Platform.runLater(() -> showAlert("Error", "Error al seguir usuario: " + e.getMessage()));
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
                Platform.runLater(() -> resultArea.setText("Has dejado de seguir a " + selected.username));
            } catch (SQLException e) {
                Platform.runLater(() -> showAlert("Error", "Error al dejar de seguir: " + e.getMessage()));
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
                Platform.runLater(() -> showAlert("Error", "Error al obtener actividad: " + e.getMessage()));
            }
        }).start();
    }

    private String solicitarNombreDeUsuarioUnico() throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/reviews_db";
        String user = "orpheusers";
        String password = "munyun214";

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nombre de usuario");
        dialog.setHeaderText("Elige un nombre de usuario único para tu reseña");
        dialog.setContentText("Nombre de usuario:");

        while (true) {
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                String username = result.get().trim();
                String sql = "SELECT 1 FROM song_reviews WHERE review_username = ?";
                try (Connection conn = DriverManager.getConnection(url, user, password);
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            return username;
                        } else {
                            Platform.runLater(() -> showAlert("Error", "Ese nombre de usuario ya está en uso. Elige otro."));
                        }
                    }
                } catch (SQLException e) {
                    Platform.runLater(() -> showAlert("Error", "Error al verificar nombre de usuario: " + e.getMessage()));
                    throw e;
                }
            } else {
                Platform.runLater(() -> showAlert("Error", "Debes ingresar un nombre de usuario."));
                return null;
            }
        }
    }
    private void volverASeleccionUsuario() {
        // Cerrar la ventana actual
        primaryStage.close();

        // Iniciar una nueva instancia de la aplicación
        Platform.runLater(() -> {
            try {
                new OrpheusTUI().start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}