import java.sql.*;

public class UserManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "";

    /**
     * Representa un perfil de usario en la app.
     */
    public static class UserProfile {
        public final int id;
        public final String username;
        public final String avatarUrl;
        public final int followersCount;
        public final int followingCount;

        /**
         * Crea un perfil de usuario.
         *
         * @param id             El identificador único del usuario.
         * @param username       El nombre de usuario.
         * @param avatarUrl      La URL del avatar del usuario.
         * @param followersCount El número de seguidores del usuario.
         * @param followingCount El número de usuarios que sigue el usuario.
         */
        public UserProfile(int id, String username, String avatarUrl, int followersCount, int followingCount) {
            this.id = id;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.followersCount = followersCount;
            this.followingCount = followingCount;
        }


        public int getId() {
            return id;
        }

        public String getDisplayName() {
            return username;
        }
    }

    /**
     * Obtiene o crea un usuario en la base de datos.
     *
     * @param spotifyId  El ID de Spotify del usuario.
     * @param username   El nombre de usuario.
     * @param avatarUrl  La URL del avatar del usuario.
     * @return El ID del usuario creado o existente.
     * @throws SQLException Si ocurre un error al acceder a la base de datos.
     */
    public static int getOrCreateUser(String spotifyId, String username, String avatarUrl) throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/reviews_db";
        String user = "orpheusers";
        String password = "munyun214";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String selectSql = "SELECT id FROM app_users WHERE spotify_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, spotifyId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }

            String insertSql = "INSERT INTO app_users (spotify_id, username, avatar_url) VALUES (?, ?, ?) RETURNING id";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, spotifyId);
                stmt.setString(2, username);
                stmt.setString(3, avatarUrl);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
            return -1; // Error
        }
    }
}