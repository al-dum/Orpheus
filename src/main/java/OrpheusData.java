import java.sql.Connection;
            import java.sql.DriverManager;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.sql.*;

            /**
             * Clase que gestiona la persistencia de datos para la aplicación Orpheus.
             * Proporciona métodos para almacenar y recuperar tokens de acceso de Spotify en una base de datos SQLite.
             */
            public class OrpheusData {
                /** URL de conexión a la base de datos SQLite */
                private static final String DB_URL = "jdbc:sqlite:orpheus.db";

                /**
                 * Establece una conexión con la base de datos.
                 *
                 * @return Una conexión activa a la base de datos
                 * @throws SQLException Si ocurre un error al conectar con la base de datos
                 */
                public Connection connect() throws SQLException {
                    return DriverManager.getConnection(DB_URL);
                }

                /**
                 * Guarda un token de acceso junto con su tiempo de expiración en la base de datos.
                 * Si la tabla no existe, la crea automáticamente.
                 *
                 * @param accessToken Token de acceso a guardar
                 * @param expirationTime Tiempo de expiración del token en milisegundos desde epoch
                 * @throws SQLException Si ocurre un error al guardar los datos
                 */
                public void saveAccessToken(String accessToken, long expirationTime) throws SQLException {
                    String createTable = "CREATE TABLE IF NOT EXISTS spotify_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT, access_token TEXT NOT NULL, expiration_time INTEGER);";
                    String query = "INSERT INTO spotify_tokens (access_token, expiration_time) VALUES (?, ?)";
                    try (Connection conn = connect()) {
                        conn.createStatement().execute(createTable);
                        try (PreparedStatement stmt = conn.prepareStatement(query)) {
                            stmt.setString(1, accessToken);
                            stmt.setLong(2, expirationTime);
                            stmt.executeUpdate();
                        }
                    }
                }

                /**
                 * Recupera el token de acceso más reciente almacenado en la base de datos.
                 *
                 * @return El token de acceso más reciente o null si no hay tokens almacenados
                 * @throws SQLException Si ocurre un error al recuperar los datos
                 */
                public String getAccessToken() throws SQLException {
                    String query = "SELECT access_token FROM spotify_tokens ORDER BY id DESC LIMIT 1";
                    try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            return rs.getString("access_token");
                        }
                    }
                    return null;
                }

                /**
                 * Elimina todos los tokens de acceso almacenados en la base de datos.
                 *
                 * @throws SQLException Si ocurre un error durante la operación de eliminación
                 */
                public void deleteAccessToken() throws SQLException {
                    String query = "DELETE FROM spotify_tokens";
                    try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.executeUpdate();
                    }
                }
            }