//import java.sql.*;
//import java.util.ArrayList;
//import java.util.List;
//
//public class MusicPlayer {
//    private static final String DB_URL = "jdbc:postgresql://localhost:5432/spotify_auth";
//    private static final String DB_USER = "postgres";
//    private static final String DB_PASSWORD = "";
//
//    public static class PlayHistory {
//        public final String trackId;
//        public final String trackName;
//        public final Timestamp playedAt;
//
//        public PlayHistory(String trackId, String trackName, Timestamp playedAt) {
//            this.trackId = trackId;
//            this.trackName = trackName;
//            this.playedAt = playedAt;
//        }
//    }
//
//    public static void recordPlayback(int userId, String trackId, String trackName) throws SQLException {
//        String sql = "INSERT INTO listening_history (user_id, track_id, track_name) VALUES (?, ?, ?)";
//
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//             PreparedStatement stmt = conn.prepareStatement(sql)) {
//            stmt.setInt(1, userId);
//            stmt.setString(2, trackId);
//            stmt.setString(3, trackName);
//            stmt.executeUpdate();
//        }
//    }
//
//    public static List<PlayHistory> getRecentTracks(int userId, int limit) throws SQLException {
//        List<PlayHistory> tracks = new ArrayList<>();
//        String sql = """
//            SELECT track_id, track_name, played_at
//            FROM listening_history
//            WHERE user_id = ?
//            ORDER BY played_at DESC
//            LIMIT ?""";
//
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//             PreparedStatement stmt = conn.prepareStatement(sql)) {
//            stmt.setInt(1, userId);
//            stmt.setInt(2, limit);
//            ResultSet rs = stmt.executeQuery();
//
//            while (rs.next()) {
//                tracks.add(new PlayHistory(
//                        rs.getString("track_id"),
//                        rs.getString("track_name"),
//                        rs.getTimestamp("played_at")
//                ));
//            }
//        }
//        return tracks;
//    }
//
//    public static List<PlayHistory> getFriendActivity(int userId) throws SQLException {
//        List<PlayHistory> activity = new ArrayList<>();
//        String sql = """
//            SELECT lh.track_id, lh.track_name, lh.played_at, u.username
//            FROM listening_history lh
//            JOIN user_follows uf ON lh.user_id = uf.followed_id
//            JOIN app_users u ON lh.user_id = u.id
//            WHERE uf.follower_id = ?
//            ORDER BY lh.played_at DESC
//            LIMIT 50""";
//
//        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
//             PreparedStatement stmt = conn.prepareStatement(sql)) {
//            stmt.setInt(1, userId);
//            ResultSet rs = stmt.executeQuery();
//
//            while (rs.next()) {
//                activity.add(new PlayHistory(
//                        rs.getString("track_id"),
//                        rs.getString("track_name") + " (Escuchado por " + rs.getString("username") + ")",
//                        rs.getTimestamp("played_at")
//                ));
//            }
//        }
//        return activity;
//    }
//}