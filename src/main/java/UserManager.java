import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "";

    public static class UserProfile {
        public final int id;
        public final String username;
        public final String avatarUrl;
        public final int followersCount;
        public final int followingCount;

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

    public static int getOrCreateUser(String spotifyId, String username, String avatarUrl) throws SQLException {
        String sql = """
            INSERT INTO app_users (spotify_id, username, avatar_url) 
            VALUES (?, ?, ?)
            ON CONFLICT (spotify_id) 
            DO UPDATE SET username = EXCLUDED.username, avatar_url = EXCLUDED.avatar_url 
            RETURNING id""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, spotifyId);
            stmt.setString(2, username);
            stmt.setString(3, avatarUrl);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            throw new SQLException("No se pudo obtener el ID después de la inserción");
        }
    }

    public static void followUser(int followerId, int followedId) throws SQLException {
        if (followerId == followedId) {
            throw new SQLException("No puedes seguirte a ti mismo");
        }

        String sql = "INSERT INTO user_follows (follower_id, followed_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, followerId);
            stmt.setInt(2, followedId);
            stmt.executeUpdate();
        }
    }

    public static void unfollowUser(int followerId, int followedId) throws SQLException {
        String sql = "DELETE FROM user_follows WHERE follower_id = ? AND followed_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, followerId);
            stmt.setInt(2, followedId);
            stmt.executeUpdate();
        }
    }

    public static List<UserProfile> searchUsers(String query) throws SQLException {
        List<UserProfile> users = new ArrayList<>();
        String sql = """
            SELECT u.id, u.username, u.avatar_url, 
                   (SELECT COUNT(*) FROM user_follows WHERE followed_id = u.id) AS followers,
                   (SELECT COUNT(*) FROM user_follows WHERE follower_id = u.id) AS following
            FROM app_users u 
            WHERE username ILIKE ? 
            LIMIT 10""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + query + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                users.add(new UserProfile(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("avatar_url"),
                        rs.getInt("followers"),
                        rs.getInt("following")
                ));
            }
        }
        return users;
    }

    public static UserProfile getUserProfile(int userId) throws SQLException {
        String sql = """
            SELECT u.id, u.username, u.avatar_url, 
                   (SELECT COUNT(*) FROM user_follows WHERE followed_id = u.id) AS followers,
                   (SELECT COUNT(*) FROM user_follows WHERE follower_id = u.id) AS following
            FROM app_users u 
            WHERE u.id = ?""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new UserProfile(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("avatar_url"),
                        rs.getInt("followers"),
                        rs.getInt("following")
                );
            }
            throw new SQLException("Usuario no encontrado");
        }
    }

    public static boolean isFollowing(int followerId, int followedId) throws SQLException {
        String sql = "SELECT 1 FROM user_follows WHERE follower_id = ? AND followed_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, followerId);
            stmt.setInt(2, followedId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }
}