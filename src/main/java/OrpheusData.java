import java.sql.*;

public class OrpheusData {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "";

    /**
     * Establece una conexión con la base de datos PostgreSQL.
     *
     * @return Una conexión activa a la base de datos.
     * @throws SQLException Si ocurre un error al conectar con la base de datos.
     */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Guarda un token de acceso de Spotify en la base de datos.
     *
     * @param accessToken    El token de acceso de Spotify.
     * @param expirationTime El tiempo de expiración del token en milisegundos.
     * @param refreshToken   El token de refresco asociado.
     * @throws SQLException Si ocurre un error al interactuar con la base de datos.
     */
    public void saveAccessToken(String accessToken, long expirationTime, String refreshToken) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS spotify_tokens (" +
                "id SERIAL PRIMARY KEY, " +
                "access_token TEXT NOT NULL, " +
                "expiration_time BIGINT, " +
                "refresh_token TEXT, " +
                "expires_in INTEGER, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        String query = "INSERT INTO spotify_tokens (access_token, expiration_time, refresh_token, expires_in) VALUES (?, ?, ?, ?)";

        try (Connection conn = connect()) {
            conn.createStatement().execute(createTable);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, accessToken);
                stmt.setLong(2, expirationTime);
                stmt.setString(3, refreshToken);
                stmt.setInt(4, (int) ((expirationTime - System.currentTimeMillis()) / 1000));
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Obtiene los datos del token más reciente almacenado en la base de datos.
     *
     * @return Un objeto TokenData con los datos del token, o null si no hay tokens disponibles.
     * @throws SQLException Si ocurre un error al consultar la base de datos.
     */
    public TokenData getTokenData() throws SQLException {
        String query = "SELECT access_token, expiration_time, refresh_token, expires_in FROM spotify_tokens ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TokenData(
                        rs.getString("access_token"),
                        rs.getLong("expiration_time"),
                        rs.getString("refresh_token")
                );
            }
        }
        return null;
    }

    /**
     * Elimina todos los tokens de acceso almacenados en la base de datos.
     *
     * @throws SQLException Si ocurre un error al interactuar con la base de datos.
     */
    public void deleteAccessToken() throws SQLException {
        String query = "DELETE FROM spotify_tokens";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }

    /**
     * Clase interna para almacenar los datos del token de acceso.
     */
    public static class TokenData {
        public final String accessToken;
        public final long expirationTime;
        public final String refreshToken;

        public TokenData(String accessToken, long expirationTime, String refreshToken) {
            this.accessToken = accessToken;
            this.expirationTime = expirationTime;
            this.refreshToken = refreshToken;
        }

        /**
         * Verifica si el token ha expirado.
         *
         * @return true si el token ha expirado, false en caso contrario.
         */
        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
    }
}