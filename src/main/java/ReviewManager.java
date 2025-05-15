import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReviewManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "";

    public static class Review {
        public final int id;
        public final String username;
        public final String avatarUrl;
        public final String trackId;
        public final String trackName;
        public final String reviewText;
        public final int rating;
        public final int usefulVotes;
        public final int notUsefulVotes;
        public final Timestamp createdAt;

        public Review(int id, String username, String avatarUrl, String trackId, String trackName,
                      String reviewText, int rating, int usefulVotes, int notUsefulVotes, Timestamp createdAt) {
            this.id = id;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.trackId = trackId;
            this.trackName = trackName;
            this.reviewText = reviewText;
            this.rating = rating;
            this.usefulVotes = usefulVotes;
            this.notUsefulVotes = notUsefulVotes;
            this.createdAt = createdAt;
        }

        public String getFormattedDate() {
            return createdAt.toString().substring(0, 16);
        }
    }

    public static void addReview(String trackId, String trackName, int userId, String reviewText, int rating) throws SQLException {
        String sql = """
            INSERT INTO song_reviews 
            (spotify_track_id, track_name, user_id, review_text, rating) 
            VALUES (?, ?, ?, ?, ?)""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackId);
            stmt.setString(2, trackName);
            stmt.setInt(3, userId);
            stmt.setString(4, reviewText);
            stmt.setInt(5, rating);
            stmt.executeUpdate();
        }
    }

    public static List<Review> getReviewsForTrack(String trackId) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.review_text, r.rating, 
                   r.useful_votes, r.not_useful_votes, r.created_at,
                   u.username, u.avatar_url
            FROM song_reviews r
            JOIN app_users u ON r.user_id = u.id
            WHERE r.spotify_track_id = ?
            ORDER BY (r.useful_votes - r.not_useful_votes) DESC, r.created_at DESC""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                reviews.add(new Review(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("avatar_url"),
                        rs.getString("spotify_track_id"),
                        rs.getString("track_name"),
                        rs.getString("review_text"),
                        rs.getInt("rating"),
                        rs.getInt("useful_votes"),
                        rs.getInt("not_useful_votes"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return reviews;
    }

    public static List<Review> getTrendingReviews(int limit) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.review_text, r.rating, 
                   r.useful_votes, r.not_useful_votes, r.created_at,
                   u.username, u.avatar_url
            FROM song_reviews r
            JOIN app_users u ON r.user_id = u.id
            ORDER BY (r.useful_votes * 2 - r.not_useful_votes) DESC, r.created_at DESC
            LIMIT ?""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                reviews.add(new Review(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("avatar_url"),
                        rs.getString("spotify_track_id"),
                        rs.getString("track_name"),
                        rs.getString("review_text"),
                        rs.getInt("rating"),
                        rs.getInt("useful_votes"),
                        rs.getInt("not_useful_votes"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return reviews;
    }

    public static void voteReview(int reviewId, int userId, boolean isUseful) throws SQLException {
        String checkVoteSql = "SELECT 1 FROM review_votes WHERE review_id = ? AND user_id = ?";
        String insertVoteSql = "INSERT INTO review_votes (review_id, user_id, is_useful) VALUES (?, ?, ?)";
        String updateReviewSql = isUseful ?
                "UPDATE song_reviews SET useful_votes = useful_votes + 1 WHERE id = ?" :
                "UPDATE song_reviews SET not_useful_votes = not_useful_votes + 1 WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);

            // Verificar si ya votó
            try (PreparedStatement stmt = conn.prepareStatement(checkVoteSql)) {
                stmt.setInt(1, reviewId);
                stmt.setInt(2, userId);
                if (stmt.executeQuery().next()) {
                    throw new SQLException("Ya has votado esta reseña");
                }
            }

            // Registrar voto
            try (PreparedStatement stmt = conn.prepareStatement(insertVoteSql)) {
                stmt.setInt(1, reviewId);
                stmt.setInt(2, userId);
                stmt.setBoolean(3, isUseful);
                stmt.executeUpdate();
            }

            // Actualizar conteo
            try (PreparedStatement stmt = conn.prepareStatement(updateReviewSql)) {
                stmt.setInt(1, reviewId);
                stmt.executeUpdate();
            }

            conn.commit();
        }
    }

    /**
     * Verifica si un usuario ya votó una reseña
     */
    public static boolean alreadyVoted(int reviewId, int userId) throws SQLException {
        String sql = "SELECT 1 FROM review_votes WHERE review_id = ? AND user_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reviewId);
            stmt.setInt(2, userId);
            return stmt.executeQuery().next();
        }
    }


    public static List<Review> getUserReviews(int userId) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.review_text, r.rating, 
                   r.useful_votes, r.not_useful_votes, r.created_at,
                   u.username, u.avatar_url
            FROM song_reviews r
            JOIN app_users u ON r.user_id = u.id
            WHERE r.user_id = ?
            ORDER BY r.created_at DESC""";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                reviews.add(new Review(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("avatar_url"),
                        rs.getString("spotify_track_id"),
                        rs.getString("track_name"),
                        rs.getString("review_text"),
                        rs.getInt("rating"),
                        rs.getInt("useful_votes"),
                        rs.getInt("not_useful_votes"),
                        rs.getTimestamp("created_at")
                ));
            }
        }
        return reviews;
    }
}