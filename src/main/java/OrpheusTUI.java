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

/**
 * OrpheusTUI es la clase principal de la interfaz gráfica de usuario (GUI) de la aplicación Orpheus.
 * Utiliza JavaFX para mostrar y gestionar las pestañas de usuario, premium y administrador,
 * así como las funcionalidades de reseñas, música y administración de usuarios.
 * Implementa ReviewVoteSystem.VoteUpdateListener para actualizar la interfaz tras acciones de voto.
 *
 * Funcionalidades principales:
 * <ul>
 *   <li>Inicio de sesión y registro de usuarios normales y premium</li>
 *   <li>Gestión de reseñas de canciones</li>
 *   <li>Integración con Spotify para usuarios premium</li>
 *   <li>Administración de usuarios y exportación de datos para administradores</li>
 * </ul>
 *
 * @author Elismoud
 * @version 1.0
 * @since 2024-06-01
 */
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
    private String currentAccessToken = "";
    private String currentRefreshToken = "";
    private TableView<ReviewManager.Review> reviewsTable = new TableView<>();
    private TableView<UserManager.UserProfile> usersTable = new TableView<>();
    private ObservableList<ReviewManager.Review> reviewsData = FXCollections.observableArrayList();
    private ObservableList<UserManager.UserProfile> usersData = FXCollections.observableArrayList();
    private ReviewUserManager.ReviewUser currentReviewUser = null;
    private boolean isAdminMode = false;
    private static final String ADMIN_PASSWORD = "admin123";

    public static void main(String[] args) {
        System.setProperty("javafx.macos.disableNativePopup", "true");
        try {
            ReviewManager.inicializarTablas();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        Spark.port(8080);
        launch(args);
    }

    /**
     * Muestra un cuadro de diálogo de alerta con el mensaje proporcionado.
     */
    @Override
    public void start(Stage primaryStage) {
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        this.primaryStage = primaryStage;
        this.voteSystem = new ReviewVoteSystem(this);
        primaryStage.setTitle("Orpheus - Spotify Social");

        boolean loginExitoso = false;
        String userType = null;
        while (!loginExitoso) {
            ChoiceDialog<String> userTypeDialog = new ChoiceDialog<>("Usuario", "Usuario", "Premium", "Admin");
            userTypeDialog.setTitle("Seleccionar Tipo de Usuario");
            userTypeDialog.setHeaderText("¿Qué tipo de usuario eres?");
            userTypeDialog.setContentText("Selecciona:");
            Optional<String> userTypeResult = userTypeDialog.showAndWait();

            if (!userTypeResult.isPresent()) {
                showAlert("Error", "Usuario no seleccionado, cerrando aplicación.");
                Platform.exit();
                return;
            }
            userType = userTypeResult.get();
            if (userType.equals("Admin")) {
                handleAdminLogin();
                loginExitoso = isAdminMode;
            } else {
                loginExitoso = handleUserLogin(userType);
            }
        }

        HBox userPanel = createUserPanel();
        resultContainer = new StackPane();
        resultContainer.getChildren().add(resultArea);
        ScrollPane resultScroll = new ScrollPane(resultContainer);
        resultScroll.setFitToWidth(true);
        resultScroll.setPrefHeight(100);

        tabPane = new TabPane();
        if (isAdminMode) {
            tabPane.getTabs().add(createAdminTab());
        } else {
            if (userType.equals("Usuario")) {
                tabPane.getTabs().addAll(createReviewsTab());
            } else if (userType.equals("Premium")) {
                tabPane.getTabs().addAll(createReviewsTab(), createMusicTab());
            }
        }

        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(userPanel);
        mainLayout.setCenter(tabPane);
        mainLayout.setBottom(resultScroll);

        primaryStage.setScene(new Scene(mainLayout, 1000, 800));
        primaryStage.show();

        if (!isAdminMode) {
            configureSpotifyCallback();
        }
    }

    /**
     * Configura el callback de Spotify para manejar la autenticación y el acceso a la API.
     */
    private boolean handleUserLogin(String userType) {
        Dialog<ButtonType> loginDialog = new Dialog<>();
        loginDialog.setTitle("Inicio de Sesión");
        loginDialog.setHeaderText("Inicia sesión o regístrate como " + userType.toLowerCase() + " usuario");

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
            return false;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Debes ingresar un nombre de usuario y contraseña.");
            return false;
        }

        try {
            if (userType.equals("Premium")) {
                if (loginResult.get().getText().equals("Iniciar Sesión")) {
                    currentReviewUser = ReviewUserManager.loginPremiumUser(username, password);
                } else {
                    currentReviewUser = ReviewUserManager.registerPremiumUser(username, password);
                }
            } else {
                if (loginResult.get().getText().equals("Iniciar Sesión")) {
                    currentReviewUser = ReviewUserManager.loginUser(username, password);
                } else {
                    currentReviewUser = ReviewUserManager.registerUser(username, password);
                }
            }
            resultArea.setText("Bienvenido, " + currentReviewUser.username + "!");
            return true;
        } catch (IllegalArgumentException | SQLException e) {
            showAlert("Error", e.getMessage());
            return false;
        }
    }

    /**
     * Crea un panel de usuario que muestra la información del usuario actual y permite cerrar sesión.
     *
     * @return Un HBox que contiene la información del usuario y los botones de acción.
     */
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
                // Evitar IndexOutOfBoundsException al acceder a las pestañas
                if (tabPane != null && tabPane.getTabs().size() > 2) {
                    Tab tab = tabPane.getTabs().get(2);
                    if (tab != null && tab.getContent() instanceof VBox vbox && !vbox.getChildren().isEmpty() && vbox.getChildren().get(0) instanceof HBox buttonBox) {
                        buttonBox.getChildren().removeIf(node -> node instanceof Button && ((Button) node).getText().equals("Iniciar sesión en Spotify"));
                        if (buttonBox.getChildren().stream().noneMatch(node -> node instanceof Button && ((Button) node).getText().equals("Iniciar sesión en Spotify"))) {
                            Button loginButton = new Button("Iniciar sesión en Spotify");
                            loginButton.setOnAction(event -> triggerSpotifyLogin());
                            buttonBox.getChildren().add(loginButton);
                        }
                    }
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

    /**
     * Crea la pestaña de administración para usuarios con permisos de administrador.
     *
     * @return Un objeto Tab configurado con las opciones de administración.
     */
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

    /**
     * Crea una pestaña que contiene opciones y funcionalidades para acciones relacionadas con la música.
     *
     * La pestaña incluye botones para iniciar sesión en Spotify, añadir canciones y álbumes a la biblioteca,
     * ver el perfil del usuario y mostrar las canciones y artistas principales.
     *
     * @return Una pestaña configurada para funcionalidades musicales.
     */
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

    /**
     *Crea una pestaña que permite a los usuarios interactuar con las reseñas de canciones.
     *
     * @return Una pestaña configurada para gestionar reseñas.
     */
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
            if (tabPane.getTabs().size() > 1 && newTab == tabPane.getTabs().get(1)) {
                buttonBox.getChildren().remove(loginButton);
                if (currentUserId == -1) {
                    buttonBox.getChildren().add(loginButton);
                }
                refreshSongReviewsTable();
            }
        });

        Tab tab = new Tab("Reseñas", reviewsTab);
        tab.setClosable(false);
        tab.setId("reviewsTab");
        return tab;
    }

    /**
     * Inicializa la tabla de reseñas de canciones con las columnas y datos necesarios.
     * Esta tabla muestra información detallada sobre las reseñas de canciones,
     * incluyendo ID, nombre de la canción, usuario que reseñó, texto de la reseña,
     * rating, votos útiles y no útiles, y fecha de creación.
     * Además, incluye botones para votar "útil" o "no útil" en cada reseña.
     * Esta tabla se utiliza para mostrar las reseñas de canciones
     * y permitir a los usuarios interactuar con ellas mediante votos.
     *
     */
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

        TableColumn<ReviewManager.Review, Void> voteUsefulCol = new TableColumn<>("Útil");
        voteUsefulCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("👍");
            {
                btn.setOnAction(e -> {
                    ReviewManager.Review review = getTableView().getItems().get(getIndex());

                    Integer reviewUserId = currentReviewUser != null ? currentReviewUser.id : null;
                    voteSystem.handleVote(review.id, null, currentReviewUser != null ? currentReviewUser.id : null, true);
                });
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

                    Integer reviewUserId = currentReviewUser != null ? currentReviewUser.id : null;
                    voteSystem.handleVote(review.id, null, currentReviewUser != null ? currentReviewUser.id : null, false);
                });
                btn.setDisable(currentReviewUser == null);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        reviewsTable.getColumns().addAll(voteUsefulCol, voteNotUsefulCol);

        reviewsTable.setPrefHeight(400);
        reviewsTable.setMinHeight(200);

        refreshSongReviewsTable();
    }

    /**
     * Configura el callback de Spotify para manejar la autenticación y el acceso a la API.
     */
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
                        try {
                            ((ImageView)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(0)).setImage(new Image(currentAvatarUrl));
                            ((Label)((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(1)).setText(currentUsername);
                            resultArea.setText("¡Bienvenido, " + currentUsername + "! User ID: " + currentUserId);
                            System.out.println("UI actualizada: userId=" + currentUserId + ", accessToken=" + currentAccessToken);
                            try {
                                if (tabPane != null && tabPane.getTabs().size() > 1) {
                                    Tab tab = tabPane.getTabs().get(1);
                                    if (tab != null && tab.getContent() instanceof VBox vbox && !vbox.getChildren().isEmpty() && vbox.getChildren().get(0) instanceof HBox buttonBox) {
                                        buttonBox.getChildren().removeIf(node -> node instanceof Button && ((Button) node).getText().equals("Iniciar sesión en Spotify"));
                                    }
                                }
                            } catch (IndexOutOfBoundsException | ClassCastException ex) {
                                resultArea.setText("todo funciona bien.");
                            }
                        } catch (Exception ex) {
                            resultArea.setText("Error inesperado actualizando la UI tras login de Spotify: " + ex.getMessage());
                        }
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

    /**
     * Inicia el proceso de inicio de sesión en Spotify.
     */
    private void triggerSpotifyLogin() {
        try {
            SpotifyToken token = SpotifyToken.getValidToken();
            if (token != null) {
                currentAccessToken = token.getAccessToken();
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
                    ((ImageView) ((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(0))
                            .setImage(new Image(currentAvatarUrl));
                    ((Label) ((HBox) ((BorderPane) primaryStage.getScene().getRoot()).getTop()).getChildren().get(1))
                            .setText(currentUsername);
                    resultArea.setText("¡Bienvenido, " + currentUsername + "! User ID: " + currentUserId);
                });
                return;
            }
        } catch (SQLException | IOException e) {
            Platform.runLater(() -> showAlert("Error", "Error al verificar tokens: " + e.getMessage()));
            return;
        }

        if (!SpotifyClient.isInternetAvailable()) {
            Platform.runLater(() -> showAlert("Error", "No hay conexión a Internet. Por favor, verifica tu conexión."));
            return;
        }

        try {
            String scope = URLEncoder.encode(
                    "user-top-read user-read-recently-played user-library-modify playlist-modify-private",
                    StandardCharsets.UTF_8);
            String authUrl = "https://accounts.spotify.com/authorize?" +
                    "client_id=" + SpotifyClient.CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyClient.REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + scope +
                    "&show_dialog=true";

            getHostServices().showDocument(authUrl);
        } catch (Exception e) {
            Platform.runLater(() -> showAlert("Error", "Error al iniciar sesión en Spotify: " + e.getMessage()));
        }
    }

    /**
     * Muestra las canciones principales del usuario autenticado.
     *
     * @param accessToken El token de acceso de Spotify.
     * @throws IOException Si ocurre un error al obtener los datos.
     */
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

    /**
     * Muestra los artistas principales del usuario autenticado.
     *
     * @param accessToken El token de acceso de Spotify.
     * @throws IOException Sí ocurre un error al obtener los datos.
     */
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

    /**
     * Obtiene un token de acceso válido para Spotify.
     *
     * @return El token de acceso válido.
     * @throws SQLException Si ocurre un error al acceder a la base de datos.
     * @throws IOException Si ocurre un error al obtener el token.
     */
    private String getValidAccessToken() throws SQLException, IOException {
        SpotifyToken token = SpotifyToken.getValidToken();
        if (token == null) {
            System.out.println("No se encontró un token válido en la base de datos");
            throw new IOException("No token available. Please login first.");
        }
        System.out.println("Token recuperado: " + token.getAccessToken());
        return token.getAccessToken();
    }

    /**
     * Método llamado cuando un voto se registra exitosamente.
     */
    @Override
    public void onVoteSuccess() {
        refreshSongReviewsTable();
        showAlert("Éxito", "Tu voto se registró correctamente");
    }

    /**
     * Método llamado cuando un voto no se puede registrar.
     *
     * @param errorMessage El mensaje de error asociado.
     */
    @Override
    public void onVoteFailure(String errorMessage) {
        showAlert("Error", "No se pudo registrar el voto: " + errorMessage);
    }

    /**
     * Muestra un diálogo para buscar reseñas por canción.
     * Permite al usuario ingresar el nombre de la canción y muestra las reseñas correspondientes.
     */
    private void refreshSongReviewsTable() {
        try {
            reviewsData.setAll(ReviewManager.getAllSongReviews());
        } catch (SQLException e) {
            Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las reseñas: " + e.getMessage()));
        }
    }

    /**
     * Muestra un cuadro de diálogo de alerta con el mensaje proporcionado.
     *
     * @param title El título de la alerta.
     * @param message El mensaje de la alerta.
     */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Muestra un diálogo para buscar reseñas por canción.
     * Permite al usuario ingresar el nombre de la canción y muestra las reseñas correspondientes.
     */
    private void leaveReviewDialog() {
        if (currentReviewUser == null && !isAdminMode) {
            showAlert("Error", "Debes iniciar sesión como usuario de reseñas.");
            return;
        }

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

            if (currentReviewUser != null && reviewUser.id == currentReviewUser.id) {

            } else {

                TextInputDialog passwordDialog = new TextInputDialog();
                passwordDialog.setTitle("Verificación");
                passwordDialog.setHeaderText("Ingresa la contraseña para " + reviewUsername);
                passwordDialog.setContentText("Contraseña:");
                Optional<String> passwordResult = passwordDialog.showAndWait();

                if (passwordResult.isEmpty() || passwordResult.get().isEmpty()) {
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

    /**
     * Muestra un diálogo para dejar una reseña de una canción.
     * Permite al usuario ingresar el ID de la canción, el nombre y la reseña.
     * Si se proporciona un ID de canción, se busca en Spotify para obtener más información.
     *
     * @param trackId ID de la canción (opcional)
     * @param trackName Nombre de la canción
     * @param reviewUser Usuario que deja la reseña
     * @param albumCover URL de la portada del álbum (opcional)
     */
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

    /**
     * Muestra un diálogo para buscar reseñas por nombre de canción.
     * Si el usuario no ha iniciado sesión, solicita que inicie sesión con Spotify.
     */
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

    /**
     * Muestra las reseñas más populares
     */
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

    /**
     * Método para cerrar la aplicación y volver a la selección de usuario.
     * Este método cierra la ventana actual y lanza una nueva instancia de OrpheusTUI.
     */
    public void volverASeleccionUsuario() {
        primaryStage.close();
        Platform.runLater(() -> {
            try {
                new OrpheusTUI().start(new Stage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Método para manejar el inicio de sesión como administrador.
     * Este método solicita la contraseña de administrador y, si es correcta,
     * cambia el modo de la aplicación a administrador.
     * Si la contraseña es incorrecta, se muestra un mensaje de error y se cierra la aplicación.
     */
    private void handleAdminLogin() {
        TextInputDialog passwordDialog = new TextInputDialog();
        passwordDialog.setTitle("Autenticación de Admin");
        passwordDialog.setHeaderText("Ingresa la contraseña de administrador");
        passwordDialog.setContentText("Contraseña:");

        Optional<String> passwordResult = passwordDialog.showAndWait();
        if (passwordResult.isPresent() && passwordResult.get().equals(ADMIN_PASSWORD)) {
            isAdminMode = true;
            resultArea.setText("Inicio de sesión como administrador exitoso.");
        } else {
            showAlert("Error", "Contraseña de administrador incorrecta.");
            Platform.exit();
        }
    }
}