import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReviewManager {
    private static final String DB_URL = "jdbc:postgresql://localhost:5433/reviews_db";
    private static final String DB_USER = "orpheusers";
    private static final String DB_PASSWORD = "munyun214";

    public static class Review {
        public final int id;
        public final String trackId;
        public final String trackName;
        public final Integer userId; // Spotify user
        public final Integer reviewUserId; // Review user
        public final String reviewText;
        public final int rating;
        public final int usefulVotes;
        public final int notUsefulVotes;
        public final LocalDateTime createdAt;
        public final String reviewUsername;
        public final String avatarUrl;

        public Review(int id, String trackId, String trackName, Integer userId, Integer reviewUserId, String reviewText, int rating,
                      int usefulVotes, int notUsefulVotes, LocalDateTime createdAt, String reviewUsername, String avatarUrl) {
            this.id = id;
            this.trackId = trackId;
            this.trackName = trackName;
            this.userId = userId;
            this.reviewUserId = reviewUserId;
            this.reviewText = reviewText;
            this.rating = rating;
            this.usefulVotes = usefulVotes;
            this.notUsefulVotes = notUsefulVotes;
            this.createdAt = createdAt;
            this.reviewUsername = reviewUsername;
            this.avatarUrl = avatarUrl;
        }

        public String getFormattedDate() {
            return createdAt.toString().substring(0, 16);
        }
    }

    public static void inicializarTablas() throws SQLException {
        String url = "jdbc:postgresql://localhost:5433/reviews_db";
        String user = "orpheusers";
        String password = "munyun214";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {


            String sql = """
    CREATE TABLE IF NOT EXISTS app_users (
        id SERIAL PRIMARY KEY,
        spotify_id VARCHAR(255) UNIQUE,
        username VARCHAR(255) NOT NULL,
        avatar_url TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS review_users (
        id SERIAL PRIMARY KEY,
        spotify_id VARCHAR(255) UNIQUE,
        username VARCHAR(255) UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS premium_users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(255) UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS song_reviews (
        id SERIAL PRIMARY KEY,
        spotify_track_id VARCHAR(255) NOT NULL,
        track_name TEXT NOT NULL,
        user_id INTEGER,
        review_user_id INTEGER,
        review_text TEXT NOT NULL,
        rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
        useful_votes INTEGER DEFAULT 0,
        not_useful_votes INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        review_username VARCHAR(255) NOT NULL,
        FOREIGN KEY (user_id) REFERENCES app_users(id),
        FOREIGN KEY (review_user_id) REFERENCES review_users(id) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS review_votes (
        review_id INTEGER NOT NULL,
        user_id INTEGER,
        review_user_id INTEGER,
        is_useful BOOLEAN NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (review_id, user_id, review_user_id),
        FOREIGN KEY (review_id) REFERENCES song_reviews(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES app_users(id),
        FOREIGN KEY (review_user_id) REFERENCES review_users(id) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS user_follows (
        id SERIAL PRIMARY KEY,
        follower_id INTEGER NOT NULL,
        followed_id INTEGER NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (follower_id) REFERENCES app_users(id) ON DELETE CASCADE,
        FOREIGN KEY (followed_id) REFERENCES app_users(id) ON DELETE CASCADE,
        UNIQUE (follower_id, followed_id)
    );
    CREATE TABLE IF NOT EXISTS listening_history (
        id SERIAL PRIMARY KEY,
        user_id INTEGER NOT NULL,
        spotify_track_id VARCHAR(255) NOT NULL,
        played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
    );
""";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            String checkSongReviewsColumnSql = """
                SELECT column_name 
                FROM information_schema.columns 
                WHERE table_name = 'song_reviews' AND column_name = 'review_user_id'
                """;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSongReviewsColumnSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    String alterTableSql = """
                        ALTER TABLE song_reviews 
                        ADD COLUMN review_user_id INTEGER,
                        ADD CONSTRAINT fk_review_user_id 
                        FOREIGN KEY (review_user_id) REFERENCES review_users(id) ON DELETE CASCADE
                        """;
                    try (Statement alterStmt = conn.createStatement()) {
                        alterStmt.execute(alterTableSql);
                    }
                }
            }

            String checkVotesUserIdNotNullSql = """
                SELECT is_nullable 
                FROM information_schema.columns 
                WHERE table_name = 'review_votes' AND column_name = 'user_id'
                """;
            boolean userIdNotNull = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkVotesUserIdNotNullSql);
                 ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    userIdNotNull = rs.getString("is_nullable").equals("NO");
                }
            }

            if (userIdNotNull) {
                String dropPrimaryKeySql = """
                    ALTER TABLE review_votes 
                    DROP CONSTRAINT review_votes_pkey
                    """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(dropPrimaryKeySql);
                }

                String removeNotNullSql = """
                    ALTER TABLE review_votes 
                    ALTER COLUMN user_id DROP NOT NULL
                    """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(removeNotNullSql);
                }
                String checkVotesReviewUserIdSql = """
                    SELECT column_name 
                    FROM information_schema.columns 
                    WHERE table_name = 'review_votes' AND column_name = 'review_user_id'
                    """;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkVotesReviewUserIdSql);
                     ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        String addReviewUserIdSql = """
                            ALTER TABLE review_votes 
                            ADD COLUMN review_user_id INTEGER,
                            ADD CONSTRAINT fk_review_votes_user_id 
                            FOREIGN KEY (review_user_id) REFERENCES review_users(id) ON DELETE CASCADE
                            """;
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(addReviewUserIdSql);
                        }
                    }
                }
                String addPrimaryKeySql = """
                    ALTER TABLE review_votes 
                    ADD CONSTRAINT review_votes_pkey 
                    PRIMARY KEY (review_id, user_id, review_user_id)
                    """;
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(addPrimaryKeySql);
                }
            }
        }catch (SQLException e) {
            System.err.println("Error al inicializar tablas: " + e.getMessage());
            throw e;
        }
    }

    public static void addReview(String trackId, String trackName, Integer userId, Integer reviewUserId, String reviewText, int rating, String reviewUsername) throws SQLException {
        if (trackId == null || trackId.isEmpty() || trackId.length() > 255) {
            throw new SQLException("Track ID inválido: debe ser no nulo y menor a 255 caracteres.");
        }
        String sql = "INSERT INTO song_reviews (spotify_track_id, track_name, user_id, review_user_id, review_text, rating, review_username) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackId);
            stmt.setString(2, trackName);
            if (userId != null) {
                stmt.setInt(3, userId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            if (reviewUserId != null) {
                stmt.setInt(4, reviewUserId);
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, reviewText);
            stmt.setInt(6, rating);
            stmt.setString(7, reviewUsername);
            stmt.executeUpdate();
        }
    }

    public static boolean alreadyVoted(int reviewId, Integer spotifyUserId, Integer reviewUserId) throws SQLException {
        String sql = "SELECT 1 FROM review_votes WHERE review_id = ? AND (user_id = ? OR review_user_id = ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reviewId);
            if (spotifyUserId != null) {
                stmt.setInt(2, spotifyUserId);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            if (reviewUserId != null) {
                stmt.setInt(3, reviewUserId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            return stmt.executeQuery().next();
        }
    }

    public static void voteReview(int reviewId, Integer spotifyUserId, Integer reviewUserId, boolean isUseful) throws SQLException {
        String checkVoteSql = "SELECT 1 FROM review_votes WHERE review_id = ? AND (user_id = ? OR review_user_id = ?)";
        String insertVoteSql = "INSERT INTO review_votes (review_id, user_id, review_user_id, is_useful) VALUES (?, ?, ?, ?)";
        String updateReviewSql = isUseful ?
                "UPDATE song_reviews SET useful_votes = useful_votes + 1 WHERE id = ?" :
                "UPDATE song_reviews SET not_useful_votes = not_useful_votes + 1 WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(checkVoteSql)) {
                    stmt.setInt(1, reviewId);
                    if (spotifyUserId != null) {
                        stmt.setInt(2, spotifyUserId);
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }
                    if (reviewUserId != null) {
                        stmt.setInt(3, reviewUserId);
                    } else {
                        stmt.setNull(3, Types.INTEGER);
                    }
                    if (stmt.executeQuery().next()) {
                        throw new SQLException("Ya has votado esta reseña");
                    }
                }
                try (PreparedStatement stmt = conn.prepareStatement(insertVoteSql)) {
                    stmt.setInt(1, reviewId);
                    if (spotifyUserId != null) {
                        stmt.setInt(2, spotifyUserId);
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }
                    if (reviewUserId != null) {
                        stmt.setInt(3, reviewUserId);
                    } else {
                        stmt.setNull(3, Types.INTEGER);
                    }
                    stmt.setBoolean(4, isUseful);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(updateReviewSql)) {
                    stmt.setInt(1, reviewId);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static List<Review> getAllSongReviews() throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.user_id, 
                   COALESCE(r.review_user_id, NULL) AS review_user_id, 
                   r.review_text, r.rating, r.useful_votes, r.not_useful_votes, 
                   r.created_at, r.review_username, u.avatar_url
            FROM song_reviews r
            LEFT JOIN app_users u ON r.user_id = u.id
            ORDER BY r.created_at DESC""";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                reviews.add(new Review(
                        rs.getInt("id"),
                        rs.getString("spotify_track_id"),
                        rs.getString("track_name"),
                        rs.getObject("user_id") != null ? rs.getInt("user_id") : null,
                        rs.getObject("review_user_id") != null ? rs.getInt("review_user_id") : null,
                        rs.getString("review_text"),
                        rs.getInt("rating"),
                        rs.getInt("useful_votes"),
                        rs.getInt("not_useful_votes"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("review_username"),
                        rs.getString("avatar_url")
                ));
            }
        }
        return reviews;
    }

    public static List<Review> getReviewsForTrack(String trackId) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.user_id, 
                   COALESCE(r.review_user_id, NULL) AS review_user_id, 
                   r.review_text, r.rating, r.useful_votes, r.not_useful_votes, 
                   r.created_at, r.review_username, u.avatar_url
            FROM song_reviews r
            LEFT JOIN app_users u ON r.user_id = u.id
            WHERE r.spotify_track_id = ?
            ORDER BY (r.useful_votes - r.not_useful_votes) DESC, r.created_at DESC""";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(new Review(
                            rs.getInt("id"),
                            rs.getString("spotify_track_id"),
                            rs.getString("track_name"),
                            rs.getObject("user_id") != null ? rs.getInt("user_id") : null,
                            rs.getObject("review_user_id") != null ? rs.getInt("review_user_id") : null,
                            rs.getString("review_text"),
                            rs.getInt("rating"),
                            rs.getInt("useful_votes"),
                            rs.getInt("not_useful_votes"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getString("review_username"),
                            rs.getString("avatar_url")
                    ));
                }
            }
        }
        return reviews;
    }

    public static List<Review> getTrendingReviews(int limit) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.user_id, 
                   COALESCE(r.review_user_id, NULL) AS review_user_id, 
                   r.review_text, r.rating, r.useful_votes, r.not_useful_votes, 
                   r.created_at, r.review_username, u.avatar_url
            FROM song_reviews r
            LEFT JOIN app_users u ON r.user_id = u.id
            ORDER BY (r.useful_votes * 2 - r.not_useful_votes) DESC, r.created_at DESC
            LIMIT ?""";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(new Review(
                            rs.getInt("id"),
                            rs.getString("spotify_track_id"),
                            rs.getString("track_name"),
                            rs.getObject("user_id") != null ? rs.getInt("user_id") : null,
                            rs.getObject("review_user_id") != null ? rs.getInt("review_user_id") : null,
                            rs.getString("review_text"),
                            rs.getInt("rating"),
                            rs.getInt("useful_votes"),
                            rs.getInt("not_useful_votes"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getString("review_username"),
                            rs.getString("avatar_url")
                    ));
                }
            }
        }
        return reviews;
    }

    public static List<Review> getUserReviews(int userId) throws SQLException {
        List<Review> reviews = new ArrayList<>();
        String sql = """
            SELECT r.id, r.spotify_track_id, r.track_name, r.user_id, 
                   COALESCE(r.review_user_id, NULL) AS review_user_id, 
                   r.review_text, r.rating, r.useful_votes, r.not_useful_votes, 
                   r.created_at, r.review_username, u.avatar_url
            FROM song_reviews r
            LEFT JOIN app_users u ON r.user_id = u.id
            WHERE r.user_id = ?
            ORDER BY r.created_at DESC""";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reviews.add(new Review(
                            rs.getInt("id"),
                            rs.getString("spotify_track_id"),
                            rs.getString("track_name"),
                            rs.getObject("user_id") != null ? rs.getInt("user_id") : null,
                            rs.getObject("review_user_id") != null ? rs.getInt("review_user_id") : null,
                            rs.getString("review_text"),
                            rs.getInt("rating"),
                            rs.getInt("useful_votes"),
                            rs.getInt("not_useful_votes"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getString("review_username"),
                            rs.getString("avatar_url")
                    ));
                }
            }
        }
        return reviews;
    }
}